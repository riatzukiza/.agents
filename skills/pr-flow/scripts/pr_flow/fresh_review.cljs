;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.fresh-review
  "Owned one-use advisory parent. Importing performs no reads, inference or writes."
  (:require ["node:crypto" :as crypto]
            ["./await_transport.mjs" :as transport]
            [clojure.string :as str]
            [pr-flow.native-context :as native]
            [pr-flow.native-review :as source]
            [pr-flow.raw-review :as raw]))

(defn- sha256 [s] (.digest (.update (crypto/createHash "sha256") s "utf8") "hex"))
(defn- uncertain [reason] {:status :uncertain :reason reason :authority :triage-only})

(defn payload
  "Use the existing bounded JSON chat/native-client protocols. System One v1
   captures remain unchanged; this new boundary does not infer a v2 Jev protocol."
  [request model protocol]
  (case protocol
    :opencode {:model model :request request :instruction raw/prompt :response_schema raw/response-schema}
    :chat {:model model :reasoning_effort "low" :max_completion_tokens 6000
           :messages [{:role "system" :content raw/prompt}
                      {:role "user" :content (js/JSON.stringify (clj->js request))}]
           :response_format {:type "json_schema" :json_schema {:name "raw_review" :strict true
                                                               :schema raw/response-schema}}}
    nil))

(defn parent
  "Create a single-active-challenge parent around the ACTUAL canonical collector.
   read-json/permission/history/policy and record! are trusted host boundaries,
   never capture/model input. record! must use the Receipt River owner and must
   throw on uncertain append; no ledger decoder/validator is implemented here.
   No active challenge is restored from a capture, receipt or restart."
  [{:keys [read-json authorized-author? read-history policy-source record!] :as deps}]
  (when-not (and (= #{:read-json :authorized-author? :read-history :policy-source :record!} (set (keys deps)))
                 (every? fn? [read-json authorized-author? read-history policy-source record!]))
    (throw (ex-info "Fresh parent requires complete trusted host boundaries" {})))
  (let [active (atom nil)
        collect (fn [selection]
                  (let [repo (:repository selection)
                        snapshot (native/collect-context
                                   {:read-json read-json :authorized-author? authorized-author?
                                    :read-history read-history :body-items? true}
                                   repo (:pr-number selection))
                        actors (source/participants snapshot)
                        permissions (into {} (for [a actors :when (= "User" (nth a 3))]
                                               [a (authorized-author? repo (nth a 2))]))
                        selected (source/select-source snapshot selection permissions (policy-source) sha256)]
                    (when (:refusal selected) (throw (ex-info "Incomplete native source" selected)))
                    (assoc selected :snapshot snapshot :permissions permissions)))]
    {:begin!
     (fn [selection model protocol]
       (if (some? @active)
         (uncertain :challenge-already-active)
         (try
           (when-not (and (= #{:repository :pr-number :review-id} (set (keys selection)))
                          (string? (:repository selection))
                          (re-matches #"[^/\s]+/[^/\s]+" (:repository selection))
                          (every? #(and (integer? %) (pos? %)) ((juxt :pr-number :review-id) selection))
                          (string? model) (not (str/blank? model)) (<= (count model) 256)
                          (contains? #{:chat :opencode} protocol))
             (throw (ex-info "Invalid native selection or protocol" {})))
           (let [before (collect selection)
                 custody (assoc (:custody before) :nonce (.toString (crypto/randomBytes 32) "hex"))
                 request (raw/prepare-native (:input before) custody sha256)
                 _ (when (:refusal request) (throw (ex-info "Classifier input refused" request)))
                 sent (payload request model protocol)
                 challenge {:request request :payload-sha256 (sha256 (js/JSON.stringify (clj->js sent)))}
                 acquired? (compare-and-set! active nil challenge)]
             (when-not acquired? (throw (ex-info "Challenge already active" {})))
             (try
               (record! {:phase :attempt :request request :source (:snapshot before)
                         :canonical-obligations (:canonical-obligations before)
                         :model model :protocol protocol :payload sent
                         :payload-sha256 (:payload-sha256 challenge)})
               {:status :pending :authority :triage-only :request request :payload sent
                :consume!
                (fn [response]
                  (if-not (compare-and-set! active challenge nil)
                    (uncertain :challenge-consumed-or-foreign)
                    (let [result
                          (try
                            (cond
                              (not (:output response)) (uncertain :inference-unavailable)
                              (not= (:payload-sha256 challenge) (get-in response [:inference :request_sha256]))
                              (uncertain :inference-request-mismatch)
                              :else
                              (let [after (collect selection)]
                                (if (not= before after)
                                  (uncertain :native-custody-changed)
                                  (raw/validate-native-result (:input after) custody (:output response) sha256))))
                            (catch :default _ (uncertain :native-recollection-unavailable)))]
                      (try
                        (record! {:phase :consumed :request-id (:request-id request)
                                  :payload-sha256 (:payload-sha256 challenge)
                                  :status (:status result) :reason (:reason result) :response response})
                        (assoc result :inference (:inference response)
                                      :provider-status (:status response) :transport-error (:error response))
                        (catch :default _ (uncertain :receipt-unavailable))))))}
               (catch :default _
                 (compare-and-set! active challenge nil)
                 (uncertain :receipt-unavailable))))
           (catch :default _ (uncertain :native-collection-unavailable)))))}))

(defn ^:async classify!
  "Own one bounded invocation and consume its challenge even when transport
   throws/rejects. Returns a Promise; infer! may return a response or a Promise.
   Native async/await interop keeps this source usable in NBB, which does not
   implement the CLJS await form. infer! is trusted bounded transport, not data."
  [controller selection model protocol infer!]
  (let [attempt ((:begin! controller) selection model protocol)]
    (if (not= :pending (:status attempt))
      (js/Promise.resolve attempt)
      (transport/consumeResponse #(infer! (:payload attempt)) (:consume! attempt)
                                 {:error :transport-failed}))))
