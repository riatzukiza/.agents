;; SPDX-License-Identifier: GPL-3.0-or-later
(ns test-fresh-review
  (:require [cljs.test :refer [deftest is run-tests]]
            ["node:crypto" :as crypto]
            [pr-flow.raw-review :as raw]
            [pr-flow.fresh-review :as fresh]
            [test-native-context :as fixture]))

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
    (is (= (:units legacy) (mapv #(select-keys % [:id :text]) (:units request))))
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

(defn host []
  (let [review (atom (assoc fixture/native-review :body "<v93>LGTM! Please keep the real request.</v93>"))
        history (atom []) policy (atom "fixture policy v1") permission (atom true)
        mode (atom nil) records (atom []) receipt-failure (atom nil) reads (atom 0) current (atom nil)
        deps {:read-json
              (fn [& args]
                (swap! reads inc)
                (when (= ["api" (str "repos/" fixture/repo "/issues/" fixture/number "/comments")
                          "--paginate" "--slurp"] (vec args))
                  (let [node (fixture/native-review-node @review)
                        node (if (= :mismatch @mode) (update node :body str " mismatch") node)]
                    (reset! current (:deps (fixture/body-readers [[@review]] [node])))))
                (if (and (= :partial-issues @mode) (not= "graphql" (second args))
                         (.includes (str (second args)) "/issues/"))
                  [nil]
                  (let [response (apply (:read-json @current) args)]
                    (if (and (= :changed-head @mode) (get-in response [:data :repository :pullRequest]))
                      (assoc-in response [:data :repository :pullRequest :headRefOid] (apply str (repeat 40 "f")))
                      response))))
              :authorized-author? (fn [_ _] @permission)
              :read-history (fn [_] @history)
              :policy-source (fn [] @policy)
              :record! (fn [record]
                         (swap! records conj record)
                         (when (= (:phase record) @receipt-failure)
                           (throw (ex-info "Synthetic uncertain append" {}))))}]
    {:deps deps :parent (fresh/parent deps) :review review :history history :policy policy
     :permission permission :mode mode :records records :reads reads :receipt-failure receipt-failure}))
(def selection {:repository fixture/repo :pr-number (js/Number fixture/number) :review-id 101})
(defn begin [h] ((get-in h [:parent :begin!]) selection "synthetic-test-model" :chat))
(defn response-for [attempt]
  {:output (result-for (:request attempt))
   :inference {:request_sha256 (sha (js/JSON.stringify (clj->js (:payload attempt))))}})

(deftest actual-collector-one-use-parent-and-restart-controls
  (let [h (host) first-attempt (begin h)]
    (is (= :pending (:status first-attempt)))
    (is (re-matches #"[0-9a-f]{64}" (get-in first-attempt [:request :custody :nonce])))
    (is (= :challenge-already-active (:reason (begin h))))
    (is (= :classified (:status ((:consume! first-attempt) (response-for first-attempt)))))
    (is (= :challenge-consumed-or-foreign (:reason ((:consume! first-attempt) (response-for first-attempt)))))
    (let [second-attempt (begin h)]
      (is (not= (get-in first-attempt [:request :request-id]) (get-in second-attempt [:request :request-id])))
      (is (= :uncertain (:status ((:consume! second-attempt) (response-for first-attempt)))))
      (is (= :challenge-consumed-or-foreign (:reason ((:consume! second-attempt) (response-for second-attempt))))))
    (let [restarted (assoc h :parent (fresh/parent (:deps h))) attempt (begin restarted)]
      (is (= :uncertain (:status ((:consume! attempt) (response-for first-attempt))))))
    (is (= [:attempt :consumed :attempt :consumed :attempt :consumed] (mapv :phase @(:records h))))
    (is (> @(:reads h) 10))))

(deftest post-inference-custody-changes-and-losses-refuse
  (doseq [change [(fn [h] (swap! (:review h) update :body str " edited"))
                  (fn [h] (reset! (:mode h) :changed-head))
                  (fn [h] (swap! (:review h) assoc-in [:user :id] 10676925))
                  (fn [h] (reset! (:history h) [{:new-observation true}]))
                  (fn [h] (reset! (:history h) nil))
                  (fn [h] (reset! (:permission h) false))
                  (fn [h] (reset! (:permission h) nil))
                  (fn [h] (reset! (:policy h) "fixture policy v2"))
                  (fn [h] (reset! (:mode h) :mismatch))
                  (fn [h] (reset! (:mode h) :partial-issues))]]
    (let [h (host) attempt (begin h)]
      (change h)
      (is (= :uncertain (:status ((:consume! attempt) (response-for attempt))))))))

(deftest initial-incomplete-input-and-caller-challenges-never-start-inference
  (doseq [change [(fn [h] (reset! (:history h) nil))
                  (fn [h] (reset! (:permission h) nil))
                  (fn [h] (reset! (:mode h) :mismatch))
                  (fn [h] (reset! (:mode h) :partial-issues))]]
    (let [h (host)] (change h) (is (= :uncertain (:status (begin h)))) (is (empty? @(:records h)))))
  (let [h (host)]
    (is (= :uncertain (:status ((get-in h [:parent :begin!]) (assoc selection :nonce (sha "caller")) "synthetic" :chat))))
    (is (zero? @(:reads h))))
  (is (thrown? js/Error (fresh/parent (assoc (:deps (host)) :restore-challenge {})))))

(deftest failures-consume-before-recollection-or-receipt-errors
  (doseq [response [nil {} {:output {}}]]
    (let [h (host) attempt (begin h)]
      (is (= :uncertain (:status ((:consume! attempt) response))))
      (is (= :challenge-consumed-or-foreign (:reason ((:consume! attempt) (response-for attempt)))))))
  (let [h (host) attempt (begin h)]
    (reset! (:receipt-failure h) :consumed)
    (is (= :receipt-unavailable (:reason ((:consume! attempt) (response-for attempt)))))
    (is (= :challenge-consumed-or-foreign (:reason ((:consume! attempt) (response-for attempt))))))
  (let [h (host)]
    (reset! (:receipt-failure h) :attempt)
    (is (= :receipt-unavailable (:reason (begin h))))
    (reset! (:receipt-failure h) nil)
    (is (= :pending (:status (begin h))))))

(deftest throwing-transport-is-consumed-without-retry
  (let [h (host) calls (atom 0)
        result (fresh/classify! (:parent h) selection "synthetic" :chat
                               (fn [_] (swap! calls inc) (throw (ex-info "Synthetic transport failure" {}))))]
    (is (= :uncertain (:status result)))
    (is (= 1 @calls))
    (is (= [:attempt :consumed] (mapv :phase @(:records h))))
    (is (= :pending (:status (begin h))))))

(deftest absolute-source-offsets-preserve-unicode-and-full-unit-coverage
  (let [source (assoc-in input [:source :body] (str (apply str (repeat 350 "😀 \n")) "Keep every request."))
        request (raw/prepare-native source custody sha)
        result (raw/validate-native-result source custody (result-for request) sha)]
    (is (= :classified (:status result)))
    (is (> (count (:items result)) 1))
    (doseq [item (:items result) :let [address (:source-address item)]]
      (is (= :utf16-code-unit (:offset-unit address)))
      (is (= (sha (get-in source [:source :body])) (:body-sha256 address)))
      (is (= (:quote item) (subs (get-in source [:source :body]) (:start address) (:end address)))))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests)
