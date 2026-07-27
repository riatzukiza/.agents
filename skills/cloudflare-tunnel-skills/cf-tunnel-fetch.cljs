#!/usr/bin/env nbb

(ns cf-tunnel-fetch
  (:require [clojure.string :as str]
            ["crypto" :as crypto]
            [promesa.core :as p]))

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

(defn fetch-json [method path & [payload]]
  (p/let [resp (js/fetch
                 (str cf-api-base path)
                 (clj->js
                  (cond-> {:method method
                           :headers (auth-headers)}
                    payload (assoc :body (->body payload)))))
          text (.text resp)]
    (ensure-ok! resp text)
    (parse-json text)))

(defn verify-token []
  (p/let [result (fetch-json "GET" "/user/tokens/verify")]
    (print-json! result)))

(defn list-tunnels []
  (p/let [result (fetch-json "GET" (str "/accounts/" (account-id) "/cfd_tunnel?is_deleted=false"))]
    (print-json! result)))

(defn get-tunnel [tunnel-id]
  (p/let [result (fetch-json "GET" (str "/accounts/" (account-id) "/cfd_tunnel/" tunnel-id))]
    (print-json! result)))

(defn create-tunnel [name secret]
  (p/let [result (fetch-json
                  "POST"
                  (str "/accounts/" (account-id) "/cfd_tunnel")
                  {:name name
                   :secret (or secret (random-secret-b64))})]
    (print-json! result)))

(defn delete-tunnel [tunnel-id]
  (p/let [result (fetch-json "DELETE" (str "/accounts/" (account-id) "/cfd_tunnel/" tunnel-id))]
    (print-json! result)))

(defn get-tunnel-token [tunnel-id]
  (p/let [result (fetch-json "GET" (str "/accounts/" (account-id) "/cfd_tunnel/" tunnel-id "/token"))]
    (print-json! result)))

(defn list-dns-records []
  (p/let [result (fetch-json "GET" (str "/zones/" (zone-id!) "/dns_records"))]
    (print-json! result)))

(defn create-dns-route [zone-name hostname target proxied]
  (p/let [result (fetch-json
                  "POST"
                  (str "/zones/" (zone-id!) "/dns_records")
                  {:type "CNAME"
                   :name (join-hostname zone-name hostname)
                   :content target
                   :proxied (if (false? proxied) false true)})]
    (print-json! result)))

(defn create-route-for-tunnel [zone-name hostname tunnel-id proxied]
  (create-dns-route zone-name hostname (tunnel-cname-target tunnel-id) proxied))

(defn delete-dns-route [record-id]
  (p/let [result (fetch-json "DELETE" (str "/zones/" (zone-id!) "/dns_records/" record-id))]
    (print-json! result)))

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
    "  nbb cf-tunnel-fetch.cljs create-dns-route <zone-name> <hostname|@> <target> [proxied=true]"
    "  nbb cf-tunnel-fetch.cljs create-route-for-tunnel <zone-name> <hostname|@> <tunnel-id> [proxied=true]"
    "  nbb cf-tunnel-fetch.cljs delete-dns-route <record-id>"]))

(defn fail! [msg]
  (binding [*out* *err*]
    (println msg))
  (.exit js/process 1))

(defn parse-proxied [raw]
  (cond
    (or (nil? raw) (= raw "true") (= raw "1")) true
    (or (= raw "false") (= raw "0")) false
    :else (throw (js/Error. (str "proxied must be true or false, got: " raw)))))

(defn dispatch [args]
  (let [[cmd & more] args]
    (case cmd
      "verify-token" (verify-token)
      "list-tunnels" (list-tunnels)
      "get-tunnel" (if-let [tunnel-id (first more)] (get-tunnel tunnel-id) (fail! "tunnel id required"))
      "create-tunnel" (if-let [name (first more)] (create-tunnel name (second more)) (fail! "name required"))
      "delete-tunnel" (if-let [tunnel-id (first more)] (delete-tunnel tunnel-id) (fail! "tunnel id required"))
      "get-tunnel-token" (if-let [tunnel-id (first more)] (get-tunnel-token tunnel-id) (fail! "tunnel id required"))
      "list-dns-records" (list-dns-records)
      "create-dns-route" (let [[zone-name hostname target proxied] more]
                           (if (and zone-name hostname target)
                             (create-dns-route zone-name hostname target (parse-proxied proxied))
                             (fail! "zone-name hostname and target required")))
      "create-route-for-tunnel" (let [[zone-name hostname tunnel-id proxied] more]
                                  (if (and zone-name hostname tunnel-id)
                                    (create-route-for-tunnel zone-name hostname tunnel-id (parse-proxied proxied))
                                    (fail! "zone-name hostname and tunnel-id required")))
      "delete-dns-route" (if-let [record-id (first more)] (delete-dns-route record-id) (fail! "record id required"))
      nil (println (usage))
      "help" (println (usage))
      "-h" (println (usage))
      "--help" (println (usage))
      (fail! (str "unknown command: " cmd "\n\n" (usage))))))

(defn main []
  (let [argv (js->clj (.-argv js/process))
        args (vec (drop 3 argv))
        cmd (first args)]
    (if (contains? #{"help" "-h" "--help" nil} cmd)
      (println (usage))
      (do
        (require-env! "CF_API_TOKEN")
        (require-env! "CF_ACCOUNT_ID")
        (-> (dispatch args)
            (p/catch (fn [e]
                       (fail! (or (.-stack e) (.-message e) (str e))))))))))

(main)
