;; SPDX-License-Identifier: GPL-3.0-or-later
;; Read-only CLI: INPUT is a captured {review,native_review} JSON fixture.
;; nbb -cp scripts scripts/classify_review.cljs INPUT REPO PR CURRENT_HEAD ENDPOINT MODEL [chat|systemone]
(ns classify-review
  (:require ["node:fs" :as fs] ["node:path" :as path] ["node:crypto" :as crypto]
            ["node:child_process" :as cp] [nbb.core :refer [*file*]]
            [pr-flow.raw-review :as classifier] [pr-flow.law :as law]))

(defn sha256 [s] (.digest (.update (crypto/createHash "sha256") s "utf8") "hex"))

(defn capture-input [capture repo number head]
  (let [review (:review capture) native (:native_review capture)
        actor (:user review)]
    (when-not (and (law/review-update-matches? review native (:type actor))
                   (= (:login actor) (law/graphql-rest-login (:author native)))
                   (= (:id actor) (get-in native [:author :databaseId]))
                   (= (:node_id actor) (get-in native [:author :id]))
                   (= (:type actor) (get-in native [:author :__typename]))
                   (= (:body_sha256 capture) (sha256 (:body review))))
      (throw (ex-info "Mismatched captured native review" {})))
    {:repository repo :pr-number number :head head
     :source {:id (:id review) :node-id (:node_id review) :body (:body review)
              :updated-at (:updatedAt native) :commit (:commit_id review)
              :actor {:id (:id actor) :node-id (:node_id actor) :login (:login actor) :type (:type actor)}}}))

(let [[file repo number head endpoint model protocol :as args] *command-line-args*]
  (try
    (when-not (and (<= 6 (count args) 7) (contains? #{nil "chat" "systemone"} protocol))
      (throw (ex-info "Expected INPUT REPO PR HEAD ENDPOINT MODEL [chat|systemone]" {})))
    (when (> (.-size (fs/statSync file)) 262144) (throw (ex-info "Capture too large" {})))
    (let [capture (js->clj (js/JSON.parse (fs/readFileSync file "utf8")) :keywordize-keys true)
          input (capture-input capture repo (js/Number number) head)
          request (classifier/prepare input sha256)
          _ (when (:refusal request) (throw (ex-info "Invalid classifier input" {})))
          payload (if (= protocol "systemone")
                    (classifier/systemone-payload request model)
                    {:model model :reasoning_effort "low" :max_completion_tokens 6000
                     :messages [{:role "system" :content classifier/prompt}
                                {:role "user" :content (js/JSON.stringify (clj->js request))}]
                     :response_format {:type "json_schema" :json_schema {:name "raw_review" :strict true
                                                                         :schema classifier/response-schema}}})
          result (cp/spawnSync js/process.execPath
                               #js [(path/join (path/dirname *file*) "pr_flow/classifier_transport.mjs")]
                               #js {:input (js/JSON.stringify (clj->js {:endpoint endpoint :payload payload :protocol (or protocol "chat")}))
                                    :encoding "utf8" :timeout 65000 :maxBuffer 2097152})
          response (when (seq (.-stdout result))
                     (js->clj (js/JSON.parse (.-stdout result)) :keywordize-keys true))
          validated (if (and (= 0 (.-status result)) (:output response))
                      (if (= protocol "systemone")
                        (classifier/validate-systemone input payload (:output response) sha256)
                        (classifier/validate-result input (:output response) sha256))
                      {:status :uncertain :reason :inference-unavailable :authority :triage-only})]
      (println (js/JSON.stringify (clj->js (assoc validated :inference (:inference response)
                                               :provider-status (:status response)
                                               :transport-error (:error response)
                                               :duration-ms (:duration_ms response))) nil 2))
      (when (not= :classified (:status validated)) (aset js/process "exitCode" 2)))
    (catch :default _
      (println "{\"status\":\"uncertain\",\"reason\":\"invalid-input-or-transport\",\"authority\":\"triage-only\"}")
      (aset js/process "exitCode" 2))))
