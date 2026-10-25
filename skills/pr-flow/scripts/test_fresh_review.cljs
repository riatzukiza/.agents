;; SPDX-License-Identifier: GPL-3.0-or-later
(ns test-fresh-review
  (:require [cljs.test :refer [deftest is run-tests]]
            ["node:crypto" :as crypto]
            [pr-flow.raw-review :as raw]))

(defn sha [s] (.digest (.update (crypto/createHash "sha256") s "utf8") "hex"))
(def input {:repository "fixture/repository" :pr-number 9 :head (apply str (repeat 40 "a"))
            :source {:id 11 :node-id "PRR_fixture" :body "<v91>LGTM. Please retain every corrective request.</v91>"
                     :updated-at "2026-10-25T00:00:00Z" :commit (apply str (repeat 40 "b"))
                     :actor {:id 12 :node-id "BOT_fixture" :login "fixture[bot]" :type "Bot"}}})
(def custody {:nonce (apply str (repeat 64 "1")) :corpus-sha256 (sha "corpus")
              :history-sha256 (sha "history") :permissions-sha256 (sha "permissions")
              :policy-sha256 (sha "policy")})
(defn result-for [request]
  {:request_id (:request-id request)
   :units (mapv (fn [{:keys [id text]}]
                  {:id id :items [{:classification "actionable" :reason "A live request." :quote text}]}) (:units request))})

(deftest versioned-native-custody-is-distinct-from-repeatable-capture
  (let [legacy (raw/prepare input sha) request (raw/prepare-native input custody sha)
        other (raw/prepare-native input (assoc custody :nonce (apply str (repeat 64 "2"))) sha)]
    (is (= "raw-review/v1" (:version legacy)))
    (is (= "raw-review/v2" (:version request)))
    (is (= (:request-id legacy) (:source-request-id request)))
    (is (not= (:request-id request) (:request-id legacy) (:request-id other)))
    (is (= (:body-sha256 legacy) (:body-sha256 request)))
    (is (= (:units legacy) (:units request)))
    (is (= :classified (:status (raw/validate-native-result input custody (result-for request) sha))))
    (is (= :triage-only (:authority (raw/validate-native-result input custody (result-for request) sha))))
    (is (= :uncertain (:status (raw/validate-result input (result-for request) sha))))
    (is (= :uncertain (:status (raw/validate-native-result input custody (result-for other) sha))))))

(deftest every-custody-coordinate-invalidates-an-old-output
  (let [request (raw/prepare-native input custody sha)]
    (doseq [key (keys custody)]
      (is (= :uncertain (:status (raw/validate-native-result input (assoc custody key (sha (str key)))
                                                           (result-for request) sha))) (str key)))
    (doseq [invalid [nil {} (dissoc custody :nonce) (assoc custody :nonce "caller")
                     (assoc custody :nonce (apply str (repeat 63 "1")))
                     (assoc custody :caller-fresh? true) (assoc custody :history-sha256 nil)]]
      (is (:refusal (raw/prepare-native input invalid sha))))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests)
