(ns test-actionability-history
  (:require [cljs.test :refer [deftest is]]
            [pr-flow.actionability-history :as history]))

(def repo-id "R_synthetic_history")
(def admitted {:purpose :thread-actionability :repo-id repo-id
               :status :qualified :assessment-id 71})
(def revoked (assoc admitted :status :revoked))
(defn receipt [decisions]
  {:origin "pr-flow-actionability-observation" :repo "owner/repository"
   :decisions decisions})

(deftest complete-history-retains-order-and-repository-selection
  (is (= [] (history/observations "" repo-id)))
  (is (= [] (history/observations " \n\r\n\t" repo-id)))
  (is (= [] (history/observations "{:kind :unrelated}\n" repo-id)))
  (is (= [admitted revoked]
         (history/observations (str (pr-str (receipt [admitted])) "\r\n"
                                    (pr-str (receipt [revoked])) "\n") repo-id)))
  (is (= [] (history/observations (pr-str (receipt [(assoc admitted :repo-id "R_other")])) repo-id)))
  (is (= [admitted] (history/observations (pr-str (dissoc (receipt [admitted]) :repo)) repo-id)))
  (is (= [] (history/observations (pr-str (receipt [])) repo-id)))
  (is (= [] (history/observations "#:event{:id \"synthetic\" :payload {:kind :unrelated}}" repo-id))))

(deftest unavailable-or-invalid-history-cannot-be-empty-success
  (is (nil? (history/observations nil repo-id)))
  (doseq [bad [42 {} [] false]] (is (nil? (history/observations bad repo-id))))
  (doseq [bad [nil "" " " 42]] (is (nil? (history/observations "" bad))))
  (doseq [bad ["{" "{} {}" "[]" "nil" "; only a comment"
               "{:kind :ok}\n{"
               (pr-str (receipt nil))
               (pr-str (receipt (list admitted)))
               (pr-str (receipt [nil]))
               (pr-str (receipt [(dissoc admitted :purpose)]))
               (pr-str (receipt [(assoc admitted :purpose :different)]))
               (pr-str (receipt [(assoc admitted :repo-id " ")]))
               (pr-str (assoc (receipt [admitted]) :repo 42))
               (pr-str (receipt [admitted (assoc revoked :repo-id "R_other")]))]]
    (is (nil? (history/observations bad repo-id))))
  ;; An invalid unrelated-repository observation still invalidates the whole read.
  (is (nil? (history/observations
              (str (pr-str (receipt [admitted])) "\n"
                   (pr-str (receipt [(assoc admitted :repo-id "R_other" :purpose :wrong)]))) repo-id))))

(deftest distinct-preservation-purpose-shares-complete-history-without-relabeling
  (let [positive (assoc admitted :purpose :positive-bot-thread-preservation)
        positive-revoked (assoc positive :status :revoked)
        source (str (pr-str (receipt [admitted positive])) "\n"
                    (pr-str (receipt [positive-revoked revoked])) "\n")]
    (is (= [admitted positive positive-revoked revoked] (history/observations source repo-id)))
    (is (nil? (history/observations (str source (pr-str (receipt [(assoc positive :purpose :different)]))) repo-id)))))
