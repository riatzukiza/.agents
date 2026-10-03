#!/usr/bin/env nbb
;; nbb -cp <skill>/scripts <skill>/scripts/test_law.cljs
(ns test-law
  (:require ["fs" :as fs]
            ["path" :as path]
            [cljs.test :refer [deftest is testing run-tests]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [nbb.core :refer [*file*]]
            [test-legacy]
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
    (is (not (:contested? (disputed "coderabbitai[bot]" "✅ Review thread resolved."))))
    (is (not (:contested? (disputed "coderabbitai[bot]"
                                   "The condition addresses this finding.\n\n✅ Review thread resolved.\n\n_You are interacting with an AI system._\n\n<!-- auto-generated reply -->"))))))

(deftest reviewer-cannot-settle-their-own-pushback
  (let [t {:resolved? true :comments [{:author "human-reviewer" :body "P1: still wrong"}
                                     {:author "riatzukiza" :body "Fixed in abc: repaired"}
                                     {:author "human-reviewer" :body "Fixed? This still reproduces"}]}
        r (law/classify-thread t)]
    (is (:contested? r))
    (is (not (:settled? r))))
  (doseq [reply ["Review thread resolved incorrectly; this still reproduces"
                 "✅ Review thread resolved? Still broken."]]
    (is (:contested? (law/classify-thread
                     (update (thread "P1: bug" "Fixed in abc: repaired") :comments conj
                             {:author "coderabbitai[bot]" :body reply}))))))

(deftest real-acknowledgement-excludes-generated-share-and-learning-blocks
  (let [body (str "`@riatzukiza` Thanks for the clarification and fix. The quorum can accept formal APPROVED reviews or completed native passing verdicts with trusted exact-head coverage. Imported CLI evidence cannot count as native approval. My suggested restriction to formal GitHub approvals was too narrow.\n\nRuntime activation remains pending.\n\n"
                  "<details>\n<summary>✏️ Learnings added</summary>\n```\nLearning: Imported CLI evidence cannot count as native approval.\n```\n> Note: Learnings are effective only in the context of similar code segments.\n</details>\n"
                  "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\nIf helpful, share on [X](https://twitter.com/intent/tweet?text=not%20resolved).\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->\n\n"
                  "✅ Review thread resolved.\n\n_You are interacting with an AI system._\n<!-- This is an auto-generated reply by CodeRabbit -->")
        replied (fn [reply] (law/classify-thread (update (thread "P1: quorum" "Fixed in b0ea790: clarified policy")
                                                       :comments conj {:author "coderabbitai[bot]" :body reply})))]
    (is (:settled? (replied body)))
    (is (not (:contested? (replied body))))
    (is (:contested? (replied (str body "\nHowever this still reproduces."))))
    (is (:contested? (replied "Quoted old reply:\n```text\n✅ Review thread resolved.\n```\nThis still reproduces.")))
    (is (:contested? (replied "> ✅ Review thread resolved.\n\nThe fix does not address the finding.")))
    (is (:contested? (replied "<blockquote>✅ Review thread resolved.</blockquote>\nThe explanation is insufficient.")))
    (is (:contested? (replied "<details>\n<summary>✏️ Learnings added</summary>\n✅ Review thread resolved.\n</details>\nThis still fails.")))
    (is (:contested? (replied "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\n✅ Review thread resolved.\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->\nThe settlement is rejected.")))))

(deftest generated-only-followup-is-not-reviewer-pushback
  (let [reply "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\nShare this old finding on X.\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->\n---\n_You are interacting with an AI system._\n<!-- auto-generated reply -->"]
    (is (not (:contested? (law/classify-thread
                          (update (thread "P1: bug" "Fixed in abc: regression passes") :comments conj
                                  {:author "coderabbitai[bot]" :body reply})))))))

(deftest body-findings-cannot-be-settled-by-their-own-opener
  (doseq [[state body marker verb]
          [["CHANGES_REQUESTED" "P1: authorization bypass" "review-body:404" "Fixed"]
           ["COMMENTED" "Outside diff range comments (1): legacy finding" "review-body:404" "Fixed"]
           ["COMMENTED" "<summary><em>🟠 Major</em> · Bug · <code>x:1</code></summary><!-- cr-comment:v1:abc123 -->" "cr-comment:v1:abc123" "Fixed"]
           ["COMMENTED" "Nitpick comments (1)<!-- cr-comment:v1:abc123 -->" "cr-comment:v1:abc123" "Fixed"]
           ["COMMENTED" "<summary><em>🟡 Minor</em> · Follow-up · <code>x:1</code></summary><!-- cr-comment:v1:abc123 -->" "cr-comment:v1:abc123" "Deferred"]]]
    (let [review {:id 404 :user {:login "human-reviewer" :type "User"} :state state :body body
                  :submitted_at "2026-10-03T01:00:00Z"}
          answer {:user {:login "different-writer" :type "User"} :created_at "2026-10-03T01:01:00Z"
                  :body (str "Handled: review-id:404\n- " verb " " marker ": verified outcome")}
          count-with #(law/unanswered-review-count [review] [%] {:rounds 6})]
      (is (= 0 (count-with answer)))
      (is (= 1 (count-with (assoc-in answer [:user :login] "human-reviewer"))))
      (is (= 1 (count-with (assoc-in answer [:user :login] "HUMAN-REVIEWER"))))
      (is (= 1 (count-with (dissoc answer :user))))
      (is (= 1 (law/unanswered-review-count [(dissoc review :user)] [answer]))))))

(deftest opaque-review-body-has-a-real-settlement-path
  (let [r {:id 303 :user {:login "human-reviewer" :type "User"}
           :state "CHANGES_REQUESTED" :body "P1: denied authorization is ignored"
           :submitted_at "2026-10-03T01:00:00Z"}
        answer {:user {:login "different-writer" :type "User"} :created_at "2026-10-03T01:01:00Z"
                :body "Handled: review-id:303\n- Fixed review-body:303: repaired authorization, regression passes"}]
    (is (= 1 (law/unanswered-review-count [r] [])))
    (is (= 0 (law/unanswered-review-count [r] [answer])))
    (is (= 1 (law/unanswered-review-count [r] [(assoc answer :body "Handled: review-id:303\n- Deferred review-body:303: later")])))
    (is (= 0 (law/unanswered-review-count [{:id 304 :body "Nitpick comments (0)"}] [])))
    (is (= 1 (law/unanswered-review-count [(assoc r :state "COMMENTED" :body "Outside diff range comments (1): legacy finding")] [])))))

(deftest review-body-answer-must-name-review
  (let [body (str "<summary><em>🟠 Major</em> · Must fix · <code>x:1</code></summary>"
                  "details <!-- cr-comment:v1:abc123 -->"
                  "<summary><em>🟡 Minor</em> · May defer · <code>x:2</code></summary>"
                  "details <!-- cr-comment:v1:def456 -->")
        review {:id 101 :user {:login "coderabbitai[bot]" :type "Bot"} :submitted_at "2026-10-01T00:00:00Z" :body body}
        answer {:user {:login "different-writer" :type "User"} :created_at "2026-10-01T00:01:00Z"
                :body "Handled: review-id:101\n- Fixed cr-comment:v1:abc123: corrected\n- Deferred cr-comment:v1:def456: card 2"}]
    (is (= 2 (count (law/review-body-findings body))))
    (is (= 0 (law/unanswered-review-count [review] [answer] {:rounds 6})))
    (is (= 1 (law/unanswered-review-count [review] [(assoc answer :body "Handled: review-id:101\n- Deferred cr-comment:v1:abc123: card 1\n- Deferred cr-comment:v1:def456: card 2")] {:rounds 6})))
    (is (= 1 (law/unanswered-review-count [review] [(assoc answer :body "Handled: review-id:101\n- Fixed cr-comment:v1:abc123: corrected")])))
    (is (= 2 (law/unanswered-review-count [review] [(assoc answer :body "Handled: generic\n- Fixed cr-comment:v1:abc123")])))))

(deftest review-body-title-may-contain-html
  (let [body "<summary><em>🟠 Major</em> · Use <code>foo</code> · <code>x:1</code></summary><blockquote>body <!-- cr-comment:v1:abc123 -->"]
    (is (= [{:id "abc123" :severity :p1 :title "Use <code>foo</code>"}]
           (law/review-body-findings body)))))

(deftest nitpick-without-item-banner-fails-closed
  (let [body "<summary>🧹 Nitpick comments (1)</summary>text <!-- cr-comment:v1:xyz789 -->"
        review {:id 202 :user {:login "coderabbitai[bot]" :type "Bot"} :submitted_at "2026-10-01T00:00:00Z" :body body}
        deferred {:user {:login "different-writer" :type "User"} :created_at "2026-10-01T00:01:00Z" :body "Handled: review-id:202\n- Deferred cr-comment:v1:xyz789: later"}
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
  (law/merge-gate (merge {:head gate-head :rounds 5 :approved-heads {"mimo" #{gate-head}}} input)))

(deftest merge-gate
  (let [done [{:name "CodeRabbit" :state "SUCCESS" :description "Review completed"}
              {:name "test" :state "SUCCESS"}]
        settled (assoc (law/classify-thread (thread "_🟡 Minor_" "Fixed in abc: verified")) :resolved? true)]
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

(deftest review-rounds-have-a-soft-minimum
  (doseq [rounds [0 1 4]]
    (is (= :iterate (law/loop-verdict {:rounds rounds :open-findings 0}))))
  (doseq [rounds [5 6 9]]
    (is (= :converged (law/loop-verdict {:rounds rounds :open-findings 0})))
    (is (= :iterate (law/loop-verdict {:rounds rounds :open-findings 1 :unanimous-approval? true}))))
  (is (= :converged (law/loop-verdict {:rounds 1 :open-findings 0 :unanimous-approval? true})))
  (is (= :iterate (law/loop-verdict {:rounds 0 :open-findings 0 :unanimous-approval? true})))
  (is (= :iterate (law/loop-verdict {:rounds 5})))
  (is (= :iterate (law/loop-verdict {})))
  (is (= :iterate (law/loop-verdict {:rounds 5 :open-findings 0 :min-review-rounds 6}))))

(deftest merge-gate-enforces-rounds-or-current-head-unanimity
  (let [participants #{"coderabbit" "codex" "mimo"}
        approvals (zipmap participants (repeat #{gate-head}))
        early {:rounds 1 :review-participants participants :approved-heads approvals}]
    (is (:pass? (gated early)))
    (is (not (:pass? (gated (assoc early :rounds 0)))))
    (is (not (:pass? (gated (assoc early :review-participants #{})))))
    (is (not (:pass? (gated (dissoc early :review-participants)))))
    (is (not (:pass? (gated (assoc early :approved-heads {"mimo" #{gate-head}})))))
    (is (not (:pass? (gated (assoc-in early [:approved-heads "codex"] #{(apply str (repeat 40 "b"))})))))
    (is (not (:pass? (gated (assoc early :review-bodies-unanswered 1)))))
    (is (:pass? (gated {:rounds 5 :review-participants participants})))
    (is (not (:pass? (law/merge-gate {:head gate-head :approved-heads {"mimo" #{gate-head}}}))))))

(def rejection-details
  "Reason: Admission rejects nil before this branch, so the proposed guard changes no reachable behavior.\nEvidence: src/domain/shape.cljc:42")

(defn rejection-thread []
  {:id "rejection-thread" :head gate-head :pr-author "riatzukiza" :resolved? true
   :identities law/default-reviewer-identities
   :comments [{:author "coderabbitai[bot]" :user {:login "coderabbitai[bot]" :type "Bot"} :body "P1: add a nil guard"}
              {:author "riatzukiza" :authorized? true :body (str "Rejection proposal for " gate-head ":\n" rejection-details)}
              {:author "chatgpt-codex-connector[bot]" :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
               :url "https://github.com/owner/repo/pull/8#discussion_r123"
               :body (str "Rejection agreement for " gate-head ":\nReason: I traced admission and agree that this guard is redundant.\nEvidence: src/domain/shape.cljc:42")}
              {:author "riatzukiza" :authorized? true :body (str "Rejected:\n" rejection-details)}]})

(deftest outright-rejection-needs-independent-native-agreement
  (let [input (rejection-thread)
        classified (law/classify-thread input)]
    (is (:rejection-approved? classified))
    (is (:settled? classified))
    (is (= "codex" (:rejection-reviewer classified)))
    (is (= "https://github.com/owner/repo/pull/8#discussion_r123" (:rejection-url classified)))
    (is (empty? (law/unsettled-blockers [classified])))
    (is (:pass? (gated {:threads [classified]})))
    (doseq [bad [(update input :comments #(vec (concat (take 2 %) (drop 3 %))))
                 (assoc-in input [:comments 2 :author] "coderabbitai[bot]")
                 (-> input (assoc-in [:comments 2 :author] "riatzukiza")
                     (assoc-in [:comments 2 :user] {:login "riatzukiza" :type "User"}))
                 (assoc-in input [:comments 2 :user :type] "User")
                 (assoc-in input [:comments 2 :user :login] "pretend-codex[bot]")
                 (assoc-in input [:comments 2 :body] "I agree. ✅")
                 (assoc-in input [:comments 2 :body] (str "Rejection agreement for " (apply str (repeat 40 "b")) ":\n" rejection-details))
                 (assoc-in input [:comments 3 :body] "Rejected: I prefer the current implementation.")
                 (assoc-in input [:comments 3 :body] "Rejected:\nReason: Not needed.\nEvidence: none")
                 (assoc-in input [:comments 3 :body] "Rejected:\nReason: A different reason.\nEvidence: src/domain/other.cljc:9")
                 (dissoc input :head)
                 (dissoc input :pr-author)
                 (-> input (update-in [:comments 1] dissoc :author) (update-in [:comments 3] dissoc :author))
                 (-> input (assoc-in [:comments 1 :author] " ") (assoc-in [:comments 3 :author] " "))
                 (assoc input :pr-author "chatgpt-codex-connector[bot]")]]
      (let [t (law/classify-thread bad)]
        (is (not (:rejection-approved? t)))
        (is (not (:settled? t)))
        (is (not (:pass? (gated {:threads [t]}))))))))

(deftest rejection-agreement-cannot-be-recycled-or-quoted
  (let [{:keys [comments] :as input} (rejection-thread)
        [opener proposal agreement settlement] comments]
    (doseq [changed [(assoc input :comments [opener agreement proposal settlement])
                     (assoc input :comments [opener proposal agreement proposal settlement])
                     (assoc-in input [:comments 2 :body] (str "> " (:body agreement)))
                     (assoc-in input [:comments 2 :body] (str "```text\n" (:body agreement) "\n```"))
                     (assoc input :comments [opener proposal agreement
                                            {:author "human-reviewer" :body "This admission path does not cover calls from the other adapter."}
                                            settlement])
                     (update input :comments conj {:author "chatgpt-codex-connector[bot]" :body "I withdraw my agreement; this still reproduces."})]]
      (is (not (:rejection-approved? (law/classify-thread changed)))))
    (testing "a configured native OpenCode identity can corroborate without joining the approval quorum"
      (let [open-code (-> input
                          (assoc-in [:identities "opencode"] #{"opencode[bot]"})
                          (assoc-in [:comments 2 :author] "opencode[bot]")
                          (assoc-in [:comments 2 :user] {:login "opencode[bot]" :type "Bot"}))]
        (is (:rejection-approved? (law/classify-thread open-code)))
        (is (= "opencode" (:rejection-reviewer (law/classify-thread open-code))))))))

(deftest first-five-rounds-do-not-clear-verified-findings-by-deferral
  (let [deferred (assoc (law/classify-thread (thread "P3: docstring correction" "Deferred to issue https://github.com/owner/repo/issues/9")) :resolved? true)]
    (doseq [rounds [1 4 5]]
      (is (not (:pass? (gated {:threads [deferred] :rounds rounds
                              :review-participants #{"mimo"}})))))
    (is (:pass? (gated {:threads [deferred] :rounds 6})))
    (is (not (:pass? (gated {:threads [(assoc deferred :severity :p1)] :rounds 6}))))))

(deftest configured-minimum-governs-thread-and-body-deferrals
  (let [deferred (assoc (law/classify-thread (thread "P3: docstring correction" "Deferred to issue https://github.com/owner/repo/issues/9")) :resolved? true)
        review {:id 505 :user {:login "human-reviewer" :type "User"} :state "COMMENTED"
                :submitted_at "2026-10-03T01:00:00Z"
                :body "<summary><em>🟡 Minor</em> · Follow-up · <code>x:1</code></summary><!-- cr-comment:v1:abc123 -->"}
        answer {:user {:login "different-writer" :type "User"} :created_at "2026-10-03T01:01:00Z"
                :body "Handled: review-id:505\n- Deferred cr-comment:v1:abc123: issue https://github.com/owner/repo/issues/9"}]
    (doseq [minimum [2 5 7] rounds [(dec minimum) minimum (inc minimum)]]
      (let [allowed? (> rounds minimum)
            context {:rounds rounds :min-review-rounds minimum}
            body-count (law/unanswered-review-count [review] [answer] context)]
        (is (= allowed? (:pass? (gated (assoc context :threads [deferred]
                                                    :review-participants #{"mimo"})))))
        (is (= (if allowed? 0 1) body-count))
        (is (= allowed? (:pass? (gated (assoc context :review-bodies-unanswered body-count
                                                    :review-participants #{"mimo"})))))
        (is (not (:pass? (gated (assoc context :threads [(assoc deferred :severity :p1)])))))))
    (is (= 1 (law/unanswered-review-count [review] [answer] {:rounds 5})))
    (is (= 0 (law/unanswered-review-count [review] [answer] {:rounds 6})))
    (is (= 1 (law/unanswered-review-count [review] [answer] {:min-review-rounds 2})))
    (is (= 1 (law/unanswered-review-count
              [(update review :body #(str/replace % "🟡 Minor" "🟠 Major"))]
              [answer] {:rounds 8 :min-review-rounds 7})))))

(deftest completed-rounds-require-the-whole-configured-cohort
  (let [participants #{"coderabbit" "codex" "mimo"}
        pass (fn [provider round] {:reviewer provider :round-id round :commit_id gate-head
                                  :submitted_at (str "2026-10-03T0" round ":00:00Z")})
        complete (vec (for [round (range 1 6) provider participants] (pass provider round)))]
    (is (= 5 (law/stage-review-rounds complete [] "code" participants)))
    (is (= 0 (law/stage-review-rounds (filter #(= 1 (:round-id %)) (remove #(= "mimo" (:reviewer %)) complete)) [] "code" participants)))
    (is (= 4 (law/stage-review-rounds (remove #(and (= 5 (:round-id %)) (= "mimo" (:reviewer %))) complete) [] "code" participants)))
    (is (= 5 (law/stage-review-rounds (concat complete complete) [] "code" participants)))
    (is (= 0 (law/stage-review-rounds complete [] "code" #{})))
    (is (= 0 (law/stage-review-rounds complete [] "code" (conj participants "kimi"))))
    (is (= 5 (law/stage-review-rounds (map #(dissoc % :round-id) complete) [] "code" participants)))
    (is (= 4 (law/stage-review-rounds (map #(if (and (= 5 (:round-id %)) (= "mimo" (:reviewer %)))
                                            (assoc % :commit_id (apply str (repeat 40 "b"))) %) complete)
                                     [] "code" participants)))))

(deftest body-rejections-have-the-same-independent-head-and-item-binding
  (let [review {:id 101 :user {:login "coderabbitai[bot]" :type "Bot"}
                :submitted_at "2026-10-03T00:00:00Z"
                :body "<summary><em>🟠 Major</em> · Guard · <code>x:1</code></summary><!-- cr-comment:v1:abc123 -->"}
        scope "review-id:101 cr-comment:v1:abc123"
        context {:head gate-head :identities law/default-reviewer-identities :pr-author "riatzukiza" :rounds 1}
        proposal {:authorized? true :user {:login "riatzukiza" :type "User"} :created_at "2026-10-03T01:00:00Z"
                  :body (str "Rejection proposal for " gate-head ": " scope "\n" rejection-details)}
        agreement {:authorized? false :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
                   :html_url "https://github.com/owner/repo/issues/8#issuecomment-123"
                   :created_at "2026-10-03T02:00:00Z"
                   :body (str "Rejection agreement for " gate-head ": " scope "\nReason: I traced admission and agree that this guard is redundant.\nEvidence: src/domain/shape.cljc:42")}
        answer {:authorized? true :user {:login "riatzukiza" :type "User"} :created_at "2026-10-03T03:00:00Z"
                :body "Handled: review-id:101\n- Rejected cr-comment:v1:abc123: Reason: Admission rejects nil before this branch, so the proposed guard changes no reachable behavior.; Evidence: src/domain/shape.cljc:42"}
        count-with #(law/unanswered-review-count [review] % context)]
    (is (= 0 (count-with [proposal agreement answer])))
    (is (= 1 (count-with [answer])))
    (is (= 1 (count-with [proposal answer])))
    (is (= 1 (count-with [proposal (update agreement :body str/replace "abc123" "def456") answer])))
    (is (= 1 (count-with [proposal (assoc-in agreement [:user :type] "User") answer])))
    (is (= 1 (count-with [proposal agreement (assoc answer :authorized? false)])))
    (is (= 1 (count-with [proposal agreement answer
                         (assoc agreement :created_at "2026-10-03T04:00:00Z"
                                :body "review-id:101 cr-comment:v1:abc123: I withdraw agreement; this still reproduces.")])))
    (is (= 1 (law/unanswered-review-count [review] [proposal agreement answer] (assoc context :head (apply str (repeat 40 "b"))))))
    (is (= 0 (count-with [proposal agreement answer
                         {:user {:login "human-reviewer" :type "User"} :created_at "2026-10-03T04:00:00Z" :body "Unrelated discussion."}])))))

(def skill-root (path/join here ".."))
(def skills-dir (path/join skill-root ".."))
(def the-flow (edn/read-string (str (fs/readFileSync (path/join skill-root "flow.edn") "utf8"))))

(deftest verified-opencode-identity-is-rejection-only
  (let [identities (get-in the-flow [:flow/defaults :review/identities])
        user {:login "opencode-agent[bot]" :type "Bot"}
        review {:id 9001 :user user :state "APPROVED" :commit_id gate-head
                :submitted_at "2026-10-03T01:00:00Z" :body "No issues found."}
        thread (-> (rejection-thread)
                   (assoc :identities identities)
                   (assoc-in [:comments 2 :author] (:login user))
                   (assoc-in [:comments 2 :user] user))]
    (is (= #{"opencode-agent[bot]"} (get identities "opencode")))
    (is (= law/default-reviewer-identities
           (select-keys identities ["coderabbit" "codex" "mimo" "kimi"])))
    (is (= #{"coderabbit" "codex" "mimo"}
           (set (for [[provider logins] identities
                      :when (and (law/eligible-reviewers provider) (seq logins))] provider))))
    (is (nil? (law/trusted-reviewer review identities)))
    (is (empty? (:approved-heads (law/review-evidence gate-head [review] [] identities))))
    (is (empty? (law/completed-review-rounds [review] [] identities)))
    (is (:settled? (law/classify-thread thread)))
    (is (= "opencode" (:rejection-reviewer (law/classify-thread thread))))))

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

(deftest every-active-stage-can-reopen-muse
  (doseq [stage [:plan :planning-review :card-ready :red :green :code-review :merge-gate]]
    (is (some #{:muse} (flow/next-states the-flow stage)) (str "Reopen " stage)))
  (is (some #{:red} (flow/next-states the-flow :card-ready)))
  (is (some #{:merged} (flow/next-states the-flow :merge-gate))))

(deftest terminal-declarations-must-name-existing-states
  (let [invalid (update the-flow :flow/terminal into #{:typo :missing})
        problems (flow/problems invalid)]
    (is (= #{{:problem :terminal-not-a-state :state :typo}
             {:problem :terminal-not-a-state :state :missing}}
           (set problems)))
    (is (= [] (flow/problems the-flow)))
    (is (some #{ {:problem :dead-end-state :state :reflected}}
              (flow/problems (assoc the-flow :flow/terminal #{}))))))

(deftest every-named-skill-exists
  (doseq [s (flow/skills the-flow)]
    (is (fs/existsSync (path/join skills-dir s "SKILL.md")) s)))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))

(run-tests 'test-law 'test-legacy)
