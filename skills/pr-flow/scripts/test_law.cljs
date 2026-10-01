#!/usr/bin/env nbb
;; nbb -cp <skill>/scripts <skill>/scripts/test_law.cljs
(ns test-law
  (:require ["fs" :as fs]
            ["path" :as path]
            [cljs.test :refer [deftest is testing run-tests]]
            [clojure.edn :as edn]
            [nbb.core :refer [*file*]]
            [pr-flow.flow :as flow]
            [pr-flow.law :as law]))

(def here
  "This script's directory, captured while nbb loads the file."
  (path/dirname *file*))

(deftest severity-from-coderabbit-banners
  (is (= :p0 (law/severity "_⚠️ Potential issue_ | _🔴 Critical_\n\nbody")))
  (is (= :p1 (law/severity "_⚠️ Potential issue_ | _🟠 Major_")))
  (is (= :p2 (law/severity "_🛠️ Refactor suggestion_ | _🟡 Minor_")))
  (is (= :p3 (law/severity "_🧹 Nitpick_ | _🔵 Trivial_")))
  (is (= :p1 (law/severity "_⚠️ Potential issue_\n\nplain")))
  (testing "explicit P-labels win over banners"
    (is (= :p3 (law/severity "P3: _⚠️ Potential issue_"))))
  (testing "severity words deep in prose do not count"
    (is (= :unknown (law/severity "Consider this.\nMore.\nStill more.\nThis is critical."))))
  (is (= :unknown (law/severity nil))))

(deftest titles
  (is (= "Define ownership for apply."
         (law/title-of "_🩺 Stability_ | _🟠 Major_\n<details>\n<summary>x</summary>\n**not this**\n</details>\n\n**Define ownership for apply.**\nMore.")))
  (is (= "plain first line" (law/title-of "<!-- c -->\n_🟡 Minor_\nplain first line\nsecond")))
  (is (= 110 (count (law/title-of (apply str (repeat 200 "a"))))))
  (is (= "" (law/title-of nil))))

(deftest blocking-is-p0-and-p1
  (is (law/blocking? :p0))
  (is (law/blocking? :p1))
  (is (not (law/blocking? :p2)))
  (is (not (law/blocking? :p3)))
  (is (not (law/blocking? :unknown))))

(deftest resolution-replies
  (is (= :fixed (law/resolution-of "Fixed in abc123: renamed")))
  (is (= :deferred (law/resolution-of "**Deferred** to card x")))
  (is (= :rejected (law/resolution-of "rejected: out of scope")))
  (is (nil? (law/resolution-of "Thanks, will look")))
  (is (nil? (law/resolution-of nil))))

(defn- thread [sev-body & replies]
  {:id "t" :resolved? false
   :comments (into [{:author "coderabbitai" :body sev-body}]
                   (map (fn [b] {:author "riatzukiza" :body b}) replies))})

(deftest classify-and-blockers
  (let [t (law/classify-thread (thread "_🟠 Major_" "Deferred to card x"))]
    (is (= :p1 (:severity t)))
    (is (= :deferred (:resolution t)))
    (is (:settled? t))
    (testing "deferring a P1 does not clear it"
      (is (= 1 (count (law/unsettled-blockers [t]))))))
  (let [t (law/classify-thread (thread "_🟠 Major_" "Fixed in abc"))]
    (is (empty? (law/unsettled-blockers [t]))))
  (testing "bot replies are not settlements"
    (let [t (law/classify-thread (update (thread "_🟡 Minor_") :comments conj
                                         {:author "coderabbitai" :body "Fixed? great"}))]
      (is (not (:settled? t))))))

(deftest coderabbit-check-states
  (is (= :pending (law/coderabbit-state [{:name "CodeRabbit" :state "PENDING" :description "Review in progress"}])))
  (is (= :rate-limited (law/coderabbit-state [{:name "CodeRabbit" :state "SUCCESS" :description "Review rate limited"}])))
  (is (= :completed (law/coderabbit-state [{:name "CodeRabbit" :state "SUCCESS" :description "Review completed"}])))
  (is (= :skipped (law/coderabbit-state [{:name "CodeRabbit" :state "SUCCESS" :description "Review skipped"}])))
  (is (= :absent (law/coderabbit-state [{:name "ci" :state "SUCCESS"}]))))

(deftest merge-gate
  (let [done [{:name "CodeRabbit" :state "SUCCESS" :description "Review completed"}
              {:name "test" :state "SUCCESS"}]
        settled (assoc (law/classify-thread (thread "_🟡 Minor_" "Rejected: intended")) :resolved? true)]
    (is (:pass? (law/merge-gate {:threads [settled] :checks done})))
    (testing "resolved without a settlement reply blocks"
      (is (not (:pass? (law/merge-gate {:threads [(assoc (law/classify-thread (thread "_🟡 Minor_")) :resolved? true)]
                                        :checks done})))))
    (testing "rate-limited is not a pass"
      (is (not (:pass? (law/merge-gate {:threads [] :checks [{:name "CodeRabbit" :state "SUCCESS" :description "Review rate limited"}]})))))
    (testing "failing check blocks"
      (is (not (:pass? (law/merge-gate {:threads [] :checks (conj done {:name "lint" :state "FAILURE"})})))))
    (testing "skipped review is not a pass"
      (is (not (:pass? (law/merge-gate {:threads [] :checks [{:name "CodeRabbit" :state "SUCCESS" :description "Review skipped"}]})))))
    (testing "a review of an older head is not a review of this head"
      (is (not (:pass? (law/merge-gate {:threads [] :checks done :head "b" :reviewed-heads #{"a"}}))))
      (is (:pass? (law/merge-gate {:threads [] :checks done :head "b" :reviewed-heads #{"a" "b"}}))))
    (testing "unanswered review-body nitpicks block"
      (is (not (:pass? (law/merge-gate {:threads [] :checks done :review-bodies-unanswered 1})))))))

(deftest loop-budget
  (is (= :converged (law/loop-verdict {:rounds 9 :open-blockers 0})))
  (is (= :iterate (law/loop-verdict {:rounds 2 :open-blockers 1})))
  (is (= :escalate (law/loop-verdict {:rounds 5 :open-blockers 1}))))

(def skill-root (path/join here ".."))
(def skills-dir (path/join skill-root ".."))
(def the-flow (edn/read-string (str (fs/readFileSync (path/join skill-root "flow.edn") "utf8"))))

(deftest flow-is-lawful
  (is (= [] (flow/problems the-flow)))
  (is (= [:plan] (flow/next-states the-flow :muse)))
  (testing "a broken flow is caught"
    (is (seq (flow/problems (update the-flow :flow/transitions conj [:muse :nowhere]))))
    (is (seq (flow/problems (assoc-in the-flow [:flow/states :orphan] {:skill "x"}))))))

(deftest every-named-skill-exists
  (doseq [s (flow/skills the-flow)]
    (is (fs/existsSync (path/join skills-dir s "SKILL.md")) s)))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))

(run-tests)
