#!/usr/bin/env nbb

(ns cf-tunnel-fetch
  (:require [clojure.string :as str]
            ["crypto" :as crypto]))

(defn env [k]
  (aget (.-env js/process) k))

(def cf-api-base
  (or (env "CF_API_BASE")
      "https://api.cloudflare.com/client/v4"))

(defn require-env! [k]
  (if-let [v (env k)]
    v
    (throw (js/Error. (str k " is required")))))

(defn account-id []
  (require-env! "CF_ACCOUNT_ID"))

(defn zone-id! []
  (require-env! "CF_ZONE_ID"))

(defn auth-headers []
  {"Authorization" (str "Bearer " (require-env! "CF_API_TOKEN"))
   "Content-Type" "application/json"})

(defn pretty [x]
  (.stringify js/JSON x nil 2))

(defn print-json! [x]
  (println (pretty x)))

(defn random-secret-b64 []
  (.toString (.randomBytes crypto 32) "base64"))

(defn join-hostname [zone-name host]
  (if (= host "@") zone-name (str host "." zone-name)))

(defn tunnel-cname-target [tunnel-id]
  (str tunnel-id ".cfargotunnel.com"))

(defn ->body [x]
  (when x (.stringify js/JSON (clj->js x))))

(defn ensure-ok! [resp text]
  (when-not (.-ok resp)
    (throw (js/Error. (str "HTTP " (.-status resp) " " (.-statusText resp) "\n" text)))))

(defn parse-json [text]
  (when-not (str/blank? text)
    (js/JSON.parse text)))

(defn ^:async fetch-json [method path & [payload]]
  (let [resp (await
              (js/fetch
               (str cf-api-base path)
               (clj->js
                (cond-> {:method method
                         :headers (auth-headers)}
                  payload (assoc :body (->body payload))))))
        text (await (.text resp))]
    (ensure-ok! resp text)
    (parse-json text)))

(defn ^:async verify-token []
  (print-json! (await (fetch-json "GET" "/user/tokens/verify"))))

(defn ^:async list-tunnels []
  (print-json! (await (fetch-json "GET" (str "/accounts/" (account-id) "/cfd_tunnel?is_deleted=false")))))

(defn ^:async get-tunnel [tunnel-id]
  (print-json! (await (fetch-json "GET" (str "/accounts/" (account-id) "/cfd_tunnel/" tunnel-id)))))

(defn ^:async create-tunnel [name secret]
  (print-json!
   (await
    (fetch-json
     "POST"
     (str "/accounts/" (account-id) "/cfd_tunnel")
     {:name name
      :secret (or secret (random-secret-b64))}))))

(defn ^:async delete-tunnel [tunnel-id]
  (print-json! (await (fetch-json "DELETE" (str "/accounts/" (account-id) "/cfd_tunnel/" tunnel-id)))))

(defn ^:async get-tunnel-token [tunnel-id]
  (print-json! (await (fetch-json "GET" (str "/accounts/" (account-id) "/cfd_tunnel/" tunnel-id "/token")))))

(defn ^:async list-dns-records []
  (print-json! (await (fetch-json "GET" (str "/zones/" (zone-id!) "/dns_records")))))

(defn ^:async create-dns-route [zone-name hostname target]
  (print-json!
   (await
    (fetch-json
     "POST"
     (str "/zones/" (zone-id!) "/dns_records")
     {:type "CNAME"
      :name (join-hostname zone-name hostname)
      :content target
      :proxied true}))))

(defn ^:async create-route-for-tunnel [zone-name hostname tunnel-id]
  (await (create-dns-route zone-name hostname (tunnel-cname-target tunnel-id))))

(defn ^:async delete-dns-route [record-id]
  (print-json! (await (fetch-json "DELETE" (str "/zones/" (zone-id!) "/dns_records/" record-id)))))

(defn usage []
  (str/join
   "\n"
   ["Usage:"
    "  nbb cf-tunnel-fetch.cljs verify-token"
    "  nbb cf-tunnel-fetch.cljs list-tunnels"
    "  nbb cf-tunnel-fetch.cljs get-tunnel <tunnel-id>"
    "  nbb cf-tunnel-fetch.cljs create-tunnel <name> [base64-secret]"
    "  nbb cf-tunnel-fetch.cljs delete-tunnel <tunnel-id>"
    "  nbb cf-tunnel-fetch.cljs get-tunnel-token <tunnel-id>"
    "  nbb cf-tunnel-fetch.cljs list-dns-records"
    "  nbb cf-tunnel-fetch.cljs create-dns-route <zone-name> <hostname|@> <target>"
    "  nbb cf-tunnel-fetch.cljs create-route-for-tunnel <zone-name> <hostname|@> <tunnel-id>"
    "  nbb cf-tunnel-fetch.cljs delete-dns-route <record-id>"]))

(defn fail! [msg]
  (binding [*out* *err*]
    (println msg))
  (.exit js/process 1))

(defn ^:async dispatch [args]
  (let [[cmd & more] args]
    (case cmd
      "verify-token" (await (verify-token))
      "list-tunnels" (await (list-tunnels))
      "get-tunnel" (if-let [tunnel-id (first more)] (await (get-tunnel tunnel-id)) (fail! "tunnel id required"))
      "create-tunnel" (if-let [name (first more)] (await (create-tunnel name (second more))) (fail! "name required"))
      "delete-tunnel" (if-let [tunnel-id (first more)] (await (delete-tunnel tunnel-id)) (fail! "tunnel id required"))
      "get-tunnel-token" (if-let [tunnel-id (first more)] (await (get-tunnel-token tunnel-id)) (fail! "tunnel id required"))
      "list-dns-records" (await (list-dns-records))
      "create-dns-route" (let [[zone-name hostname target] more]
                             (if (and zone-name hostname target)
                               (await (create-dns-route zone-name hostname target))
                               (fail! "zone-name hostname and target required")))
      "create-route-for-tunnel" (let [[zone-name hostname tunnel-id] more]
                                   (if (and zone-name hostname tunnel-id)
                                     (await (create-route-for-tunnel zone-name hostname tunnel-id))
                                     (fail! "zone-name hostname and tunnel-id required")))
      "delete-dns-route" (if-let [record-id (first more)] (await (delete-dns-route record-id)) (fail! "record id required"))
      nil (println (usage))
      "help" (println (usage))
      "-h" (println (usage))
      "--help" (println (usage))
      (fail! (str "unknown command: " cmd "\n\n" (usage))))))

(defn ^:async main []
  (let [argv (js->clj (.-argv js/process))
        args (vec (drop 3 argv))
        cmd (first args)]
    (if (contains? #{"help" "-h" "--help" nil} cmd)
      (println (usage))
      (do
        (require-env! "CF_API_TOKEN")
        (require-env! "CF_ACCOUNT_ID")
        (try
          (await (dispatch args))
          (catch :default e
            (fail! (or (.-stack e) (.-message e) (str e)))))))))

(main)
