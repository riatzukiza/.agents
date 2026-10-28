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
            [test-issue-agreement]
            [test-informational]
            [test-actionability-history]
            [test-native-context]
            [test-opener-withdrawal]
            [test-repair-ack]
            [test-positive-thread]
            [pr-flow.flow :as flow]
            [pr-flow.law :as law]))

(def here
  "This script's directory, captured while nbb loads the file."
  (path/dirname *file*))

(def native-codex-receipt-v1
  (js->clj (js/JSON.parse
             (fs/readFileSync
               (path/join here ".." ".." ".." ".ημ" "review-evidence"
                          "codex-receipt-v1-20261016" "native-codex-passing-6206360170.json")
               "utf8"))
           :keywordize-keys true))

;; Actual GitHub-resolved full binding, supplied separately from the untouched
;; native JSON. Removing the envelope and every other mutation below is synthetic.
(def codex-receipt-v1-head "04bb88861f1ed876732e050a299bbfaabe1752af")
(def codex-receipt-v1-envelope "<!-- codex-review-receipt:v1 -->\n\n")

(deftest native-codex-receipt-v1-and-legacy-control-have-exact-head-evidence
  (let [raw native-codex-receipt-v1
        body (:body raw)
        bound (assoc raw :resolved-commit-id codex-receipt-v1-head)]
    (is (= 6206360170 (:id raw)))
    (is (str/starts-with? body codex-receipt-v1-envelope))
    (is (not (contains? raw :resolved-commit-id)))
    (doseq [[label text] [["synthetic legacy control" (subs body (count codex-receipt-v1-envelope))]
                         ["actual native receipt-v1 body" body]]]
      (let [comment (assoc bound :body text)
            evidence (law/review-evidence codex-receipt-v1-head [] [comment]
                                          law/default-reviewer-identities)]
        (is (= #{codex-receipt-v1-head} (get-in evidence [:approved-heads "codex"])) label)
        (is (= {:head codex-receipt-v1-head :channel :explicit-issue-verdict :id 6206360170}
               (get-in evidence [:approval-evidence "codex"])) label)
        (is (empty? (:incomplete-evidence evidence)) label)
        (is (= text (:body comment)) label)))
    (is (= body (:body bound) (:body native-codex-receipt-v1)))))

(deftest codex-receipt-v1-does-not-normalize-other-leading-envelopes
  ;; All cases are synthetic negatives derived from the actual native body.
  (let [wrapped (:body native-codex-receipt-v1)
        legacy (subs wrapped (count codex-receipt-v1-envelope))
        malformed [["unknown version" (str "<!-- codex-review-receipt:v2 -->\n\n" legacy)]
                   ["altered marker" (str "<!-- Codex-review-receipt:v1 -->\n\n" legacy)]
                   ["one LF" (str "<!-- codex-review-receipt:v1 -->\n" legacy)]
                   ["CRLF envelope" (str "<!-- codex-review-receipt:v1 -->\r\n\r\n" legacy)]
                   ["extra LF" (str "<!-- codex-review-receipt:v1 -->\n\n\n" legacy)]
                   ["repeated leading envelope" (str codex-receipt-v1-envelope wrapped)]
                   ["unrelated text after envelope" (str codex-receipt-v1-envelope "Example:\n" legacy)]]
        wrappers [["leading space" #(str " " %)]
                  ["leading LF" #(str "\n" %)]
                  ["leading tab" #(str "\t" %)]
                  ["leading BOM" #(str "\uFEFF" %)]
                  ["Markdown quote" #(str/replace % #"(?m)^" "> ")]
                  ["backtick fence" #(str "```text\n" % "\n```")]
                  ["tilde fence" #(str "~~~text\n" % "\n~~~")]
                  ["example preface" #(str "Generated example:\n" %)]]]
    (doseq [[label body] (concat malformed
                                (for [[kind text] [["legacy" legacy] ["wrapped" wrapped]]
                                      [label wrap] wrappers]
                                  [(str kind "/" label) (wrap text)]))]
      (is (empty? (:approved-heads
                    (law/review-evidence codex-receipt-v1-head []
                                         [(assoc native-codex-receipt-v1 :body body
                                                 :resolved-commit-id codex-receipt-v1-head)]
                                         law/default-reviewer-identities))) label))))

(deftest later-literal-or-quoted-codex-marker-is-not-a-verdict-veto
  ;; Synthetic later marker prose must neither broaden the leading-envelope
  ;; rule nor become an independent veto on an otherwise recognized body.
  (let [wrapped (:body native-codex-receipt-v1)]
    (doseq [[kind body] [["legacy" (subs wrapped (count codex-receipt-v1-envelope))]
                       ["wrapped" wrapped]]
            [tail marker] [["literal" "\n\n<!-- codex-review-receipt:v1 -->"]
                           ["quoted" "\n\n> <!-- codex-review-receipt:v1 -->"]]]
      (let [comment (assoc native-codex-receipt-v1 :body (str body marker)
                           :resolved-commit-id codex-receipt-v1-head)
            evidence (law/review-evidence codex-receipt-v1-head [] [comment]
                                          law/default-reviewer-identities)]
        (is (= #{codex-receipt-v1-head} (get-in evidence [:approved-heads "codex"]))
            (str "synthetic " kind "/" tail))
        (is (empty? (:incomplete-evidence evidence)) (str kind "/" tail))))))

(deftest in-sentence-codex-marker-preserves-incomplete-scope-admissions
  ;; Synthetic admissions appended to the unchanged native prefix/body. Removing
  ;; an inert later comment must not hide the separating whitespace in prose.
  (let [wrapped (:body native-codex-receipt-v1)]
    (doseq [[kind body] [["legacy" (subs wrapped (count codex-receipt-v1-envelope))]
                        ["wrapped" wrapped]]
            [case admission]
            [["native finding" "Review <!-- codex-review-receipt:v1 --> is incomplete."]
             ["before adjective" "Review is <!-- codex-review-receipt:v1 --> incomplete."]
             ["tab and CRLF separators" "Review\t<!-- codex-review-receipt:v1 -->\r\nis\tincomplete."]
             ["rate limited" "Review is rate <!-- codex-review-receipt:v1 --> limited."]
             ["incomplete review" "Incomplete <!-- codex-review-receipt:v1 --> review."]
             ["could not complete" "Could <!-- codex-review-receipt:v1 --> not complete the review."]]]
      (let [text (str body "\n\n" admission)
            label (str "synthetic " kind "/" case)]
        (is (true? (law/codex-positive-body? text)) label)
        (is (= :incomplete-review (law/incomplete-review-reason text)) label)))))

(deftest in-sentence-marker-controls-preserve-positive-scope
  (let [wrapped (:body native-codex-receipt-v1)
        admission "Review <!-- codex-review-receipt:v1 --> is incomplete."]
    (doseq [[kind body] [["legacy" (subs wrapped (count codex-receipt-v1-envelope))]
                        ["wrapped" wrapped]]
            [case suffix]
            [["later literal marker" "<!-- codex-review-receipt:v1 -->"]
             ["quoted admission" (str "> " admission)]
             ["fenced admission" (str "```text\n" admission "\n```")]
             ["generated admission" (str "<details>\n<summary>🧩 Analysis chain</summary>\n"
                                         admission "\n</details>")]
             ["artifact guard" "Reject incomplete <!-- codex-review-receipt:v1 --> review submissions."]
             ["no word separator" "Review<!-- codex-review-receipt:v1 -->is incomplete."]]]
      (let [text (str body "\n\n" suffix)
            comment (assoc native-codex-receipt-v1 :body text
                           :resolved-commit-id codex-receipt-v1-head)
            evidence (law/review-evidence codex-receipt-v1-head [] [comment]
                                          law/default-reviewer-identities)
            label (str "synthetic " kind "/" case)]
        (is (true? (law/codex-positive-body? text)) label)
        (is (nil? (law/incomplete-review-reason text)) label)
        (is (= #{codex-receipt-v1-head} (get-in evidence [:approved-heads "codex"])) label)))))

(deftest incomplete-phrase-fragments-do-not-cross-paragraphs-or-excluded-blocks
  (let [wrapped (:body native-codex-receipt-v1)
        controls (concat
                   (for [separator ["\n\n" "\r\n\r\n" "\r\r" "\n \t\n"]]
                     [(str "heading " (pr-str separator))
                      (str "Review" separator "Rate limited requests are retried with backoff.")])
                   [["partial heading" "Partial\n\nreview adapters retain their inputs."]
                    ["unable heading" "Unable to\n\ncomplete the review API request retries."]
                    ["quote barrier" "Review\n> example text\nis incomplete."]
                    ["fence barrier" "Review\n```text\nexample text\n```\nis incomplete."]
                    ["analysis barrier" "Review <details><summary>🧩 Analysis chain</summary>example text</details> is incomplete."]
                    ["learnings barrier" "Review <details><summary>✏️ Learnings added</summary>example text</details> is incomplete."]
                    ["share barrier" "Review <!-- This is an auto-generated comment: tweet message by coderabbit.ai -->example text<!-- end of auto-generated comment: tweet message by coderabbit.ai --> is incomplete."]])]
    (doseq [[kind body] [["legacy" (subs wrapped (count codex-receipt-v1-envelope))]
                        ["wrapped" wrapped]]
            [case suffix] controls]
      (let [text (str body "\n\n" suffix)
            label (str "synthetic " kind "/" case)]
        (is (true? (law/codex-positive-body? text)) label)
        (is (nil? (law/incomplete-review-reason text)) label)))))

(deftest live-incomplete-prose-after-examples-and-generic-inline-comments-remains-visible
  (let [wrapped (:body native-codex-receipt-v1)
        admission "Review <!-- unrelated inert note --> is incomplete."
        examples [["none" ""]
                  ["quote" "> example text\n\n"]
                  ["fence" "```text\nexample text\n```\n\n"]
                  ["analysis" "<details><summary>🧩 Analysis chain</summary>example text</details>\n\n"]
                  ["learnings" "<details><summary>✏️ Learnings added</summary>example text</details>\n\n"]
                  ["share" "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->example text<!-- end of auto-generated comment: tweet message by coderabbit.ai -->\n\n"]
                  ;; A quoted comment opener cannot consume later live prose.
                  ["quoted HTML opener" "> <!--\n\n"]]]
    (doseq [[kind body] [["legacy" (subs wrapped (count codex-receipt-v1-envelope))]
                        ["wrapped" wrapped]]
            [case example] examples]
      (let [text (str body "\n\n" example admission
                      (when (= case "quoted HTML opener") "\n-->"))
            label (str "synthetic " kind "/" case)]
        (is (true? (law/codex-positive-body? text)) label)
        (is (= :incomplete-review (law/incomplete-review-reason text)) label)))))

(deftest adjacent-quote-and-complete-fence-preserve-positive-scope
  ;; Synthetic adjacent blocks retain their actual LF/CRLF boundary. Neither
  ;; example-only prose nor a fenced admission is the live reviewer's verdict.
  (let [wrapped (:body native-codex-receipt-v1)]
    (doseq [[kind body] [["legacy" (subs wrapped (count codex-receipt-v1-envelope))]
                        ["wrapped" wrapped]]
            newline ["\n" "\r\n"]
            example ["example text" "Review is incomplete."]]
      (let [text (str body "\n\n> quoted context" newline
                      "```text" newline example newline "```")
            label (str "synthetic " kind "/" (pr-str newline) "/" example)]
        (is (true? (law/codex-positive-body? text)) label)
        (is (nil? (law/incomplete-review-reason text)) label)))))

(deftest adjacent-quote-fence-keeps-live-admission-and-ambiguity-distinct
  (let [wrapped (:body native-codex-receipt-v1)]
    (doseq [[kind body] [["legacy" (subs wrapped (count codex-receipt-v1-envelope))]
                        ["wrapped" wrapped]]
            newline ["\n" "\r\n"]]
      (let [example (str body "\n\n> quoted context" newline
                         "```text" newline "example text" newline)
            live (str example "```" newline newline
                      "Review <!-- unrelated inert note --> is incomplete.")
            unterminated (str example "Review is incomplete.")
            label (str "synthetic " kind "/" (pr-str newline))]
        (is (true? (law/codex-positive-body? live)) label)
        (is (= :incomplete-review (law/incomplete-review-reason live)) label)
        (is (false? (law/codex-positive-body? unterminated)) label)
        (is (= :ambiguous-prose (law/incomplete-review-reason unterminated)) label)))))

(deftest codex-positive-lexical-boundary-refuses-nontext-input
  ;; Input-shape refusal stays local; the caller tests above own actual evidence.
  (doseq [body [nil false 42 {} []]]
    (is (false? (law/codex-positive-body? body))))
  (is (true? (law/codex-positive-body? (:body native-codex-receipt-v1)))))

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
  (let [flow-identities (get-in the-flow [:flow/defaults :review/identities])
        identities (merge law/default-reviewer-identities flow-identities)
        user {:login "opencode-agent[bot]" :type "Bot"}
        review {:id 9001 :user user :state "APPROVED" :commit_id gate-head
                :submitted_at "2026-10-03T01:00:00Z" :body "No issues found."}
        thread (-> (rejection-thread)
                   (assoc :identities identities)
                   (assoc-in [:comments 2 :author] (:login user))
                   (assoc-in [:comments 2 :user] user))]
    (is (= #{"opencode-agent[bot]"} (get identities "opencode")))
    ;; Reviewer identities have one home (reviewers.edn); flow.edn adds none.
    (is (empty? (select-keys flow-identities (keys law/default-reviewer-identities))))
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


;; This is a decoded native historical review and a later native writer, not a
;; generated settlement or current-head approval. Tests supply the CLI's
;; existing repository-permission admission seam explicitly.
(def native-positive-fixture
  (js->clj (js/JSON.parse (fs/readFileSync
                          (path/join here "fixtures/native-services94-positive-review.json") "utf8"))
           :keywordize-keys true))
;; Native node PRR_kwDOSuY53c8AAAABRqlEYA read on 2026-10-11: exact body,
;; ID/state/commit/submission match the unchanged fixture; updatedAt equals
;; submission. The REST fixture does not itself contain this GraphQL field.
(def native-positive-review
  (assoc (:review native-positive-fixture) :updated_at "2026-10-10T19:18:33Z"))
(def native-positive-writer (assoc (:writer native-positive-fixture) :authorized? true))
(def positive-head (get-in native-positive-fixture [:context :head]))
(def positive-id "1234567890abcdef12345678")

(defn- positive-body [item]
  (str "<details>\n<summary>🔇 Additional comments (1)</summary><blockquote>\n\n"
       "<details>\n<summary>src/example.cljc (1)</summary><blockquote>\n\n"
       item "\n\n</blockquote></details>\n\n</blockquote></details>"))
(def positive-item (str "`3-5`: LGTM!\n\nAlso applies to: 7-7, 10-12\n\n<!-- cr-comment:v1:" positive-id " -->"))
(def positive-review (assoc native-positive-review :body (positive-body positive-item)))
(def positive-writer
  (assoc native-positive-writer :body
         (str "review-id:" (:id positive-review) "\n\n- Handled cr-comment:v1:" positive-id
              ": Your LGTM requests no change; retained in " positive-head ".")))
(def positive-first-item-body
  (str "- Handled cr-comment:v1:" positive-id ": Your LGTM requests no change; retained in " positive-head
       ".\n\nreview-id:" (:id positive-review)))
(defn- positive-unanswered
  ([r w] (positive-unanswered r w {:head positive-head}))
  ([r w context] (law/unanswered-review-count [r] [w] context)))

(deftest native-positive-items-have-bounded-provenance-and-handled-admission
  (let [items (law/review-findings native-positive-review)
        expected (filter :positive-body-item items)]
    (is (= 35 (count items)))
    (is (= 33 (count expected)))
    (is (= 33 (law/unanswered-review-count [native-positive-review]
                                           [native-positive-writer] {}))
        "raw/context-free parsing cannot grant settlement authority")
    (doseq [item expected]
      (is (= :p1 (:severity item)) (:id item))
      (is (= :native-additional-lgtm (get-in item [:positive-body-item :format])) (:id item))
      (is (string? (get-in item [:positive-body-item :source])) (:id item)))
    (is (= 0 (law/unanswered-review-count [native-positive-review]
                                          [native-positive-writer] {:head positive-head})))
    (is (nil? (:positive-body-item (first (filter #(= "3f516a61822bc7e8f7be997e" (:id %)) items)))))
    (is (not= positive-head (:commit_id native-positive-review)))
    (is (empty? (:approved-heads (law/review-evidence positive-head [native-positive-review] []
                                                      law/default-reviewer-identities))))
    (is (empty? (filter #(= positive-head (:commit_id %))
                       (law/completed-review-rounds [native-positive-review] [] law/default-reviewer-identities))))
    (let [gate-context {:head positive-head
                        :review-bodies-unanswered (law/unanswered-review-count
                                             [native-positive-review] [native-positive-writer]
                                             {:head positive-head})
                        :rounds law/default-min-review-rounds
                        :approved-heads (:approved-heads
                                         (law/review-evidence positive-head [native-positive-review] []
                                                              law/default-reviewer-identities))}
          gate (law/merge-gate gate-context)
          ;; Test-only approval isolates quorum from the already admitted settlement.
          approved-context (assoc gate-context :approved-heads {"mimo" #{positive-head}})]
      (is (not (:pass? gate)))
      (is (= ["No trusted exact-head APPROVED review meets the quorum"] (:reasons gate)))
      (is (:pass? (law/merge-gate approved-context)))
      (is (= ["35 unanswered review summary item(s)"
              "Review loop requires 5 completed rounds or unanimous current-head approval, with every finding settled"]
             (:reasons (law/merge-gate
                        (assoc approved-context :review-bodies-unanswered
                               (law/unanswered-review-count [native-positive-review] []
                                                            {:head positive-head})))))))))

;; Native PR144 review 5623461619 uses colon counts at both levels.
;; These format controls are synthetic; they supply no native approval evidence.
(deftest positive-native-colon-counts-preserve-bounded-admission
  (let [colon (-> (positive-body positive-item)
                  (str/replace "Additional comments (1)" "Additional comments: 1")
                  (str/replace "src/example.cljc (1)" "src/example.cljc: 1"))
        nonpositive (str "<details>\n<summary>src/concern.cljc: 1</summary><blockquote>\n\n"
                         "`8-9`: Verify authorization.\n\n"
                         "<!-- cr-comment:v1:abcdef1234567890abcdef12 -->\n\n"
                         "</blockquote></details>\n")
        mixed (-> colon
                  (str/replace "Additional comments: 1" "Additional comments: 2")
                  (str/replace "<summary>🔇 Additional comments: 2</summary><blockquote>"
                               (str "<summary>🔇 Additional comments: 2</summary><blockquote>\n" nonpositive)))]
    (doseq [body [colon
                  (str/replace colon "Additional comments: 1" "Additional comments (1)")
                  (str/replace colon "src/example.cljc: 1" "src/example.cljc (1)")]]
      (let [review (assoc positive-review :body body)
            item (first (law/review-findings review))]
        (is (= :native-additional-lgtm (get-in item [:positive-body-item :format])))
        (is (= "src/example.cljc" (get-in item [:positive-body-item :file])))
        (is (= :p1 (:severity item)))
        (is (= 0 (positive-unanswered review positive-writer)))))
    (let [review (assoc positive-review :body mixed)
          items (law/review-findings review)]
      (is (= 2 (count items)))
      (is (= 1 (count (filter :positive-body-item items))))
      (is (nil? (:positive-body-item (first items))))
      (is (= 1 (positive-unanswered review positive-writer))))
    (doseq [[label body]
            [["section mismatch" (str/replace colon "Additional comments: 1" "Additional comments: 2")]
             ["file mismatch" (str/replace colon "example.cljc: 1" "example.cljc: 2")]
             ["zero count" (str/replace colon "Additional comments: 1" "Additional comments: 0")]
             ["leading zero" (str/replace colon "example.cljc: 1" "example.cljc: 01")]
             ["unbounded count" (str/replace colon "Additional comments: 1" "Additional comments: 10000")]
             ["broken colon" (str/replace colon "Additional comments: 1" "Additional comments : 1")]
             ["negative count" (str/replace colon "example.cljc: 1" "example.cljc: -1")]
             ["mixed-group section mismatch" (str/replace mixed "Additional comments: 2" "Additional comments: 3")]
             ["quoted" (str/join "\n" (map #(str "> " %) (str/split-lines colon)))]
             ["fenced" (str "```html\n" colon "\n```")]
             ["indented" (str/join "\n" (map #(str "    " %) (str/split-lines colon)))]
             ["duplicate ID" (str colon "\n<!-- cr-comment:v1:" positive-id " -->")]
             ["mixed negative item" (str/replace colon "LGTM!" "LGTM! But fix this.")]]]
      (let [review (assoc positive-review :body body)]
        (is (every? #(nil? (:positive-body-item %)) (law/review-findings review)) label)
        (is (pos? (positive-unanswered review positive-writer)) label)))))

;; Exact Additional section read from open-hax/foresight PR144 review
;; 5623461619. Synthetic review/writer metadata below isolates format admission.
;; The eighth file has nested nonpositive prose and no stable item marker.
(deftest native-pr144-colon-section-keeps-six-positive-items-scoped
  (let [body (str
                  "<details>\n"
                  "<summary>🔇 Additional comments: 8</summary><blockquote>\n"
                  "\n"
                  "<details>\n"
                  "<summary>docs/notes/design/harness-actor-continuity.md: 1</summary><blockquote>\n"
                  "\n"
                  "`32-35`: **🔒 Security & Privacy**\n"
                  "\n"
                  "The cited design authenticates the producer but does not establish whether the payload-reference resolver enforces destination read authorization. The Knoxx/Clio resolver contract is unavailable, so this concern cannot be decided from the supplied evidence.\n"
                  "\n"
                  "<!-- cr-comment:v1:738833ecc80349af3f4ffbaa -->\n"
                  "\n"
                  "</blockquote></details>\n"
                  "<details>\n"
                  "<summary>docs/agile/kanban/epic-harness-actor-continuity.md: 1</summary><blockquote>\n"
                  "\n"
                  "`1-63`: LGTM!\n"
                  "\n"
                  "<!-- cr-comment:v1:0341dd3570a92106d785b645 -->\n"
                  "\n"
                  "</blockquote></details>\n"
                  "<details>\n"
                  "<summary>docs/agile/kanban/hac-01-codex-tools.md: 1</summary><blockquote>\n"
                  "\n"
                  "`1-56`: LGTM!\n"
                  "\n"
                  "<!-- cr-comment:v1:2aa5b19bdde6dc998e14c30a -->\n"
                  "\n"
                  "</blockquote></details>\n"
                  "<details>\n"
                  "<summary>docs/agile/kanban/hac-02-actor-bindings.md: 1</summary><blockquote>\n"
                  "\n"
                  "`1-66`: LGTM!\n"
                  "\n"
                  "<!-- cr-comment:v1:bfd57c46d5e07339a7e19ff3 -->\n"
                  "\n"
                  "</blockquote></details>\n"
                  "<details>\n"
                  "<summary>docs/agile/kanban/hac-03-codex-lifecycle.md: 1</summary><blockquote>\n"
                  "\n"
                  "`1-65`: LGTM!\n"
                  "\n"
                  "<!-- cr-comment:v1:fa124598ff5617abdea1554f -->\n"
                  "\n"
                  "</blockquote></details>\n"
                  "<details>\n"
                  "<summary>docs/agile/kanban/hac-04-hermes-orbit.md: 1</summary><blockquote>\n"
                  "\n"
                  "`1-55`: LGTM!\n"
                  "\n"
                  "<!-- cr-comment:v1:cae02b6ec8234ed28cc892fa -->\n"
                  "\n"
                  "</blockquote></details>\n"
                  "<details>\n"
                  "<summary>docs/agile/kanban/hac-07-memory-promotion.md: 1</summary><blockquote>\n"
                  "\n"
                  "`1-53`: LGTM!\n"
                  "\n"
                  "<!-- cr-comment:v1:a40f272605080d1974c3b553 -->\n"
                  "\n"
                  "</blockquote></details>\n"
                  "<details>\n"
                  "<summary>docs/agile/kanban/hac-06-layered-tool-projection.md-40-40: 1</summary><blockquote>\n"
                  "\n"
                  "`40-40`: **🗄️ Data Integrity & Integration** | **🟡 Minor** | **⚡ Quick win**\n"
                  "\n"
                  "⚠️ **Unverified finding**\n"
                  "Verification ran but could not confirm this finding. It is shown for review, not as a verified issue.\n"
                  "\n"
                  "**Verify instruction projection after contract changes.**\n"
                  "\n"
                  "Line 40 checks tool exposure, but it does not check that emitted instructions follow the effective contract or that stale instruction evidence is rejected. The card can therefore pass with correct tools and stale instructions. Add assertions for instruction content or manifests after profile exclusions and ancestor changes.\n"
                  "\n"
                  "<details>\n"
                  "<summary>Suggested criterion</summary>\n"
                  "\n"
                  "```diff\n"
                  "- - [ ] GIVEN a profile exclusion or ancestor change WHEN rebuilt THEN tool exposure follows the effective contract and stale projection evidence is invalidated.\n"
                  "+ - [ ] GIVEN a profile exclusion or ancestor change WHEN rebuilt THEN tool exposure and emitted instructions follow the effective contract, and stale tool or instruction evidence is rejected.\n"
                  "```\n"
                  "</details>\n"
                  "\n"
                  "</blockquote></details>\n"
                  "\n"
                  "</blockquote></details>")
        review (assoc positive-review :body body)
        items (law/review-findings review)
        positives (filter :positive-body-item items)
        writer (assoc positive-writer :body
                      (str "review-id:" (:id review) "\n\n"
                           (str/join "\n" (for [item positives]
                             (str "- Handled cr-comment:v1:" (:id item)
                                  " at " positive-head ": Preserved existing behavior.")))))]
    (is (= 8 (count items)))
    (is (= #{"0341dd3570a92106d785b645" "2aa5b19bdde6dc998e14c30a"
             "bfd57c46d5e07339a7e19ff3" "fa124598ff5617abdea1554f"
             "cae02b6ec8234ed28cc892fa" "a40f272605080d1974c3b553"}
           (set (map :id positives))))
    (is (every? #(= :p1 (:severity %)) items))
    (is (nil? (:positive-body-item (first items))))
    (is (= 2 (positive-unanswered review writer)))
    (is (= 8 (positive-unanswered review writer {})))
    (let [marked-fixed (update writer :body str
                               "\n- Fixed cr-comment:v1:738833ecc80349af3f4ffbaa: Verified marked security fix.")
          body-id (str "review-body:" (:id review))
          whole (first (filter #(= :review-body (:kind %)) items))]
      (is (= (str (:id review)) (:id whole)))
      (is (= :p1 (:severity whole)))
      (is (nil? (:positive-body-item whole)))
      (is (= 1 (positive-unanswered review marked-fixed))
          "settling all seven marked items must retain the unmarked concern")
      (is (= 1 (positive-unanswered review (update marked-fixed :body str
                         "\n- Handled " body-id ": No change."))))
      (is (= 0 (positive-unanswered review (update marked-fixed :body str
                         "\n- Fixed " body-id ": Added and verified instruction projection checks."))))
      (is (pos? (positive-unanswered review
                       (assoc (update marked-fixed :body str
                                 "\n- Fixed " body-id ": Verified checks.") :authorized? false)))))
    (is (empty? (:approved-heads
                  (law/review-evidence positive-head [review] [] law/default-reviewer-identities))))))

(deftest unmarked-native-additional-items-retain-whole-body-obligation
  (doseq [style [:colon :paren]]
    (let [body (-> (positive-body positive-item)
                   (str/replace positive-item "`3-5`: Verify destination authorization."))
          body (if (= style :colon)
                 (-> body (str/replace "Additional comments (1)" "Additional comments: 1")
                     (str/replace "src/example.cljc (1)" "src/example.cljc: 1")) body)
          review (assoc positive-review :body body)
          finding (first (law/review-findings review))
          fixed (assoc positive-writer :body
                       (str "review-id:" (:id review) "\n\n- Fixed review-body:" (:id review)
                            ": Verified destination authorization tests."))]
      (is (= 1 (count (law/review-findings review))))
      (is (= :review-body (:kind finding)))
      (is (= :p1 (:severity finding)))
      (is (= [review] (vec (law/outstanding-review-bodies positive-head [review]))))
      (is (= 1 (positive-unanswered review positive-writer)))
      (is (= 0 (positive-unanswered review fixed)))
      (doseq [excluded [(str "```html\n" body "\n```")
                        (str/join "\n" (map #(str "> " %) (str/split-lines body)))]]
        (is (empty? (law/review-findings (assoc review :body excluded)))))))
  (let [body (-> (positive-body positive-item)
                 (str/replace "Additional comments (1)" "Additional comments (2)")
                 (str/replace "src/example.cljc (1)" "src/example.cljc (2)")
                 (str/replace positive-item (str positive-item "\n\n---\n\n" positive-item)))
        review (assoc positive-review :body body)]
    (is (= 1 (count (filter #(= :review-body (:kind %)) (law/review-findings review))))
        "duplicate IDs cannot account for two declared items")
    (is (not-any? :positive-body-item (law/review-findings review)))))

(deftest positive-format-is-complete-and-item-scoped
  (let [item (first (law/review-findings positive-review))]
    (is (= positive-id (:id item)))
    (is (= :p1 (:severity item)))
    (is (= "src/example.cljc" (get-in item [:positive-body-item :file])))
    (is (= [[3 5] [7 7] [10 12]] (get-in item [:positive-body-item :ranges])))
    (is (= 0 (positive-unanswered positive-review positive-writer))))
  (doseq [[label body]
          [["own quoted extra text" (positive-body (str/replace positive-item "LGTM!" "LGTM!\n> Example quotation"))]
           ["own fenced extra text" (positive-body (str/replace positive-item "LGTM!" "LGTM!\n```text\nExample source\n```"))]
           ["own generated share text" (positive-body (str/replace positive-item "LGTM!" "LGTM!\n<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\nExample source\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->"))]
           ["own indented code item" (positive-body (str/join "\n" (map #(str "    " %) (str/split-lines positive-item))))]
           ["own five-space extra range" (positive-body (str/replace positive-item "Also applies to:" "     Also applies to:"))]
           ["own five-space marker" (positive-body (str/replace positive-item "<!-- cr-comment:v1:" "     <!-- cr-comment:v1:"))]
           ["own eight-space extra range" (positive-body (str/replace positive-item "Also applies to:" "        Also applies to:"))]
           ["own eight-space marker" (positive-body (str/replace positive-item "<!-- cr-comment:v1:" "        <!-- cr-comment:v1:"))]
           ["own tab-space extra range" (positive-body (str/replace positive-item "Also applies to:" "\t Also applies to:"))]
           ["own tab-space marker" (positive-body (str/replace positive-item "<!-- cr-comment:v1:" "\t <!-- cr-comment:v1:"))]
           ["own four-space-tab extra range" (positive-body (str/replace positive-item "Also applies to:" "    \tAlso applies to:"))]
           ["own four-space-tab marker" (positive-body (str/replace positive-item "<!-- cr-comment:v1:" "    \t<!-- cr-comment:v1:"))]
           ["own space-tab extra range" (positive-body (str/replace positive-item "Also applies to:" " \tAlso applies to:"))]
           ["own space-tab marker" (positive-body (str/replace positive-item "<!-- cr-comment:v1:" " \t<!-- cr-comment:v1:"))]
           ["own two-space-tab extra range" (positive-body (str/replace positive-item "Also applies to:" "  \tAlso applies to:"))]
           ["own two-space-tab marker" (positive-body (str/replace positive-item "<!-- cr-comment:v1:" "  \t<!-- cr-comment:v1:"))]
           ["own three-space-tab extra range" (positive-body (str/replace positive-item "Also applies to:" "   \tAlso applies to:"))]
           ["own three-space-tab marker" (positive-body (str/replace positive-item "<!-- cr-comment:v1:" "   \t<!-- cr-comment:v1:"))]
           ["body-only LGTM" positive-item]
           ["Windows absolute path" (str/replace (positive-body positive-item) "src/example.cljc" "C:/src/example.cljc")]
           ["backslash path" (str/replace (positive-body positive-item) "src/example.cljc" "src\\example.cljc")]
           ["wrong section" (str/replace (positive-body positive-item) "Additional comments" "Nitpick comments")]
           ["quoted section" (str/join "\n" (map #(str "> " %) (str/split-lines (positive-body positive-item))))]
           ["four-space code section" (str/join "\n" (map #(str "    " %) (str/split-lines (positive-body positive-item))))]
           ["generated share section" (str "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\n" (positive-body positive-item) "\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->")]
           ["uppercase duplicate ID" (str (positive-body positive-item) "\n<!-- cr-comment:v1:" (str/upper-case positive-id) " -->")]
           ["fenced section" (str "```html\n" (positive-body positive-item) "\n```")]
           ["unclosed fence" (str "```html\n" (positive-body positive-item))]
           ["escaped section" (str/replace (positive-body positive-item) "<details>" "&lt;details&gt;")]
           ["generated nested section" (str "<details>\n<summary>🧩 Analysis chain</summary>\n" (positive-body positive-item) "\n</details>")]
           ["foreign wrapper" (str "<div>" (positive-body positive-item) "</div>")]
           ["duplicated section" (str (positive-body positive-item) "\n" (positive-body positive-item))]
           ["duplicate ID in quote" (str (positive-body positive-item) "\n> <!-- cr-comment:v1:" positive-id " -->")]
           ["invalid primary range" (positive-body (str/replace positive-item "`3-5`" "`5-3`"))]
           ["invalid extra range" (positive-body (str/replace positive-item "10-12" "12-10"))]
           ["zero line" (positive-body (str/replace positive-item "`3-5`" "`0-5`"))]
           ["unbounded range" (positive-body (str/replace positive-item "`3-5`" "`3333333333-3333333333`"))]
           ["mismatched item count" (str/replace (positive-body positive-item) "example.cljc (1)" "example.cljc (2)")]
           ["mismatched section count" (str/replace (positive-body positive-item) "Additional comments (1)" "Additional comments (2)")]
           ["correction after positive" (positive-body (str positive-item "\nThis still needs a fix."))]
           ["explicit P1 appended" (positive-body (str positive-item "\nP1: unsafe."))]
           ["banner inserted" (positive-body (str "_⚠️ Potential issue_ | _🟠 Major_\n" positive-item))]
           ["second marker" (positive-body (str positive-item "\n<!-- cr-comment:v1:abcdef1234567890abcdef12 -->"))]
           ["sibling borrowed boundary" (str/replace (positive-body positive-item) "LGTM!" "LGTM!\n</blockquote></details>\n<details><summary>other (1)</summary><blockquote>")]
           ["missing boundary" (str/replace-first (positive-body positive-item) "</blockquote></details>" "")]
           ["non-native short ID" (str/replace (positive-body positive-item) positive-id "abc")]
           ["inline code LGTM" (positive-body (str/replace positive-item "LGTM!" "`LGTM!`"))]]]
    (let [r (assoc positive-review :body body)]
      (is (every? #(nil? (:positive-body-item %)) (law/review-findings r)) label)
      (is (pos? (positive-unanswered r positive-writer)) label))))

(deftest positive-admission-authenticates-both-native-participants
  (doseq [[label r]
          [["unconfigured Bot" (assoc-in positive-review [:user :login] "other[bot]")]
           ["human reviewer" (assoc-in positive-review [:user :type] "User")]
           ["missing reviewer type" (update positive-review :user dissoc :type)]
           ["provider claim only" (assoc positive-review :provider "coderabbit" :user {:login "other[bot]" :type "Bot"})]
           ["changes requested" (assoc positive-review :state "CHANGES_REQUESTED")]
           ["unknown state" (dissoc positive-review :state)]
           ["missing review ID" (dissoc positive-review :id)]
           ["string review ID" (update positive-review :id str)]
           ["missing reviewed SHA" (dissoc positive-review :commit_id)]
           ["short reviewed SHA" (assoc positive-review :commit_id "abc123")]
           ["missing submission" (dissoc positive-review :submitted_at)]
           ["impossible submission" (assoc positive-review :submitted_at "2026-99-99T99:99:99Z")]
           ["invalid submission" (assoc positive-review :submitted_at "unknown")]]]
    (is (= 1 (positive-unanswered r positive-writer)) label))
  (doseq [[label w]
          [["permission absent" (dissoc positive-writer :authorized?)]
           ["permission false" (assoc positive-writer :authorized? false)]
           ["permission truthy" (assoc positive-writer :authorized? "true")]
           ["missing User type" (update positive-writer :user dissoc :type)]
           ["Bot writer" (assoc-in positive-writer [:user :type] "Bot")]
           ["opener writer" (assoc positive-writer :user (:user positive-review))]
           ["blank writer" (assoc-in positive-writer [:user :login] "")]
           ["missing writer ID" (dissoc positive-writer :id)]
           ["string writer ID" (update positive-writer :id str)]
           ["missing creation" (dissoc positive-writer :created_at)]
           ["missing edit" (dissoc positive-writer :updated_at)]
           ["impossible edit" (assoc positive-writer :updated_at "2026-10-32T19:34:34Z")]
           ["malformed edit" (assoc positive-writer :updated_at "unknown")]
           ["equal creation" (assoc positive-writer :created_at (:submitted_at positive-review))]
           ["backdated creation" (assoc positive-writer :created_at "2026-10-10T18:00:00Z")]
           ["backdated edit" (assoc positive-writer :updated_at "2026-10-10T18:00:00Z")]]]
    (is (= 1 (positive-unanswered positive-review w)) label))
  (doseq [[label context]
          [["no current head" {}]
           ["short current head" {:head "abc123"}]
           ["different current head" {:head (apply str (repeat 40 "a"))}]
           ["unconfigured identity" {:head positive-head :identities {"coderabbit" #{}}}]
           ["ambiguous Bot identity" {:head positive-head :identities {"coderabbit" #{"coderabbitai[bot]"} "mimo" #{"coderabbitai[bot]"}}}]]]
    (is (= 1 (positive-unanswered positive-review positive-writer context)) label)))

(deftest positive-handled-is-live-item-and-head-bound
  (doseq [[label body]
          [["quoted writer" (str/join "\n" (map #(str "> " %) (str/split-lines (:body positive-writer))))]
           ["fenced writer" (str "```text\n" (:body positive-writer) "\n```")]
           ["unfinished fence" (str "```text\n" (:body positive-writer))]
           ["generated writer" (str "<details><summary>Example</summary>\n" (:body positive-writer) "\n</details>")]
           ["quoted head only" (str/replace (:body positive-writer) positive-head (str "\n> " positive-head))]
           ["head in HTML comment" (str/replace (:body positive-writer) positive-head (str "<!-- " positive-head " -->"))]
           ["quoted review binding" (str/replace (:body positive-writer) "review-id:" "> review-id:")]
           ["duplicate review binding" (str (:body positive-writer) "\nreview-id:" (:id positive-review))]
           ["malformed extra review binding" (str (:body positive-writer) "\nreview-id:not-a-native-id")]
           ["foreign review binding" (str (:body positive-writer) "\nreview-id:12345")]
           ["quoted item" (str/replace (:body positive-writer) "- Handled" "> - Handled")]
           ["different item" (str/replace (:body positive-writer) positive-id "abcdef1234567890abcdef12")]
           ["head only in intro" (str "Current head " positive-head ".\n" (str/replace (:body positive-writer) positive-head "the current head"))]
           ["first four-space writer item" (str "    " positive-first-item-body)]
           ["first five-space writer item" (str "     " positive-first-item-body)]
           ["first eight-space writer item" (str "        " positive-first-item-body)]
           ["first tab writer item" (str "\t" positive-first-item-body)]
           ["first tab-space writer item" (str "\t " positive-first-item-body)]
           ["first space-tab writer item" (str " \t" positive-first-item-body)]
           ["first two-space-tab writer item" (str "  \t" positive-first-item-body)]
           ["first three-space-tab writer item" (str "   \t" positive-first-item-body)]
           ["four-space review binding" (str/replace positive-first-item-body "review-id:" "    review-id:")]
           ["five-space review binding" (str/replace positive-first-item-body "review-id:" "     review-id:")]
           ["eight-space review binding" (str/replace positive-first-item-body "review-id:" "        review-id:")]
           ["tab review binding" (str/replace positive-first-item-body "review-id:" "\treview-id:")]
           ["tab-space review binding" (str/replace positive-first-item-body "review-id:" "\t review-id:")]
           ["space-tab review binding" (str/replace positive-first-item-body "review-id:" " \treview-id:")]
           ["two-space-tab review binding" (str/replace positive-first-item-body "review-id:" "  \treview-id:")]
           ["three-space-tab review binding" (str/replace positive-first-item-body "review-id:" "   \treview-id:")]
           ["inline-code review binding" (str/replace positive-first-item-body (str "review-id:" (:id positive-review)) (str "`review-id:" (:id positive-review) "`"))]
           ["HTML attribute review binding" (str/replace positive-first-item-body (str "review-id:" (:id positive-review)) (str "<span data-review=\"review-id:" (:id positive-review) "\">example</span>"))]
           ["HTML wrapper review binding" (str/replace positive-first-item-body (str "review-id:" (:id positive-review)) (str "<div>\nreview-id:" (:id positive-review) "\n</div>"))]
           ["unclosed HTML review binding" (str/replace positive-first-item-body (str "review-id:" (:id positive-review)) (str "<div>\nreview-id:" (:id positive-review)))]
           ["fenced review binding" (str/replace positive-first-item-body (str "review-id:" (:id positive-review)) (str "```text\nreview-id:" (:id positive-review) "\n```"))]
           ["quoted review binding after item" (str/replace positive-first-item-body "review-id:" "> review-id:")]
           ["generated review binding after item" (str/replace positive-first-item-body (str "review-id:" (:id positive-review)) (str "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\nreview-id:" (:id positive-review) "\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->"))]
           ["HTML comment review binding" (str/replace positive-first-item-body (str "review-id:" (:id positive-review)) (str "<!-- review-id:" (:id positive-review) " -->"))]
           ["comment-assembled binding plus quoted native binding" (str (str/replace positive-first-item-body "review-id:" "review<!-- example -->-id:") "\n> review-id:" (:id positive-review))]
           ["comment-assembled item plus fenced native item" (str (str/replace positive-first-item-body "- Handled" "- Hand<!-- example -->led") "\n```text\n" (first (str/split-lines positive-first-item-body)) "\n```")]
           ["details-assembled binding plus quoted native binding" (str (str/replace positive-first-item-body "review-id:" "review<details>example</details>-id:") "\n> review-id:" (:id positive-review))]
           ["details-assembled item plus fenced native item" (str (str/replace positive-first-item-body "- Handled" "- Hand<details>example</details>led") "\n```text\n" (first (str/split-lines positive-first-item-body)) "\n```")]
           ["incomplete opening HTML before binding" (str/replace positive-first-item-body "review-id:" "<div\nreview-id:")]
           ["incomplete closing HTML before binding" (str/replace positive-first-item-body "review-id:" "</div\nreview-id:")]
           ["inline-code item" (str/replace (:body positive-writer) "- Handled" "`- Handled")]]]
    (is (= 1 (positive-unanswered positive-review (assoc positive-writer :body body))) label))
  (testing "live first item remains admitted when the review binding follows"
    (is (= 0 (positive-unanswered positive-review (assoc positive-writer :body positive-first-item-body)))))
  (testing "configured alternate CodeRabbit identity and later edited live writer"
    (let [r (assoc-in positive-review [:user :login] "review-helper[bot]")
          w (assoc positive-writer :updated_at "2026-10-10T19:35:00Z")]
      (is (= 0 (positive-unanswered r w {:head positive-head :identities {"coderabbit" #{"review-helper[bot]"}}})))))
  (testing "unknown/P0/P1 concerns retain the existing Fixed requirement"
    (doseq [body [(str "P0 unsafe\n<!-- cr-comment:v1:" positive-id " -->")
                  (str "P1 unsafe\n<!-- cr-comment:v1:" positive-id " -->")
                  (str "Unclassified unknown concern\n<!-- cr-comment:v1:" positive-id " -->")
                  (positive-body (str/replace positive-item "LGTM!" "LGTM! But fix this."))]]
      (is (= 1 (positive-unanswered (assoc positive-review :body body) positive-writer))))))

(deftest positive-handled-refuses-xml-markup-source
  (let [binding (str "review-id:" (:id positive-review))
        item (first (str/split-lines positive-first-item-body))
        hidden-writers
        [["complete CDATA writer" (str "<![CDATA[\n" (:body positive-writer) "\n]]>")]
         ["incomplete CDATA writer" (str "<![CDATA[\n" (:body positive-writer))]
         ["complete processing instruction writer" (str "<?settlement\n" (:body positive-writer) "\n?>")]
         ["incomplete processing instruction writer" (str "<?settlement\n" (:body positive-writer))]
         ["complete declaration writer" (str "<!DOCTYPE settlement [\n" (:body positive-writer) "\n]>")]
         ["incomplete declaration writer" (str "<!DOCTYPE settlement\n" (:body positive-writer))]
         ["CDATA binding after live item" (str item "\n\n<![CDATA[\n" binding "\n]]>")]
         ["processing instruction item after live binding" (str binding "\n\n<?settlement\n" item "\n?>")]]]
    (doseq [[label body] hidden-writers]
      (is (= 1 (positive-unanswered positive-review (assoc positive-writer :body body))) label))
    (testing "markup cannot clear conservative P1 or unknown concerns"
      (doseq [body [(str "P1 unsafe\n<!-- cr-comment:v1:" positive-id " -->")
                    (str "Unclassified unknown concern\n<!-- cr-comment:v1:" positive-id " -->")]
              :let [review (assoc positive-review :body body)
                    finding (first (law/review-findings review))]]
        (is (= :p1 (:severity finding)))
        (is (nil? (:positive-body-item finding)))
        (doseq [[label writer-body] hidden-writers]
          (is (= 1 (positive-unanswered review (assoc positive-writer :body writer-body))) label))))
    (testing "ordinary native plaintext remains admitted"
      (is (= 0 (positive-unanswered positive-review positive-writer)))
      (is (= 0 (positive-unanswered positive-review (assoc positive-writer :body positive-first-item-body))))
      (is (= 0 (law/unanswered-review-count [native-positive-review]
                                            [native-positive-writer] {:head positive-head}))))))

(deftest positive-review-refuses-xml-section-boundaries
  (let [section (:body positive-review)
        hidden-sections
        [["complete CDATA section" (str "<![CDATA[\n" section "\n]]>")]
         ["incomplete CDATA section" (str "<![CDATA[\n" section)]
         ["complete processing instruction section" (str "<?review\n" section "\n?>")]
         ["incomplete processing instruction section" (str "<?review\n" section)]
         ["complete declaration section" (str "<!DOCTYPE review [\n" section "\n]>")]
         ["incomplete declaration section" (str "<!DOCTYPE review\n" section)]
         ["bare declaration start" (str "<!\n" section)]
         ["bare processing instruction start" (str "<?\n" section)]
         ["complete comment wrapper" (str "<!--\n" section "\n-->")]
         ["incomplete comment wrapper" (str "<!--\n" section)]
         ["unclosed comment after section" (str section "\n<!--")]]]
    (doseq [[label body] hidden-sections
            :let [review (assoc positive-review :body body)
                  findings (law/review-findings review)
                  finding (first findings)]]
      (is (= 1 (count findings)) label)
      (is (= positive-id (:id finding)) label)
      (is (= :p1 (:severity finding)) label)
      (is (nil? (:positive-body-item finding)) label)
      (is (= 1 (positive-unanswered review positive-writer)) label))
    (testing "supported complete native comments do not hide live sections"
      (let [review (assoc positive-review :body
                          (str "<!-- Native preface with <?example and <![CDATA[ tokens -->\n"
                               section "\n<!-- Native tail -->"))]
        (is (= :native-additional-lgtm
               (get-in (first (law/review-findings review)) [:positive-body-item :format])))
        (is (= 0 (positive-unanswered review positive-writer)))))
    (testing "the unwrapped native plaintext and actual 33-item fixture remain admitted"
      (is (= 0 (positive-unanswered positive-review positive-writer)))
      (is (= 0 (law/unanswered-review-count [native-positive-review]
                                            [native-positive-writer] {:head positive-head}))))))

(deftest positive-review-refuses-invalid-git-path-components
  (doseq [file ["src//example.cljc" "src///example.cljc" "src/library//example.cljc"
                "src/example.cljc/" "src/example.cljc//" "src/example.cljc///"
                "src//" "src/.cljs/" "/src/example.cljc" "src/./example.cljc"
                "src/../example.cljc" "C:/src/example.cljc" "src\\example.cljc"
                "src/example\u0000.cljc" "rootfile\u0000.cljc"]
          :let [review (update positive-review :body str/replace "src/example.cljc" file)
                findings (law/review-findings review)
                finding (first findings)]]
    (is (= 1 (count findings)) file)
    (is (= positive-id (:id finding)) file)
    (is (= :p1 (:severity finding)) file)
    (is (nil? (:positive-body-item finding)) file)
    (is (= 1 (positive-unanswered review positive-writer)) file))
  (doseq [file ["example.cljc" "src/library/example.cljc" "src/v1.0/example.cljc"
                ".config.cljc" "-entry.cljc" "src/λ名前.cljc" "src/%2Fexample.cljc"]
          :let [review (update positive-review :body str/replace "src/example.cljc" file)
                item (:positive-body-item (first (law/review-findings review)))]]
    (is (= :native-additional-lgtm (:format item)) file)
    (is (= file (:file item)) file)
    (is (= 0 (positive-unanswered review positive-writer)) file))
  (is (= 33 (count (filter :positive-body-item (law/review-findings native-positive-review)))))
  (is (= 0 (law/unanswered-review-count [native-positive-review]
                                        [native-positive-writer] {:head positive-head}))))


(deftest positive-review-refuses-code-indented-structural-openers
  (let [section (:body positive-review)
        opener-forms
        [["first outer details" (fn [indent] (str indent section))]
         ["outer details after blank lines" (fn [indent] (str "\n\n" indent section))]
         ["file details" (fn [indent] (str/replace section "\n\n<details>\n<summary>src/"
                                                  (str "\n\n" indent "<details>\n<summary>src/")))]
         ["outer summary" (fn [indent] (str/replace section "\n<summary>🔇"
                                                    (str "\n" indent "<summary>🔇")))]
         ["file summary" (fn [indent] (str/replace section "\n<summary>src/"
                                                   (str "\n" indent "<summary>src/")))]
         ["outer blockquote" (fn [indent] (str/replace section "(1)</summary><blockquote>\n\n<details>"
                                                       (str "(1)</summary>\n" indent "<blockquote>\n\n<details>")))]
         ["file blockquote" (fn [indent] (str/replace section "src/example.cljc (1)</summary><blockquote>"
                                                      (str "src/example.cljc (1)</summary>\n" indent "<blockquote>")))]]]
    (doseq [indent ["    " "        " "\t" " \t" "  \t" "   \t" "    \t" "\t "]
            [label body-for] opener-forms
            :let [review (assoc positive-review :body (body-for indent))
                  findings (law/review-findings review)
                  finding (first findings)
                  context (str label " / " (pr-str indent))]]
      (is (= 1 (count findings)) context)
      (is (= positive-id (:id finding)) context)
      (is (= :p1 (:severity finding)) context)
      (is (nil? (:positive-body-item finding)) context)
      (is (= 1 (positive-unanswered review positive-writer)) context))
    (doseq [body [section (str "\n\n" section) (str "\n    \n" section)
                  (str "<!-- Native example only:\n    <details>\n\t<summary>\n  \t<blockquote>\n-->\n" section)]
            :let [review (assoc positive-review :body body)]]
      (is (= :native-additional-lgtm
             (get-in (first (law/review-findings review)) [:positive-body-item :format])))
      (is (= 0 (positive-unanswered review positive-writer))))
    (is (= 33 (count (filter :positive-body-item (law/review-findings native-positive-review)))))
    (is (= 0 (law/unanswered-review-count [native-positive-review]
                                          [native-positive-writer] {:head positive-head})))))

(deftest positive-handled-refuses-lazy-blockquote-binding
  (let [binding (str "review-id:" (:id positive-review))
        item (first (str/split-lines positive-first-item-body))]
    (doseq [quote ["> quoted preface" " > quoted preface" "  > quoted preface"
                  "   > quoted preface" "> > nested preface" "> first preface\n> second preface"]]
      (is (= 1 (positive-unanswered positive-review
                                    (assoc positive-writer :body (str quote "\n" binding "\n\n" item))))
          quote))
    (doseq [preface ["Ordinary comparison: 2 > 1." "\\> escaped plaintext preface." "Ordinary native plaintext preface."]]
      (is (= 0 (positive-unanswered positive-review
                                    (assoc positive-writer :body (str preface "\n\n" binding "\n\n" item))))
          preface))
    (is (= 0 (law/unanswered-review-count [native-positive-review]
                                          [native-positive-writer] {:head positive-head})))))


(deftest positive-review-refuses-code-indented-structural-closers
  (let [section (:body positive-review)]
    (doseq [indent ["    " "        " "\t" " \t" "  \t" "   \t" "    \t" "\t "]
            [label body] [["file close" (str/replace section "\n\n</blockquote></details>\n\n"
                                                   (str "\n\n" indent "</blockquote></details>\n\n"))]
                          ["outer close" (str (subs section 0 (- (count section) (count "</blockquote></details>")))
                                              indent "</blockquote></details>")]]
            :let [review (assoc positive-review :body body)
                  findings (law/review-findings review)
                  finding (first findings)
                  context (str label " / " (pr-str indent))]]
      (is (= 1 (count findings)) context)
      (is (= positive-id (:id finding)) context)
      (is (= :p1 (:severity finding)) context)
      (is (nil? (:positive-body-item finding)) context)
      (is (= 1 (positive-unanswered review positive-writer)) context))
    (let [review (assoc positive-review :body
                        (str "<!-- Native example:\n    </blockquote></details>\n\t</summary>\n-->\n" section))]
      (is (= :native-additional-lgtm
             (get-in (first (law/review-findings review)) [:positive-body-item :format])))
      (is (= 0 (positive-unanswered review positive-writer))))))

(deftest positive-review-refuses-lazy-quoted-section
  (doseq [quote ["> quoted preface" ">quoted preface" " > quoted preface"
                 "  > quoted preface" "   > quoted preface" "> > nested preface"]
          :let [review (assoc positive-review :body (str quote "\n" (:body positive-review)))
                findings (law/review-findings review) finding (first findings)]]
    (is (= 1 (count findings)) quote)
    (is (= positive-id (:id finding)) quote)
    (is (= :p1 (:severity finding)) quote)
    (is (nil? (:positive-body-item finding)) quote)
    (is (= 1 (positive-unanswered review positive-writer)) quote))
  (doseq [preface ["Ordinary preface" "Literal > sign" "\\> escaped preface"]
          :let [review (assoc positive-review :body (str preface "\n\n" (:body positive-review)))]]
    (is (= :native-additional-lgtm
           (get-in (first (law/review-findings review)) [:positive-body-item :format])) preface)
    (is (= 0 (positive-unanswered review positive-writer)) preface)))

(deftest positive-review-admits-extensionless-native-file-names
  (doseq [file ["Makefile" "Dockerfile" "LICENSE" "README" ".gitignore" "-entry" "λ名前" "%2F"]
          :let [review (update positive-review :body str/replace "src/example.cljc" file)
                finding (first (law/review-findings review)) item (:positive-body-item finding)]]
    (is (= positive-id (:id finding)) file)
    (is (= :p1 (:severity finding)) file)
    (is (= :native-additional-lgtm (:format item)) file)
    (is (= file (:file item)) file)
    (is (= 0 (positive-unanswered review positive-writer)) file)))

(deftest positive-review-requires-clean-final-wrapper-line
  (doseq [suffix [" P1: these items require changes" "\tP0: unsafe" "adverse correction"
                  " <!-- same-line correction -->" " <details><summary>correction</summary></details>"
                  "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->P1: correction<!-- end of auto-generated comment: tweet message by coderabbit.ai -->"
                  " <!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\nP1: correction\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->"]
          :let [review (update positive-review :body str suffix)
                findings (law/review-findings review) finding (first findings)]]
    (is (= 1 (count findings)) suffix)
    (is (= positive-id (:id finding)) suffix)
    (is (= :p1 (:severity finding)) suffix)
    (is (nil? (:positive-body-item finding)) suffix)
    (is (= 1 (positive-unanswered review positive-writer)) suffix))
  (doseq [suffix ["" " " "\t" " \t  " "\n" "\r\n" " \t\r\nOrdinary later prose" " \t\nOrdinary later prose"]
          :let [review (update positive-review :body str suffix)]]
    (is (= :native-additional-lgtm
           (get-in (first (law/review-findings review)) [:positive-body-item :format])) suffix)
    (is (= 0 (positive-unanswered review positive-writer)) suffix))
  (let [review (update native-positive-review :body str/replace
                       "\n\n</blockquote></details>\n\n</blockquote></details>"
                       "\n\n</blockquote></details>\n\n</blockquote></details> P1: these items require changes")]
    (is (not= (:body native-positive-review) (:body review)))
    (is (zero? (count (filter :positive-body-item (law/review-findings review)))))
    (is (= 33 (law/unanswered-review-count [review] [native-positive-writer] {:head positive-head})))))

(deftest positive-handled-follows-latest-native-review-edit
  (doseq [[label review]
          [["missing edit time" (dissoc positive-review :updated_at)]
           ["invalid edit time" (assoc positive-review :updated_at "unknown")]
           ["impossible edit time" (assoc positive-review :updated_at "2026-10-32T19:18:33Z")]
           ["edit before submission" (assoc positive-review :updated_at "2026-10-10T19:18:32Z")]
           ["writer before edit" (assoc positive-review :updated_at "2026-10-10T19:34:35Z")]
           ["writer equals edit" (assoc positive-review :updated_at (:created_at positive-writer))]
           ["fractional edit after writer" (assoc positive-review :updated_at "2026-10-10T19:34:34.001Z")]]]
    (is (= 1 (positive-unanswered review positive-writer)) label))
  (doseq [edited [(:submitted_at positive-review) "2026-10-10T19:34:33Z" "2026-10-10T19:34:33.999Z"]]
    (is (= 0 (positive-unanswered (assoc positive-review :updated_at edited) positive-writer)) edited))
  (is (= 33 (count (filter :positive-body-item (law/review-findings native-positive-review)))))
  (is (= 0 (law/unanswered-review-count [native-positive-review] [native-positive-writer] {:head positive-head}))))

(deftest positive-review-refuses-multiline-inline-code
  (doseq [[open close] [["`" "`"] ["``" "``"] ["Example `" "` end"]
                       ["Example ``" "`` end"] ["Example ```" "``` end"]
                       ["Example `` ` unmatched" "`` end"]
                       ["\\``" "` end"] ["\\```" "`` end"] ["Example ``" "\\`` end"]]
          :let [review (assoc positive-review :body (str open "\n" (:body positive-review) "\n" close))
                findings (law/review-findings review) finding (first findings)]]
    (is (= 1 (count findings)) open)
    (is (= positive-id (:id finding)) open)
    (is (= :p1 (:severity finding)) open)
    (is (nil? (:positive-body-item finding)) open)
    (is (= 1 (positive-unanswered review positive-writer)) open))
  (doseq [preface ["Ordinary `same-line code`" "Ordinary ``single ` inside``"
                   "\\` escaped delimiter" "<!-- comment with ```\nliteral ```\ncontent -->"
                   "<!-- ordinary comment `\ncode-like example\n` -->"
                   "```text\nordinary fenced ` example\n```"
                   "Unmatched ````` literal" "Unmatched `` literal"]
          :let [review (assoc positive-review :body (str preface "\n\n" (:body positive-review)))]]
    (is (= :native-additional-lgtm
           (get-in (first (law/review-findings review)) [:positive-body-item :format])) preface)
    (is (= 0 (positive-unanswered review positive-writer)) preface))
  ;; This native-format guard conservatively rejects raw equal-run pairs
  ;; across blank paragraphs too; it is not a general Markdown renderer.
  (let [review (update positive-review :body #(str "Unmatched ` preface\n\n" %))
        finding (first (law/review-findings review))]
    (is (= positive-id (:id finding)))
    (is (= :p1 (:severity finding)))
    (is (nil? (:positive-body-item finding)))
    (is (= 1 (positive-unanswered review positive-writer))))
  (let [review (update native-positive-review :body #(str "``\n" % "\n``"))]
    (is (= (set (map :id (remove :kind (law/review-findings native-positive-review))))
           (set (map :id (law/review-findings review)))))
    (is (zero? (count (filter :positive-body-item (law/review-findings review)))))
    (is (= 33 (law/unanswered-review-count [review] [native-positive-writer] {:head positive-head})))))

(deftest positive-handled-refuses-multiline-inline-code
  (doseq [[open close] [["`" "`"] ["``" "``"] ["Example `" "` end"]
                       ["Example ``" "`` end"] ["Example ```" "``` end"]
                       ["Example `` ` unmatched" "`` end"]
                       ["\\``" "` end"] ["\\```" "`` end"] ["Example ``" "\\`` end"]]
          body [(:body positive-writer) positive-first-item-body]
          :let [writer (assoc positive-writer :body (str open "\n" body "\n" close))]]
    (is (= 1 (positive-unanswered positive-review writer)) open))
  (doseq [ending ["\r" "\r\n"] delimiter ["`" "``"]
          :let [binding (str "review-id:" (:id positive-review))
                item (first (str/split-lines positive-first-item-body))
                writer (assoc positive-writer :body
                              (str delimiter ending binding ending delimiter "\n\n" item))]]
    (is (= 1 (positive-unanswered positive-review writer)) (pr-str [ending delimiter])))
  (doseq [preface ["Ordinary `same-line code`" "Ordinary ``single ` inside``"
                   "\\` escaped single delimiter" "Unmatched ` literal" "Unmatched ````` literal" "Unmatched `` literal"]
          :let [writer (update positive-writer :body #(str preface "\n\n" %))]]
    (is (= 0 (positive-unanswered positive-review writer)) preface))
  (is (= 33 (law/unanswered-review-count [native-positive-review]
                                        [(update native-positive-writer :body #(str "``\n" % "\n``"))]
                                        {:head positive-head}))))

(deftest positive-review-refuses-incomplete-generic-html-starts
  (doseq [preface ["<script" "<SCRIPT" "<script type=\"text/plain\"" "</script"
                   "<style" "<pre" "<div" "</div" "Ordinary <script"
                   "<script\n<!-- ordinary intervening comment -->"]
          :let [review (assoc positive-review :body (str preface "\n" (:body positive-review)))
                findings (law/review-findings review) finding (first findings)]]
    (is (= 1 (count findings)) preface)
    (is (= positive-id (:id finding)) preface)
    (is (= :p1 (:severity finding)) preface)
    (is (nil? (:positive-body-item finding)) preface)
    (is (= 1 (positive-unanswered review positive-writer)) preface))
  (doseq [suffix ["<script" "</script" "<style" "<pre" "<div" "</div"]
          :let [review (update positive-review :body #(str % "\n\n" suffix))
                finding (first (law/review-findings review))]]
    (is (= positive-id (:id finding)) suffix)
    (is (= :p1 (:severity finding)) suffix)
    (is (nil? (:positive-body-item finding)) suffix)
    (is (= 1 (positive-unanswered review positive-writer)) suffix))
  (let [review (assoc positive-review :body (str "<!-- literal <script and </div examples -->\n\n"
                                                (:body positive-review)))]
    (is (= :native-additional-lgtm
           (get-in (first (law/review-findings review)) [:positive-body-item :format])))
    (is (= 0 (positive-unanswered review positive-writer)))))

(deftest positive-review-admits-literal-space-file-names
  (doseq [file ["src/my file.clj" "my file" "src/my (2) file.clj" " leading" "trailing "
                "src/ leading file.clj" "src/trailing file.clj " "src/λ 名前.cljc"]
          :let [review (update positive-review :body str/replace "src/example.cljc" file)
                finding (first (law/review-findings review)) item (:positive-body-item finding)]]
    (is (= positive-id (:id finding)) file)
    (is (= :p1 (:severity finding)) file)
    (is (= :native-additional-lgtm (:format item)) file)
    (is (= file (:file item)) file)
    (is (= 0 (positive-unanswered review positive-writer)) file))
  (doseq [file ["src/my\tfile.clj" "src/my\rfile.clj"]
          :let [review (update positive-review :body str/replace "src/example.cljc" file)]]
    (is (nil? (:positive-body-item (first (law/review-findings review)))) file)
    (is (= 1 (positive-unanswered review positive-writer)) file)))

(def skipped-scope-fixture
  (js->clj (js/JSON.parse (fs/readFileSync (path/join here "fixtures/native-agents26-skipped-review-scope.json") "utf8")) :keywordize-keys true))

(def scope-header
  "<details>\n<summary><strong>🚧 Files skipped from review as they are similar to previous changes (1)</strong></summary>")
(def scope-section (str scope-header "\n* .ημ/receipts.edn\n</details>"))
(defn scope-evidence [body]
  (law/review-evidence (:head skipped-scope-fixture) []
    [(assoc (:nativeComment skipped-scope-fixture) :body body)]
    law/default-reviewer-identities [(:observedCheck skipped-scope-fixture)]))
(deftest native-skipped-scope-refuses-positive-count-omission
  (let [body (get-in skipped-scope-fixture [:nativeComment :body])
        evidence (scope-evidence body)
        formal {:id 99001 :user (get-in skipped-scope-fixture [:nativeComment :user])
                :state "APPROVED" :commit_id (:head skipped-scope-fixture)
                :submitted_at "2026-10-11T02:35:11Z" :body body}]
    (is (= :unreviewed-input (law/incomplete-review-reason body)))
    (is (empty? (:approved-heads evidence)))
    (is (= :unreviewed-input (get-in evidence [:incomplete-evidence "coderabbit" :reason])))
    (is (empty? (:approved-heads (law/review-evidence (:head skipped-scope-fixture) [formal] [] law/default-reviewer-identities))))
    (is (not (law/full-review? formal law/default-reviewer-identities)))
    (is (empty? (law/completed-review-rounds [formal] [] law/default-reviewer-identities))))
  (doseq [n ["1" "2" "10" "999999999999999999999999999999"]]
    (is (= :unreviewed-input (law/incomplete-review-reason (str/replace scope-section "(1)" (str "(" n ")")))) n)))
(deftest native-skipped-scope-live-html-block-interrupts-quoted-preface
  (doseq [prefix ["> Example\n" "  > > Example\n"]
          :let [body (str/replace (get-in skipped-scope-fixture [:nativeComment :body])
                                 (:removedControlSection skipped-scope-fixture)
                                 (str prefix (:removedControlSection skipped-scope-fixture)))
                evidence (scope-evidence body)]]
    (is (= :unreviewed-input (law/incomplete-review-reason body)) prefix)
    (is (empty? (:approved-heads evidence)) prefix)
    (is (= :unreviewed-input (get-in evidence [:incomplete-evidence "coderabbit" :reason])) prefix)))
(deftest native-skipped-scope-inline-paragraph-cannot-hide-html-block
  (doseq [[open close] [["`\n" "\n`"] ["``\n" "\n``"] ["`\r" "\r`"] ["``\r\n" "\r\n``"]]
          :let [body (str/replace (get-in skipped-scope-fixture [:nativeComment :body])
                                 (:removedControlSection skipped-scope-fixture)
                                 (str open (:removedControlSection skipped-scope-fixture) close))
                evidence (scope-evidence body)]]
    (is (= :unreviewed-input (law/incomplete-review-reason body)) open)
    (is (empty? (:approved-heads evidence)) open)
    (is (= :unreviewed-input (get-in evidence [:incomplete-evidence "coderabbit" :reason])) open)))
(deftest native-skipped-scope-complete-control-remains-eligible
  (let [body (get-in skipped-scope-fixture [:derivedNoSkippedSectionControl :body])
        evidence (scope-evidence body)]
    (is (nil? (law/incomplete-review-reason body)))
    (is (= #{(:head skipped-scope-fixture)} (get-in evidence [:approved-heads "coderabbit"])))
    (is (empty? (:incomplete-evidence evidence)))))
(deftest native-skipped-scope-excludes-examples-without-manufacturing-admissions
  (doseq [[label body]
          [["zero" (str/replace scope-section "(1)" "(0)")]
           ["plain prose" "Files skipped from review as they are similar to previous changes (1)"]
           ["quoted" (str/join "\n" (map #(str "> " %) (str/split-lines scope-section)))]
           ["fence" (str "```html\n" scope-section "\n```")]
           ["tilde fence" (str "~~~html\n" scope-section "\n~~~")]
           ["pre wrapper" (str "<pre>\n" scope-section "\n</pre>")]
           ["code wrapper" (str "<code>\n" scope-section "\n</code>")]
           ["HTML quote" (str "<blockquote>\n" scope-section "\n</blockquote>")]
           ["comment" (str "<!--\n" scope-section "\n-->")]
           ["generated" (str "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\n" scope-section "\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->")]
           ["encoded tags" (-> scope-section (str/replace "<" "&lt;") (str/replace ">" "&gt;"))]
           ["escaped tags" (str/replace scope-section "<" "\\<")]
           ["same-line comment composition" (str/replace scope-section "<details>" "<!-- example --> <details>")]
           ["same-line inline span composition" (str "`example`" scope-section)]]]
    (is (nil? (law/incomplete-review-reason body)) label))
  (doseq [[label body]
          [["single span" (str "`\n" scope-section "\n`")]
           ["double span" (str "``\n" scope-section "\n``")]
           ["CR span" (str "`\r" (str/replace scope-section "\n" "\r") "\r`")]
           ["CRLF double span" (str "``\r\n" (str/replace scope-section "\n" "\r\n") "\r\n``")]
           ["escaped residual single" (str "\\``\n" scope-section "\n`")]
           ["escaped residual double" (str "\\```\n" scope-section "\n``")]
           ["escaped-looking closer" (str "`\n" scope-section "\n\\`")]]]
    (is (= :unreviewed-input (law/incomplete-review-reason body)) label))
  (is (= :ambiguous-prose (law/incomplete-review-reason (str "```\nexample\n```" scope-section))))
  (doseq [[label prefix]
          [["native HTML block interrupts quote" "> Example\n"]
           ["native HTML block interrupts nested quote" "  > > Example\n"]
           ["blank terminates lazy quote" "> Example\n\n"]
           ["unrelated matched span" "`line\nline`\n\n"]
           ["unmatched run" "`` example\n\n"]
           ["escaped opener" "\\` example\n\n"]
           ["literal backticks in comment" "<!-- ` example -->\n\n"]
           ["literal backticks in fence" "```\n` example\n```\n\n"]
           ["generated backticks" "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\n` example\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->\n\n"]
           ["unfamiliar unrelated tag" "<div>example</div>\n\n"]
           ["span delimiter in comment" "`\n<!-- ` -->\n"]
           ["span delimiter in fence" "`\n~~~\n`\n~~~\n"]]]
    (is (= :unreviewed-input (law/incomplete-review-reason (str prefix scope-section))) label)))

(defn scope-boundary-evidence [section label]
  (let [body (str/replace (get-in skipped-scope-fixture [:nativeComment :body])
                          (:removedControlSection skipped-scope-fixture) section)
        evidence (scope-evidence body)
        formal {:id 99002 :user (get-in skipped-scope-fixture [:nativeComment :user])
                :state "APPROVED" :commit_id (:head skipped-scope-fixture)
                :submitted_at "2026-10-11T02:35:11Z" :body body}]
    (is (= :unreviewed-input (law/incomplete-review-reason body)) label)
    (is (empty? (:approved-heads evidence)) label)
    (is (= :unreviewed-input (get-in evidence [:incomplete-evidence "coderabbit" :reason])) label)
    (is (empty? (:approved-heads (law/review-evidence (:head skipped-scope-fixture) [formal] [] law/default-reviewer-identities))) label)
    (is (not (law/full-review? formal law/default-reviewer-identities)) label)
    (is (empty? (law/completed-review-rounds [formal] [] law/default-reviewer-identities)) label)))
(deftest native-skipped-scope-indented-pseudo-fences-cannot-hide-live-block
  (doseq [indent ["    " "\t" " \t" "  \t" "   \t"]
          marker ["```" "~~~"]]
    (scope-boundary-evidence (str indent marker "html\n" scope-section "\n" indent marker)
                             (str "code-indented fence " (pr-str indent) marker))))
(deftest native-skipped-scope-escaped-wrappers-cannot-hide-live-block
  (doseq [tag ["pre" "code" "blockquote"] indent ["" "  "] escape ["\\" "\\\\\\"]]
    (scope-boundary-evidence (str indent escape "<" tag ">\n" scope-section "\n" indent escape "</" tag ">")
                             (str "escaped wrapper " (pr-str [indent escape tag])))))
(deftest native-skipped-scope-code-indented-wrappers-cannot-hide-live-block
  (doseq [tag ["pre" "code" "blockquote"] indent ["    " "\t"]]
    (scope-boundary-evidence (str indent "<" tag ">\n" scope-section "\n" indent "</" tag ">")
                             (str "code-indented wrapper " (pr-str [indent tag])))))
(deftest native-skipped-scope-valid-opener-indent-remains-live
  (doseq [indent [" " "  " "   "]]
    (scope-boundary-evidence (str/replace scope-section "<details>" (str indent "<details>"))
                             (str "native opener " (pr-str indent)))))
(deftest native-skipped-scope-valid-indented-html-block-interrupts-quote
  (doseq [indent ["" " " "  " "   "] prefix ["> Example\n" "  > > Example\n"]]
    (scope-boundary-evidence (str prefix (str/replace scope-section "<details>" (str indent "<details>")))
                             (str "native block after quote " (pr-str [prefix indent])))))
(deftest native-skipped-scope-invalid-backtick-info-cannot-hide-live-block
  (doseq [open ["```html`" "``` `example`" "````html`"]]
    (scope-boundary-evidence (str open "\n" scope-section "\n" (if (str/starts-with? open "````") "````" "```"))
                             (str "backtick in info " open))))
(deftest native-skipped-scope-real-indented-examples-remain-excluded
  (doseq [indent ["" " " "  " "   "] marker ["```" "~~~"]]
    (is (nil? (law/incomplete-review-reason (str indent marker "html\n" scope-section "\n" indent marker)))
        (str "real fence " (pr-str [indent marker]))))
  (doseq [indent ["" " " "  " "   "] tag ["pre" "code" "blockquote"]]
    (is (nil? (law/incomplete-review-reason (str indent "<" tag ">\n" scope-section "\n" indent "</" tag ">")))
        (str "real wrapper " (pr-str [indent tag]))))
  (doseq [indent ["    " "\t" " \t" "   \t"]]
    (is (nil? (law/incomplete-review-reason (str/replace scope-section "<details>" (str indent "<details>"))))
        (str "code opener " (pr-str indent)))))

(deftest native-skipped-scope-live-html-header-whitespace-remains-adverse
  (doseq [trailing [" " "\t" " \t"] prefix ["" "> Example\n"]]
    (scope-boundary-evidence (str prefix (str/replace scope-section "<details>\n" (str "<details>" trailing "\n")))
                             (str "horizontal opener suffix " (pr-str [prefix trailing]))))
  (doseq [indent [" " "    " "\t" "  \t"]]
    (scope-boundary-evidence (str/replace scope-section "\n<summary>" (str "\n" indent "<summary>"))
                             (str "raw inner summary indent " (pr-str indent))))
  (doseq [ending ["\n" "\r\n" "\r"]]
    (scope-boundary-evidence (str/replace (str/replace scope-section "<details>\n" "  <details> \t\n") "\n" ending)
                             (str "raw line boundary " (pr-str ending)))))

(deftest native-skipped-scope-inline-wrapper-prefix-cannot-hide-live-block
  (doseq [tag ["pre" "code" "blockquote"] prefix ["Example " "<!-- example --> "]]
    (scope-boundary-evidence (str prefix "<" tag ">\n" scope-section "\n</" tag ">")
                             (str "inline wrapper prefix " (pr-str [prefix tag]))))
  (is (nil? (law/incomplete-review-reason (str "~~~html`\n" scope-section "\n~~~")))
      "Backticks are permitted in tilde info strings"))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))

(run-tests 'test-law 'test-legacy 'test-issue-agreement 'test-informational 'test-actionability-history 'test-native-context 'test-opener-withdrawal 'test-repair-ack 'test-positive-thread)
