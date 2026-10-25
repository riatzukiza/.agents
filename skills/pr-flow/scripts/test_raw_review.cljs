;; SPDX-License-Identifier: GPL-3.0-or-later
(ns test-raw-review
  (:require [cljs.test :refer [deftest is run-tests]]
            ["node:crypto" :as crypto]
            [pr-flow.raw-review :as classifier]))

(defn sha [s] (.digest (.update (crypto/createHash "sha256") s "utf8") "hex"))
(def input
  {:repository "open-hax/receipt-river" :pr-number 4 :head (apply str (repeat 40 "a"))
   :source {:id 12 :node-id "PRR_native" :updated-at "2026-10-25T06:00:00Z"
            :commit (apply str (repeat 40 "b"))
            :actor {:id 13 :node-id "BOT_native" :type "Bot" :login "reviewer[bot]"}
            :body "<new-format>LGTM on preserving old events.\nPlease add provenance to the undated entry.</new-format>\n"}})
(defn output [source]
  (let [request (classifier/prepare source sha)]
    {:request_id (:request-id request)
     :units (mapv (fn [unit]
                    {:id (:id unit) :items [{:classification "actionable" :reason "A live provenance request."
                                            :quote (:text unit)}]}) (:units request))}))

(deftest exact-input-coverage-and-native-binding
  (let [result (classifier/validate-result input (output input) sha)]
    (is (= :classified (:status result)))
    (is (= :triage-only (:authority result)))
    (is (= 0 (:quote-start (first (:items result)))))
    (is (= (get-in input [:source :body]) (apply str (map :text (get-in result [:request :units])))))
    (is (= :uncertain (:status (classifier/validate-result (assoc input :head (apply str (repeat 40 "c"))) (output input) sha))))
    (doseq [changed [(assoc input :repository "other/repo") (update input :pr-number inc)
                     (assoc-in input [:source :id] 14) (assoc-in input [:source :node-id] "other")
                     (assoc-in input [:source :actor :id] 99)
                     (assoc-in input [:source :updated-at] "later")
                     (update-in input [:source :body] str " edited")]]
      (is (= :uncertain (:status (classifier/validate-result changed (output input) sha)))))))

(deftest refuse-invalid-model-output
  (let [valid (output input)]
    (doseq [invalid [nil {} (assoc valid :approved true) (assoc valid :request_id "forged")
                     (assoc valid :units []) (update valid :units conj (first (:units valid)))
                     (assoc-in valid [:units 0 :id] 7)
                     (assoc-in valid [:units 0 :items 0 :classification] "approved")
                     (assoc-in valid [:units 0 :items 0 :reason] " ")
                     (assoc-in valid [:units 0 :items 0 :quote] "normalized or invented")
                     (assoc-in valid [:units 0 :items 0 :severity] "P3")
                     (assoc-in valid [:units 0 :items] [])
                     (update-in valid [:units 0 :items] conj (get-in valid [:units 0 :items 0]))]]
      (is (= :uncertain (:status (classifier/validate-result input invalid sha)))))))

(deftest uncertainty-is-not-permission
  (let [result (classifier/validate-result input (assoc-in (output input) [:units 0 :items 0 :classification] "uncertain") sha)]
    (is (= :uncertain (:status result)))
    (is (= 1 (count (:items result))))
    (is (= :triage-only (:authority result)))))

(deftest arbitrary-format-and-whitespace-are-lossless
  (doseq [body ["<v92>LGTM\n\n<dd>Needs correction</dd>\n" "\n😀 café\n\nありがとう\n"
               (apply str (repeat 500 "unparsed format\n")) (apply str (repeat 3000 "x"))]]
    (let [prepared (classifier/prepare (assoc-in input [:source :body] body) sha)]
      (is (nil? (:refusal prepared)))
      (is (= body (apply str (map :text (:units prepared)))))))
  (doseq [body [nil "" " \n " (apply str (repeat 49153 "x"))]]
    (is (:refusal (classifier/prepare (assoc-in input [:source :body] body) sha)))))

(deftest ambiguous-quotes-are-not-evidence
  (let [source (assoc-in input [:source :body] "LGTM and LGTM, but fix the permission boundary.")
        result (assoc-in (output source) [:units 0 :items 0 :quote] "LGTM")]
    (is (= :uncertain (:status (classifier/validate-result source result sha))))))

(deftest typed-systemone-decisions-remain-bound-and-conservative
  (let [request (classifier/prepare input sha)
        payload (classifier/systemone-payload request "jev-1.13-free")
        response {:model "jev-1.13.0"
                  :answers {:unit_0 {:type "choice" :choice "actionable" :confidence 1
                                     :probabilities {:actionable 1 :informational 0 :uncertain 0}}}}
        evaluate #(classifier/validate-systemone input payload % sha)]
    (is (= :classified (:status (evaluate response))))
    (is (= "actionable" (get-in (evaluate response) [:units 0 :classification])))
    (is (= (get-in input [:source :body]) (get-in (evaluate response) [:units 0 :text])))
    (is (= :triage-only (:authority (evaluate response))))
    (is (= :uncertain (:status (evaluate (assoc-in response [:answers :unit_0 :confidence] 0.8)))))
    (doseq [bad [(assoc response :answers {})
                 (assoc-in response [:answers :unknown] (get-in response [:answers :unit_0]))
                 (assoc-in response [:answers :unit_0 :choice] "approved")
                 (assoc-in response [:answers :unit_0 :choice] {})
                 (assoc-in response [:answers :unit_0 :probabilities :actionable] 0.2)
                 (assoc-in response [:answers :unit_0 :probabilities :informational] 2)
                 (assoc-in response [:answers :unit_0 :confidence] ##NaN)
                 (assoc-in response [:answers :unit_0 :approved] true)]]
      (is (= :uncertain (:status (evaluate bad)))))
    (is (= :uncertain (:status (classifier/validate-systemone (update-in input [:source :body] str " edit") payload response sha))))
    (is (= :uncertain (:status (classifier/validate-systemone (assoc input :head (apply str (repeat 40 "c"))) payload response sha))))
    (is (= :uncertain (:status (classifier/validate-systemone input (assoc-in payload [:state :review-units] []) response sha))))))

(run-tests)
