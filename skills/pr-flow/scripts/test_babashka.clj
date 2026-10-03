(ns test-babashka
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [pr-flow.actionability :as actionability]))

(deftest issue-comments-without-bodies-remain-findings
  ;; NBB tolerated the old split-lines nil call; Babashka throws. Exercise
  ;; disposition itself so both native null and absent body fields stay safe.
  (doseq [comment [{:body nil} {}]]
    (testing (str "issue comment " (pr-str comment))
      (is (= {:kind :finding :status :absent :observations []}
             (select-keys (actionability/disposition {:issue-comments [comment]
                                                     :actionability-observations []})
                          [:kind :status :observations])))
      (is (= {:kind :finding :status :unavailable :observations []}
             (select-keys (actionability/disposition {:issue-comments [comment]})
                          [:kind :status :observations]))))))

(let [{:keys [fail error]} (run-tests 'test-babashka)]
  (when (pos? (+ fail error))
    (System/exit 1)))
