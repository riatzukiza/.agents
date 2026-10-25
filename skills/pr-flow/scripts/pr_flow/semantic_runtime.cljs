;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.semantic-runtime
  "Opt-in fresh semantic assessment inside the canonical PR CLI."
  (:require [clojure.string :as str]
            [pr-flow.semantic-transport :as transport]
            [pr-flow.semantic-repair :as semantic]))
(defonce owned-cache (atom {}))
(defn- stamp [] (subs (.toISOString (js/Date.)) 0 19))
(defn- utc-stamp [] (str (stamp) "Z"))
(defn- paths [t]
  (let [repo (get-in t [:native-context :repository :nameWithOwner])
        prefix (str "https://github.com/" repo "/blob/" (:head t) "/")
        body [(last (get-in t [:native-context :thread :comments :nodes]))]
        linked (mapcat #(map second (re-seq (re-pattern (str (str/replace prefix #"[.\[\]{}()*+?^$|\\]" "\\$0") "([^\\s<>`\"()\\[\\]]+)")) (:body %))) body)]
    (vec (distinct (cons (:path t) linked)))))
(defn assess [read-json trusted-bot? model t]
  (if (or (:settled? t) (not (true? (:resolved? t)))
          (not= (:pr-author t) (get-in t [:comments 0 :author]))) t
    (try
      (let [b (get-in t [:native-context :thread :comments :nodes])
            bot (:author (last b))
            _ (when-not (trusted-bot? bot) (throw (ex-info "Unconfigured native verifier" {})))
            source-paths (paths t)
            _ (when-not (<= 1 (count source-paths) 8) (throw (ex-info "Semantic source set exceeds limit" {})))
            repo (get-in t [:native-context :repository :nameWithOwner])
            sources (mapv #(transport/immutable-source read-json repo (:head t) %) source-paths)
            input {:thread t :sources sources :complete? true
                   :policy {:bot-identities #{(select-keys bot [:id :databaseId :login :__typename])}
                            :models #{model} :transport :opencode-cli}}
            request (semantic/prepare input transport/sha256)]
        (if-not (= :prepared (:status request))
          (assoc t :semantic-diagnostic (dissoc request :input :payload))
          (let [key (:request-id request)
                evidence (or (get @owned-cache key)
                  (let [started (utc-stamp)
                        result (transport/infer model (select-keys request [:purpose :request-id :payload])
                                                semantic/prompt semantic/response-schema)
                        completed (utc-stamp)
                        candidate (if (= :complete (:status result))
                                    (semantic/validate-result request (:output result))
                                    {:status :uncertain :reason (:reason result)})
                        evidence (assoc candidate :execution
                                  {:channel :owned-model-execution :purpose semantic/version
                                   :request-id key :response-digest (transport/sha256 (pr-str (:output result)))
                                   :model model :run-id (get-in result [:inference :session_id])
                                   :complete? (= :complete (:status result)) :tools-used? false
                                   :started-at started :completed-at completed :inference (:inference result)})]
                    (swap! owned-cache assoc key evidence) evidence))
                fresh-sources (mapv #(transport/immutable-source read-json repo (:head t) %) source-paths)]
            (if (= sources fresh-sources)
              (assoc t :semantic-repair-evidence evidence
                       :semantic-diagnostic (select-keys evidence [:status :reason]))
              (assoc t :semantic-diagnostic {:status :uncertain :reason :source-changed})))))
      (catch :default e (assoc t :semantic-diagnostic {:status :uncertain :reason (ex-message e)})))))
