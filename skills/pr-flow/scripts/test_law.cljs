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

(deftest handled-does-not-clear-blockers
  (let [t (law/classify-thread (thread "_🔴 Critical_" "Handled: already fine"))]
    (is (= 1 (count (law/unsettled-blockers [t]))))))

(deftest pushback-after-settlement-blocks
  (let [base (thread "_🟡 Minor_" "Rejected: intended")
        pushed (law/classify-thread (update base :comments conj {:author "coderabbitai[bot]" :body "That reasoning does not hold; the path is still reachable."}))
        confirmed (law/classify-thread (update base :comments conj {:author "coderabbitai[bot]" :body "Verified in abc. ✅ Review thread resolved."}))
        resettled (law/classify-thread (update base :comments into [{:author "coderabbitai[bot]" :body "Still wrong."}
                                                                   {:author "riatzukiza" :body "Fixed in def: covered now"}]))]
    (is (:contested? pushed))
    (is (not (:pass? (law/merge-gate {:threads [(assoc pushed :resolved? true)] :checks [{:name "CodeRabbit" :state "SUCCESS" :description "Review completed"}]}))))
    (is (not (:contested? confirmed)))
    (is (not (:contested? resettled)))))

(deftest ambiguous-confirmation-and-human-pushback
  (let [base (thread "_🟡 Minor_" "Fixed in abc: repaired")
        disputed (fn [author body]
                   (law/classify-thread (update base :comments conj {:author author :body body})))]
    (is (:contested? (disputed "coderabbitai[bot]" "Verified: this still reproduces.")))
    (is (:contested? (disputed "human-reviewer" "I still see the bug.")))
    (is (not (:contested? (disputed "riatzukiza" "Follow-up details from the fixer."))))
    (is (not (:contested? (disputed "coderabbitai[bot]" "✅ Review thread resolved."))))))

(deftest review-body-answer-must-name-review
  (let [body (str "<summary><em>🟠 Major</em> · Must fix · <code>x:1</code></summary>"
                  "details <!-- cr-comment:v1:abc123 -->"
                  "<summary><em>🟡 Minor</em> · May defer · <code>x:2</code></summary>"
                  "details <!-- cr-comment:v1:def456 -->")
        review {:id 101 :submitted_at "2026-10-01T00:00:00Z" :body body}
        answer {:created_at "2026-10-01T00:01:00Z"
                :body "Handled: review-id:101\n- Fixed cr-comment:v1:abc123: corrected\n- Deferred cr-comment:v1:def456: card 2"}]
    (is (= 2 (count (law/review-body-findings body))))
    (is (= 0 (law/unanswered-review-count [review] [answer])))
    (is (= 1 (law/unanswered-review-count [review] [(assoc answer :body "Handled: review-id:101\n- Deferred cr-comment:v1:abc123: card 1\n- Deferred cr-comment:v1:def456: card 2")])))
    (is (= 1 (law/unanswered-review-count [review] [(assoc answer :body "Handled: review-id:101\n- Fixed cr-comment:v1:abc123: corrected")])))
    (is (= 2 (law/unanswered-review-count [review] [(assoc answer :body "Handled: generic\n- Fixed cr-comment:v1:abc123")])))))

(deftest review-body-title-may-contain-html
  (let [body "<summary><em>🟠 Major</em> · Use <code>foo</code> · <code>x:1</code></summary><blockquote>body <!-- cr-comment:v1:abc123 -->"]
    (is (= [{:id "abc123" :severity :p1 :title "Use <code>foo</code>"}]
           (law/review-body-findings body)))))

(deftest nitpick-without-item-banner-fails-closed
  (let [body "<summary>🧹 Nitpick comments (1)</summary>text <!-- cr-comment:v1:xyz789 -->"
        review {:id 202 :submitted_at "2026-10-01T00:00:00Z" :body body}
        deferred {:created_at "2026-10-01T00:01:00Z" :body "Handled: review-id:202\n- Deferred cr-comment:v1:xyz789: later"}
        fixed (assoc deferred :body "Handled: review-id:202\n- Fixed cr-comment:v1:xyz789: corrected")]
    (is (= 1 (law/unanswered-review-count [review] [deferred])))
    (is (= 0 (law/unanswered-review-count [review] [fixed])))))

(deftest review-rounds-are-stage-scoped
  (let [reviews [{:submitted_at "2026-10-01T00:01:00Z"}
                 {:submitted_at "2026-10-01T01:01:00Z"}
                 {:submitted_at "2026-10-01T02:01:00Z"}]
        markers [{:stage "planning" :created_at "2026-10-01T00:00:00Z"}
                 {:stage "code" :created_at "2026-10-01T02:00:00Z"}]]
    (is (= 1 (law/stage-review-rounds reviews markers "code")))
    (is (= 0 (law/stage-review-rounds reviews markers "planning")))
    (is (= 3 (law/stage-review-rounds reviews [] "code")))))

(deftest codex-titles-and-badges
  (let [body "**<sub><sub>![P1 Badge](https://img.shields.io/badge/P1-orange?style=flat)</sub></sub>  Reuse the SHA that actually passed the gate**\n\nIf another commit..."]
    (is (= :p1 (law/severity body)))
    (is (= "Reuse the SHA that actually passed the gate" (law/title-of body)))))

(deftest coderabbit-check-states
  (is (= :pending (law/coderabbit-state [{:name "CodeRabbit" :state "PENDING" :description "Review in progress"}])))
  (is (= :rate-limited (law/coderabbit-state [{:name "CodeRabbit" :state "SUCCESS" :description "Review rate limited"}])))
  (is (= :completed (law/coderabbit-state [{:name "CodeRabbit" :state "SUCCESS" :description "Review completed"}])))
  (is (= :skipped (law/coderabbit-state [{:name "CodeRabbit" :state "SUCCESS" :description "Review skipped"}])))
  (is (= :absent (law/coderabbit-state [{:name "ci" :state "SUCCESS"}]))))

(def gate-head (apply str (repeat 40 "a")))
(defn gated [input]
  (law/merge-gate (merge {:head gate-head :approved-heads {"mimo" #{gate-head}}} input)))

(deftest merge-gate
  (let [done [{:name "CodeRabbit" :state "SUCCESS" :description "Review completed"}
              {:name "test" :state "SUCCESS"}]
        settled (assoc (law/classify-thread (thread "_🟡 Minor_" "Rejected: intended")) :resolved? true)]
    (is (:pass? (gated {:threads [settled] :checks done})))
    (is (not (:pass? (gated {:threads [(assoc (law/classify-thread (thread "_🟡 Minor_")) :resolved? true)] :checks done}))))
    (doseq [check [{:name "CodeRabbit" :state "SUCCESS" :description "Review rate limited"}
                   {:name "CodeRabbit" :state "SUCCESS" :description "Review skipped"}
                   {:name "CodeRabbit" :state "FAILURE" :description "Review failed"}]]
      (is (:pass? (gated {:checks [check]})))
      (is (not (:pass? (gated {:checks [check] :required-reviewers #{"coderabbit"}})))))
    (is (not (:pass? (gated {:checks (conj done {:name "lint" :state "FAILURE"})}))))
    (is (not (:pass? (gated {:checks done :approved-heads {"mimo" #{(apply str (repeat 40 "b"))}}}))))
    (is (not (:pass? (gated {:checks done :required-reviewers #{"coderabbit" "codex"}}))))
    (is (:pass? (gated {:checks done :required-reviewers #{"coderabbit" "codex"}
                        :approved-heads {"coderabbit" #{gate-head} "codex" #{gate-head}}})))
    (is (not (:pass? (gated {:checks done :incomplete? true}))))
    (is (not (:pass? (gated {:checks done :review-bodies-unanswered 1}))))
    (is (not (:pass? (gated {:checks (conj done {:name "required" :state "SKIPPED" :required? true})}))))
    (is (:pass? (gated {:checks (conj done {:name "optional" :state "SKIPPED" :required? false})})))))

(deftest loop-budget
  (is (= :converged (law/loop-verdict {:rounds 9 :open-blockers 0})))
  (is (= :iterate (law/loop-verdict {:rounds 2 :open-blockers 1})))
  (is (= :escalate (law/loop-verdict {:rounds 5 :open-blockers 1}))))

(def skill-root (path/join here ".."))
(def skills-dir (path/join skill-root ".."))
(def the-flow (edn/read-string (str (fs/readFileSync (path/join skill-root "flow.edn") "utf8"))))

(deftest user-quorum-defaults-and-explicit-overrides
  (let [defaults (:flow/defaults the-flow)]
    (is (= #{} (law/required-reviewers-for defaults "open-hax/knoxx" nil)))
    (is (= #{"coderabbit"}
           (law/required-reviewers-for defaults "open-hax/knoxx" #{"coderabbit"})))
    (is (= #{}
           (law/required-reviewers-for defaults "open-hax/foresight" nil)))
    (is (= #{"codex"}
           (law/required-reviewers-for defaults "open-hax/foresight" #{"codex"})))))

(deftest flow-is-lawful
  (is (= [] (flow/problems the-flow)))
  (is (= [:plan] (flow/next-states the-flow :muse)))
  (testing "a broken flow is caught"
    (is (seq (flow/problems (update the-flow :flow/transitions conj [:muse :nowhere]))))
    (is (seq (flow/problems (assoc-in the-flow [:flow/states :orphan] {:skill "x"}))))
    (let [disconnected (-> the-flow
                           (assoc-in [:flow/states :island-a] {:skill "x"})
                           (assoc-in [:flow/states :island-b] {:skill "x"})
                           (update :flow/transitions into [[:island-a :island-b] [:island-b :island-a]]))]
      (is (some #(and (= :unreachable-state (:problem %)) (= :island-a (:state %))) (flow/problems disconnected))))))

(deftest every-named-skill-exists
  (doseq [s (flow/skills the-flow)]
    (is (fs/existsSync (path/join skills-dir s "SKILL.md")) s)))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))

(run-tests)
