#!/usr/bin/env nbb
(ns test-policy
  (:require ["fs" :as fs] ["path" :as path]
            [nbb.core :refer [*file*]]
            [cljs.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [pr-flow.law :as law]))

(def head (apply str (repeat 40 "a")))
(def old-head (apply str (repeat 40 "b")))
(def cr-success [{:name "CodeRabbit" :state "SUCCESS" :headSha head}])
(def identities {"coderabbit" #{"coderabbitai[bot]"}
                 "codex" #{"chatgpt-codex-connector[bot]"}
                 "mimo" #{"eta-mu-ai[bot]"}
                 "kimi" #{"verified-kimi-app[bot]"}})
(defn review [login state sha]
  {:id 1 :user {:login login :type "Bot"} :state state :commit_id sha
   :submitted_at "2026-10-03T01:00:00Z"})
(def baseline {:head head :rounds 5 :approved-heads {"mimo" #{head}}
               :required-reviewers #{} :approval-quorum 1
               :checks [{:name "laws" :state "SUCCESS" :required? true}]
               :threads []})

(deftest one-approval-and-all-findings
  (is (:pass? (law/merge-gate baseline)))
  (doseq [quorum [nil 0 -1 5 "1"]]
    (is (not (:pass? (law/merge-gate (assoc baseline :approval-quorum quorum))))))
  (doseq [provider ["coderabbit" "codex" "mimo" "kimi"]]
    (is (:pass? (law/merge-gate (assoc baseline :approved-heads {provider #{head}})))))
  (doseq [approvals [{} {"mimo" #{old-head}} {"agentickey" #{head}}]]
    (is (not (:pass? (law/merge-gate (assoc baseline :approved-heads approvals))))))
  (is (not (:pass? (law/merge-gate (assoc baseline :head nil)))))
  (is (not (:pass? (law/merge-gate (assoc baseline :head "abc")))))
  (is (not (:pass? (law/merge-gate (assoc baseline :snapshot-head old-head)))))
  (is (not (:pass? (law/merge-gate (assoc baseline :review-bodies-unanswered 1)))))
  (is (not (:pass? (law/merge-gate (assoc baseline :threads [{:settled? false :resolved? true}]))))))

(deftest optional-reviewers-do-not-become-green
  (doseq [[state description] [["PENDING" "Review in progress"] ["SUCCESS" "Review rate limited"]
                               ["SKIPPED" "Review skipped"] ["FAILURE" "Review failed"]]]
    (let [g (law/merge-gate (update baseline :checks conj {:name "CodeRabbit" :state state :description description :required? false}))]
      (is (:pass? g))
      (is (not= :completed (:coderabbit g)))
      (is (not (:pass? (law/merge-gate (assoc (update baseline :checks conj {:name "CodeRabbit" :state state :description description :required? false})
                                             :required-reviewers #{"coderabbit"})))))))
  (doseq [state ["FAILURE" "PENDING" "SKIPPED" "CANCELLED"]]
    (is (not (:pass? (law/merge-gate (update baseline :checks conj {:name "laws" :state state :required? true}))))))
  (is (not (:pass? (law/merge-gate (update baseline :checks conj {:name "OpenCode evidence review gate" :state "FAILURE" :required? false}))))))

(def native-checks
  (js->clj (js/JSON.parse (fs/readFileSync
                          (path/join (path/dirname *file*) "fixtures" "native-reviewer-checks.json") "utf8"))
           :keywordize-keys true))
(defn check-row [check] (assoc check :required? (:required check)))

;; Proposed split-job source contract, not captured native execution. Keep the
;; historical JSON fixture and its observed source/head provenance unchanged.
(def reviewer-output-fixtures
  (conj (:reviewer_outputs native-checks)
        {:provider "kimi"
         :check {:name "Produce exact-head Kimi review" :workflow "OpenCode Kimi PR Review"
                 :state "IN_PROGRESS" :required false}}))

(deftest exact-native-output-tuples-retain-optional-failure-states
  (doseq [{:keys [provider check]} reviewer-output-fixtures]
    (is (= provider (law/reviewer-check check)))
    (doseq [state ["FAILURE" "PENDING" "IN_PROGRESS" "SKIPPED" "CANCELLED"]]
      (let [row (assoc (check-row check) :state state)
            input (update baseline :checks conj row)]
        (is (:pass? (law/merge-gate input)))
        ;; Classification cannot create approval or completed cohort rounds.
        (is (not (:pass? (law/merge-gate (assoc input :approved-heads {})))))
        (is (not (:pass? (law/merge-gate (assoc input :rounds 0)))))
        (is (not (:pass? (law/merge-gate
                         (update input :checks #(conj (vec (butlast %)) (assoc row :required? true)))))))
        (is (not (:pass? (law/merge-gate
                         (assoc input :required-reviewers #{provider}
                                :approved-heads (assoc (:approved-heads input) provider #{head})))))))))
  (let [rows (mapv check-row (concat (map :check reviewer-output-fixtures)
                                    (:deterministic_checks native-checks) (:required_checks native-checks)))]
    (is (:pass? (law/merge-gate (assoc baseline :checks rows))))
    (is (= ["FAILURE" "IN_PROGRESS"] (mapv :state (take 2 rows)))))
  (doseq [[name provider] [["CodeRabbit" "coderabbit"] ["Codex" "codex"] ["MiMo" "mimo"] ["Kimi" "kimi"]]]
    (is (= provider (law/reviewer-check {:name name})))))

(deftest named-provider-adjacent-and-unknown-checks-stay-deterministic
  (doseq [check (concat (:deterministic_checks native-checks) (:required_checks native-checks)
                       [{:name "OpenCode evidence review gate" :workflow "eta-mu evidence review"}
                        {:name "MiMo review" :workflow "eta-mu evidence review"}]
                       (mapcat (fn [{:keys [check]}]
                                 [(dissoc check :workflow)
                                  (assoc check :workflow "unrecognized workflow")
                                  (update check :workflow #(str % " gate"))
                                  (update check :name #(str % " tests"))
                                  (update check :name #(str "Required / " %))])
                               reviewer-output-fixtures))]
    (is (nil? (law/reviewer-check check)))
    (doseq [state ["FAILURE" "IN_PROGRESS"]]
      (is (not (:pass? (law/merge-gate
                       (update baseline :checks conj (assoc (check-row check) :state state)))))))))

(def native-state-buckets [["FAILURE" :fail] ["PENDING" :pending] ["IN_PROGRESS" :pending]
                          ["SKIPPED" :skip] ["CANCELLED" :skip] ["SUCCESS" :pass]])

(deftest deterministic-check-names-never-suppress-native-outcomes
  (doseq [check (concat (:deterministic_checks native-checks) (:required_checks native-checks)
                       (for [name ["CodeRabbit gate" "required / CodeRabbit" "CodeRabbit / lint"
                                   "coderabbit" "CodeRabbit " "unknown-context"]]
                         {:name name :workflow "eta-mu-review-gate"}))
          [state bucket] native-state-buckets]
    (let [row (assoc (check-row check) :state state)
          gate (law/merge-gate (assoc baseline :checks [row]))]
      (is (nil? (law/reviewer-check row)))
      (is (= {bucket 1} (law/check-summary [row])))
      (is (= {bucket 1} (:checks gate)))
      (when (or (:required? row) (#{:fail :pending} bucket))
        (is (= (= :pass bucket) (:pass? gate)))))))

(deftest exact-coderabbit-provider-is-optional-only-outside-obligations
  (doseq [[state bucket] native-state-buckets]
    (let [row {:name "CodeRabbit" :state state :required? false}
          optional (law/merge-gate (assoc baseline :checks [row]))
          required (law/merge-gate (assoc baseline :checks [(assoc row :required? true)]))
          mandatory (law/merge-gate (assoc baseline :checks [row]
                                         :required-reviewers #{"coderabbit"}
                                         :approved-heads {"coderabbit" #{head} "mimo" #{head}}))]
      (is (= "coderabbit" (law/reviewer-check row)))
      (is (:pass? optional))
      (is (= {} (:checks optional)))
      (doseq [gate [required mandatory]]
        (is (= {bucket 1} (:checks gate)))
        (is (= (= :pass bucket) (:pass? gate)))))))

(deftest output-names-never-authenticate-generic-actions-reviewers
  (doseq [{:keys [check]} reviewer-output-fixtures
          login ["github-actions[bot]" "opencode-agent[bot]"]]
    (let [r (merge check (review login "APPROVED" head))
          evidence (law/review-evidence head [r] [] law/default-reviewer-identities)]
      (is (nil? (law/trusted-reviewer r law/default-reviewer-identities)))
      (is (empty? (:approved-heads evidence)))
      (is (empty? (law/completed-review-rounds [r] [] law/default-reviewer-identities)))
      (is (not (:pass? (law/merge-gate (assoc baseline :checks [(check-row check)]
                                            :approved-heads (:approved-heads evidence)))))))))

(deftest exact-output-pending-attempts-remain-deduplicated
  (doseq [{:keys [provider check]} reviewer-output-fixtures]
    (let [request {:id 50 :trusted? true :created_at "2026-10-03T01:00:00Z"
                   :body (str "<!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:" provider " -->")}
          input {:head head :reviewer provider :comments []
                 :checks [(assoc check :headSha head :state "IN_PROGRESS")]}]
      (is (= :pending (:status (law/request-verdict input))))
      (is (= :pending (:status (law/request-verdict (assoc input :comments [request])))))
      (is (= :request (:status (law/request-verdict
                               (assoc input :comments [request]
                                      :checks [(assoc check :headSha head :state "FAILURE"
                                                      :completedAt "2026-10-03T01:01:00Z")])))))
      (is (= :pending (:status (law/request-verdict
                               (assoc input :comments [request]
                                      :checks [(assoc check :headSha old-head :state "FAILURE"
                                                      :completedAt "2026-10-03T01:01:00Z")]))))))))

(deftest mandatory-override-remains-all-of
  (let [defaults {:review/required #{} :review/by-repo-name {"knoxx" #{"coderabbit" "codex"}}}]
    (is (= #{} (law/required-reviewers-for defaults "riatzukiza/.agents" nil)))
    (is (= #{"codex"} (law/required-reviewers-for defaults "riatzukiza/.agents" #{"codex"})))
    (is (= #{"coderabbit" "codex" "mimo"} (law/required-reviewers-for defaults "open-hax/knoxx" #{"mimo"})))
    (is (= #{"coderabbit" "codex" "mimo"} (law/required-reviewers-for defaults "OPEN-HAX/Knoxx" #{"mimo"}))))
  (is (not (:pass? (law/merge-gate (assoc baseline :required-reviewers #{"codex" "mimo"}))))))

(deftest approval-identity-state-and-revocation
  (let [evidence #(law/review-evidence head % [] identities)]
    (doseq [provider ["coderabbit" "codex" "mimo" "kimi"]]
      (let [login (first (get identities provider))]
        (is (= #{head} (get-in (evidence [(review login "APPROVED" head)]) [:approved-heads provider])))))
    (doseq [r [(review "eta-mu-ai[bot]" "COMMENTED" head)
               (review "eta-mu-ai[bot]" "CHANGES_REQUESTED" head)
               (review "eta-mu-ai[bot]" "APPROVED" old-head)
               (review "fake-coderabbit[bot]" "APPROVED" head)
               (assoc-in (review "coderabbitai[bot]" "APPROVED" head) [:user :type] "User")
               (assoc (review "coderabbitai[bot]" "APPROVED" head) :provider "agentickey")]]
      (is (empty? (:approved-heads (evidence [r])))))
    (let [approved (review "eta-mu-ai[bot]" "APPROVED" head)
          later (assoc (review "eta-mu-ai[bot]" "CHANGES_REQUESTED" head) :id 2 :submitted_at "2026-10-03T01:01:00Z")]
      (is (empty? (:approved-heads (evidence [approved later])))))))

(def admitted-partial-scope
  (str "The staged diff was truncated at 31 of 85 files, so line-level review covered the reviewable payload; "
       "the truncated tail was bound to the exact-head deterministic gates rather than exhaustively read.\n\nConfirmed findings: none."))

(def native-preparation-only-scope
  ;; Captured scope admission from native MiMo 5401804557 on 225defda.
  ;; Its formal APPROVED state does not read these files.
  (str "The staged diff was truncated at 300000 of 634965 bytes, so 15 of 39 files - "
       "including law.cljc, actionability.cljc, flow.cljc, all five test files, and five companion skill docs - "
       "are outside this inline review; their behavior is evidenced only by the PR's own preparation logs.\n"
       "Confirmed findings: none."))

(def native-restricted-omission-scope
  ;; Native MiMo 5401976068 admits claim-only inspection of omitted source.
  ;; This restriction, not the word exhaustive alone, leaves scope incomplete.
  (str "The staged pr.diff was truncated at 300 KB of 659 KB (23 of 40 files), so inline review covered the new CI workflow; "
       "law.cljc, actionability.cljc, pr.cljs, the test files and six sibling skill docs were read at head only "
       "to validate claims anchored on staged lines and are not exhaustively reviewed.\nFindings: none confirmed."))

(deftest formal-approval-does-not-overrule-an-explicit-incomplete-scope
  (doseq [provider ["coderabbit" "codex" "mimo" "kimi"]
          state ["APPROVED" "COMMENTED"]
          body [admitted-partial-scope native-preparation-only-scope native-restricted-omission-scope "Review incomplete.\nConfirmed findings: none."
                "Partial review; unreviewed files remain.\nNo issues found."]]
    (let [r (assoc (review (first (get identities provider)) state head) :body body)
          evidence (law/review-evidence head [r] [] identities)]
      (is (empty? (:approved-heads evidence)))
      ;; A head-bound observation is preserved; it is not full-scope approval.
      (when (not= "coderabbit" provider)
        (is (= #{head} (get-in evidence [:reviewed-heads provider]))))
      (is (= (:id r) (get-in evidence [:incomplete-evidence provider :id])))
      (is (not (:pass? (law/merge-gate (assoc baseline :approved-heads (:approved-heads evidence))))))))
  (let [full (review "eta-mu-ai[bot]" "APPROVED" head)
        partial (assoc full :id 2 :submitted_at "2026-10-03T01:01:00Z" :body admitted-partial-scope)
        other-provider (review "chatgpt-codex-connector[bot]" "APPROVED" head)
        evidence (law/review-evidence head [full partial other-provider] [] identities)]
    (is (nil? (get-in evidence [:approved-heads "mimo"])))
    (is (= #{head} (get-in evidence [:approved-heads "codex"])))
    (is (:pass? (law/merge-gate (assoc baseline :approved-heads (:approved-heads evidence))))))
  (doseq [body ["Approved after reviewing all changed files."
                "The initial staged diff was truncated. I fetched and reviewed every omitted file; the full changeset is covered."
                "Approved. This review is not an exhaustive proof of program correctness."
                "Approved. No unreviewed files remain."
                "Approved.\n> Review incomplete. Unreviewed files remain."
                "Approved. Example failed run:\n```text\nReview incomplete. Unreviewed files remain.\n```"]]
    (is (= #{head} (get-in (law/review-evidence head [(assoc (review "eta-mu-ai[bot]" "APPROVED" head) :body body)] [] identities)
                           [:approved-heads "mimo"])))))

(deftest native-preparation-only-admission-revokes-approval-and-round-credit
  (doseq [body [native-preparation-only-scope native-restricted-omission-scope]]
    (let [full (review "eta-mu-ai[bot]" "APPROVED" head)
        partial (assoc full :id 5401804557 :submitted_at "2026-10-03T01:01:00Z"
                       :body body)
        evidence (law/review-evidence head [full partial] [] identities)]
    (is (nil? (get-in evidence [:approved-heads "mimo"])))
    (is (= :unreviewed-input (get-in evidence [:incomplete-evidence "mimo" :reason])))
    (is (not (law/full-review? partial identities)))
    (doseq [body [(str "Approved after reading every changed file.\n> "
                      (str/replace body "\n" "\n> "))
                 (str "Approved. Example failed run:\n```text\n" body "\n```")
                 (str "Approved.\n<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\n"
                      body "\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->")
                 "The initial staged diff was truncated. Those files were outside the inline review; I fetched and reviewed every omitted file. The full changeset is covered."]]
        (is (nil? (law/incomplete-review-reason body)))))))

(deftest native-issue-verdicts-also-require-a-completed-scope
  (let [marker (str "<!-- final_review_risk_coverage:{\"sourceCommitId\":\"" head
                    "\",\"coveredCommitId\":\"" head "\",\"kind\":\"reviewed\"} -->")
        cr {:id 8 :user {:login "coderabbitai[bot]" :type "Bot"} :updated_at "2026-10-03T01:01:00Z"
            :body (str "<!-- recent_review_start -->\nNo actionable comments were generated in the recent review.\n"
                       "Reviewing files between " old-head " and " head ".\n<!-- recent_review_end -->\n" marker "\nReview incomplete.")}
        codex {:id 9 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"} :updated_at "2026-10-03T01:01:00Z"
               :resolved-commit-id head :body "Codex Review: Didn't find any major issues.\nUnreviewed files remain."}]
    (doseq [comment [cr codex]]
      (let [provider (law/trusted-reviewer comment identities)
            earlier (review (get-in comment [:user :login]) "APPROVED" head)
            evidence (law/review-evidence head [earlier] [comment] identities)]
        (is (empty? (:approved-heads evidence)))
        (is (= (:id comment) (get-in evidence [:incomplete-evidence provider :id])))))))

(deftest incomplete-approval-cannot-supersede-unsettled-change-requests
  (let [request (assoc (review "reviewer" "CHANGES_REQUESTED" old-head)
                       :body "P1: reject untrusted credentials before use.")
        partial (assoc request :id 2 :state "APPROVED" :commit_id head
                       :submitted_at "2026-10-03T01:01:00Z" :body admitted-partial-scope)
        full (assoc partial :body "Approved after verifying the repaired credential boundary.")]
    (is (= [request] (vec (law/outstanding-review-bodies head [request partial]))))
    (is (empty? (law/outstanding-review-bodies head [request full])))))

(deftest review-scope-is-distinct-from-commit-binding-and-exhaustive-proof
  (doseq [body ["Review remains incomplete." "Reviewed only 2 of 5 changed files."
                "Input truncated; the omitted files were not reviewed."
                "The review was incomplete. No confirmed findings."]]
    (is (some? (law/incomplete-review-reason body))))
  (doseq [body ["Reviewed 5 of 5 changed files." "Review finished; no unreviewed files remain."
                "Diff truncation was repaired by retrieving and reviewing the tail."
                "Approved.\n<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\nPartial review\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->"]]
    (is (nil? (law/incomplete-review-reason body)))))

(deftest coverage-alone-is-not-a-passing-verdict
  (let [body (str "No actionable comments. <!-- final_review_risk_coverage:{\"sourceCommitId\":\"" head
                  "\",\"coveredCommitId\":\"" head "\",\"kind\":\"reviewed\"} -->")
        comment {:user {:login "coderabbitai[bot]" :type "Bot"} :body body :updated_at "2026-10-03T01:00:00Z"}
        e (law/review-evidence head [] [comment] identities)]
    (is (= #{head} (get-in e [:reviewed-heads "coderabbit"])))
    (is (empty? (:approved-heads e)))
    (is (empty? (:approved-heads (law/review-evidence head [] [(assoc comment :body (str "APPROVED " body))] identities))))
    (is (empty? (:reviewed-heads (law/review-evidence old-head [] [comment] identities))))
    (is (empty? (:reviewed-heads (law/review-evidence head [] [(assoc-in comment [:user :login] "fake-coderabbit[bot]")] identities))))))

(deftest explicit-passing-verdicts-require-current-coverage
  (let [marker (str "<!-- final_review_risk_coverage:{\"sourceCommitId\":\"" head
                    "\",\"coveredCommitId\":\"" head "\",\"kind\":\"reviewed\"} -->")
        cr {:id 3 :user {:login "coderabbitai[bot]" :type "Bot"}
            :updated_at "2026-10-03T01:02:00Z"
            :body (str "<!-- recent_review_start -->\nNo actionable comments were generated in the recent review.\n"
                       "Reviewing files between " old-head " and " head ".\n<!-- recent_review_end -->\n" marker)}
        codex {:id 4 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
               :updated_at "2026-10-03T01:02:00Z" :resolved-commit-id head
               :body (str "Codex Review: Didn't find any major issues. :tada:\n\n**Reviewed commit:** `" (subs head 0 10) "`")}
        passing (assoc (review "eta-mu-ai[bot]" "COMMENTED" head)
                       :body "Confirmed findings: none — no candidate survived adversarial validation.")]
    (is (= #{head} (get-in (law/review-evidence head [] [cr] identities cr-success) [:approved-heads "coderabbit"])))
    (is (= #{head} (get-in (law/review-evidence head [] [codex] identities) [:approved-heads "codex"])))
    (is (= #{head} (get-in (law/review-evidence head [passing] [] identities) [:approved-heads "mimo"])))
    (is (= #{head} (get-in (law/review-evidence head [(assoc passing :body "No issues found.\n```text\nExample code\n```")] [] identities)
                           [:approved-heads "mimo"])))
    (doseq [bad [(assoc cr :body marker)
                 (assoc cr :body (str "Full review triggered. " marker))
                 (assoc cr :body (str "No actionable comments were generated in the recent review. " marker))
                 (assoc-in cr [:user :type] "User")
                 (assoc codex :resolved-commit-id old-head)
                 (dissoc codex :resolved-commit-id)
                 (assoc codex :body "Codex Review: queued. No issues reported yet.")]]
      (is (empty? (:approved-heads (law/review-evidence head [] [bad] identities cr-success)))))
    (doseq [body ["No confirmed findings yet; review queued." "Confirmed findings: none.\nReview incomplete." "No issues found.\nUnreviewed files remain."
                 "Quoted PR example:\n```text\nConfirmed findings: none.\n```\nThe review has no verdict yet."
                 "Quoted PR example:\n~~~\nNo issues found.\n~~~\nThe review has no verdict yet."]]
      (is (empty? (:approved-heads (law/review-evidence head [(assoc passing :body body)] [] identities)))))
    (is (empty? (:approved-heads (law/review-evidence old-head [passing] [cr codex] identities cr-success))))
    (let [revocation (assoc (review "coderabbitai[bot]" "CHANGES_REQUESTED" head)
                            :submitted_at "2026-10-03T01:03:00Z")]
      (is (empty? (:approved-heads (law/review-evidence head [revocation] [cr] identities cr-success)))))))

(deftest request-dedupe-cooldown-and-soft-minimum
  (let [request {:trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->")}
        input {:head head :reviewer "coderabbit" :comments [request] :checks [] :rounds 1 :max-loops 6
               :now-ms 1790989260000 :identities identities}]
    (is (= :pending (:status (law/request-verdict input))))
    (is (= :request (:status (law/request-verdict (assoc input :head old-head)))))
    (is (= :request (:status (law/request-verdict (assoc input :comments [(assoc request :trusted? false)])))))
    (is (= :request (:status (law/request-verdict (assoc input :rounds 6 :comments [])))))
    (is (= :cooldown (:status (law/request-verdict
                                  (assoc input :comments [{:user {:login "coderabbitai[bot]" :type "Bot"}
                                                          :updated_at "2026-10-03T01:00:00Z"
                                                          :body "Review limit reached. Please wait 53 minutes and 12 seconds."}])))))
    (is (= 3192000 (law/cooldown-ms "Review limit reached. Please wait 53 minutes and 12 seconds.")))
    (is (= 8000 (law/cooldown-ms "Your included review limit is currently reached. Your next included review will be available in 8 seconds.")))
    (is (nil? (law/cooldown-ms "Your plan provides 5 included reviews per hour; 0 remain.")))
    (is (= :rate-limited (:status (law/request-verdict
                                      (assoc input :comments [{:user {:login "coderabbitai[bot]" :type "Bot"}
                                                              :body "Review limit reached."}])))))
    (is (= :pending (:status (law/request-verdict (assoc input :comments [] :checks [{:name "CodeRabbit" :state "PENDING"}])))))))

(deftest current-included-review-cooldown-uses-the-updated-comment-time
  ;; Native #125 warning reduced to its actual heading and cooldown line;
  ;; omit organization links, run metadata and generic prose. Cover both the
  ;; user's 09:19:47/57-minute observation and the saved 09:22:17/55-minute edit.
  (doseq [[updated minutes reset] [["2026-10-03T09:19:47Z" 57 "2026-10-03T10:16:47Z"]
                                  ["2026-10-03T09:22:17Z" 55 "2026-10-03T10:17:17Z"]]]
    (let [body (str "> ## Review limit reached\n>\n> **Next included review available in " minutes " minutes.**")
          limit {:user {:login "coderabbitai[bot]" :type "Bot"}
                 :created_at "2026-10-03T08:00:00Z" :updated_at updated :body body}
          reset-ms (js/Date.parse reset)
          input {:head head :reviewer "coderabbit" :comments [limit] :checks []
                 :rounds 1 :max-loops 5 :identities identities}
          verdict (law/request-verdict (assoc input :now-ms (dec reset-ms)))]
      (is (= (* minutes 60000) (law/cooldown-ms body)))
      (is (= :cooldown (:status verdict)))
      (is (= reset-ms (:retry-at-ms verdict)))
      ;; Only eligibility changes at expiry; this pure law never sends a request.
      (is (= :request (:status (law/request-verdict (assoc input :now-ms reset-ms)))))
      (is (= :request (:status (law/request-verdict (assoc input :now-ms reset-ms :rounds 5)))))
      (is (= :pending (:status (law/request-verdict (assoc input :now-ms reset-ms :checks [{:name "CodeRabbit" :state "PENDING"}])))))))
  (is (= 8000 (law/cooldown-ms "Your next included review will be available in 8 seconds.")))
  (doseq [body ["Your allowance is 1 review per hour; 0 remain."
                "Your included PR review attempts over the past 7 days set your current allowance at 5 reviews per hour."]]
    (is (nil? (law/cooldown-ms body)))))

(deftest quota-notices-exclude-incidental-walkthrough-prose
  (let [input {:head head :reviewer "coderabbit" :comments [] :checks []
               :now-ms (js/Date.parse "2026-10-03T01:01:00Z") :identities identities}
        comment {:user {:login "coderabbitai[bot]" :type "Bot"}
                 :updated_at "2026-10-03T01:00:00Z"}]
    (doseq [body ["The workflow preserves rate-limited reviews without granting approval."
                  "The review limit handling tells operators to wait 53 minutes."
                  "The quota may be exceeded during repeated review attempts."
                  "Quota examples:\n```\nReview limit reached. Please wait 53 minutes.\n```"
                  "Example quoted notice:\n> ```text\n> Review limit reached. Please wait 53 minutes.\n> ```"]]
      (is (= :request (:status (law/request-verdict
                               (assoc input :comments [(assoc comment :body body)])))) body))
    (doseq [heading ["Review limit reached." "Rate limit exceeded."
                     "> ## Review limit reached" "**Review quota exceeded.**"
                     "Review rate limited."
                     "Your included review limit is currently reached."]]
      (let [notice (assoc comment :body (str heading "\nNext included review available in 20 minutes."))
            verdict (law/request-verdict (assoc input :comments [notice]))]
        (is (= :cooldown (:status verdict)) heading)
        (is (= (js/Date.parse "2026-10-03T01:20:00Z") (:retry-at-ms verdict)) heading)))
    (doseq [example ["```\nPlease wait 53 minutes.\n```"
                     "> ```text\n> Please wait 53 minutes.\n> ```"]]
      (let [notice (assoc comment :body (str "Review rate limited.\n" example
                                            "\nNext included review available in 20 minutes."))]
        (is (= (js/Date.parse "2026-10-03T01:20:00Z")
               (:retry-at-ms (law/request-verdict (assoc input :comments [notice])))))))))

(deftest quota-notices-must-follow-the-request-and-current-head-coverage
  (let [request {:trusted? true :created_at "2026-10-03T01:02:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:coderabbit -->")}
        limit {:user {:login "coderabbitai[bot]" :type "Bot"}
               :created_at "2026-10-03T01:00:00Z" :body "Review limit reached."}
        coverage {:user {:login "coderabbitai[bot]" :type "Bot"}
                  :created_at "2026-10-03T01:03:00Z"
                  :body (str "<!-- final_review_risk_coverage: {\"kind\":\"reviewed\",\"sourceCommitId\":\""
                             head "\",\"coveredCommitId\":\"" head "\"} -->")}
        input {:head head :reviewer "coderabbit" :comments [] :checks []
               :now-ms (js/Date.parse "2026-10-03T01:05:00Z") :identities identities}]
    ;; A newer request stays pending rather than inheriting an old quota reply.
    (is (= :pending (:status (law/request-verdict (assoc input :comments [limit request])))))
    ;; Coverage alone is not completion or evidence of recovered quota capacity.
    (is (= :rate-limited (:status (law/request-verdict (assoc input :comments [limit coverage])))))
    (is (= :pending (:status (law/request-verdict (assoc input :comments [limit request coverage])))))
    ;; Coverage for another commit or provider cannot clear a current notice.
    (is (= :rate-limited (:status (law/request-verdict
                                  (assoc input :comments [limit (assoc coverage :body
                                    (str "<!-- final_review_risk_coverage: {\"kind\":\"reviewed\",\"sourceCommitId\":\""
                                         old-head "\",\"coveredCommitId\":\"" old-head "\"} -->"))])))))
    (is (= :rate-limited (:status (law/request-verdict
                                  (assoc input :comments [limit (assoc-in coverage [:user :login] "eta-mu-ai[bot]")])))))
    ;; A later native edit remains current; its actual updated time controls reset.
    (let [current (assoc limit :updated_at "2026-10-03T01:04:00Z"
                         :body "Review rate limited.\nNext included review available in 20 minutes.")
          verdict (law/request-verdict (assoc input :comments [request coverage current]))]
      (is (= :cooldown (:status verdict)))
      (is (= (js/Date.parse "2026-10-03T01:24:00Z") (:retry-at-ms verdict)))
      (is (= :request (:status (law/request-verdict
                               (assoc input :comments [request current]
                                      :now-ms (js/Date.parse "2026-10-03T01:24:00Z")))))))
    (is (= :rate-limited (:status (law/request-verdict
                                  (assoc input :comments [request coverage (assoc limit :updated_at "2026-10-03T01:04:00Z")])))))))

(deftest completed-unsuccessful-request-can-retry-without-an-unrelated-push
  (let [request {:trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->")}
        input {:head head :reviewer "coderabbit" :comments [request] :rounds 1 :max-loops 5
               :now-ms 1790989260000 :identities identities}]
    (doseq [state ["FAILURE" "SKIPPED" "CANCELLED"]]
      (is (= :request (:status (law/request-verdict
                               (assoc input :checks [{:name "CodeRabbit" :state state :headSha head
                                                      :startedAt "2026-10-03T01:00:10Z" :completedAt "2026-10-03T01:01:00Z"}]))))))
    (is (= :request (:status (law/request-verdict
                             (assoc input :checks [{:name "CodeRabbit" :state "SUCCESS" :description "Review skipped"
                                                    :headSha head :completedAt "2026-10-03T01:01:00Z"}])))))
    (is (= :pending (:status (law/request-verdict
                             (assoc input :checks [{:name "CodeRabbit" :state "FAILURE" :headSha head
                                                    :completedAt "2026-10-03T00:59:00Z"}])))))
    (is (= :pending (:status (law/request-verdict
                             (assoc input :checks [{:name "CodeRabbit" :state "FAILURE" :headSha old-head
                                                    :completedAt "2026-10-03T01:01:00Z"}])))))))

(deftest native-completion-and-rest-review-are-one-round
  (let [request {:id 5 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "<!-- pr-flow-stage:code --> <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->")}
        done {:id 6 :user {:login "coderabbitai[bot]" :type "Bot"} :created_at "2026-10-03T01:00:20Z" :body "Full review finished."}
        reviewed (assoc (review "coderabbitai[bot]" "COMMENTED" head) :id 7 :submitted_at "2026-10-03T01:00:30Z"
                        :body "No actionable comments were generated.")]
    (is (= 1 (count (law/completed-review-rounds [reviewed] [request done] identities cr-success))))
    (is (= 1 (count (law/completed-review-rounds [] [request done] identities cr-success))))
    (is (empty? (law/completed-review-rounds [] [(assoc request :trusted? false) done] identities cr-success)))
    (is (empty? (law/completed-review-rounds [] [request (assoc done :body "Full review triggered.")] identities cr-success)))))

(deftest full-review-completion-retains-request-head-round-and-stage
  (let [request {:id 5 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "<!-- pr-flow-stage:planning --> <!-- pr-flow-round:2 --> <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->")}
        done {:id 6 :user {:login "coderabbitai[bot]" :type "Bot"} :created_at "2026-10-03T01:00:20Z" :body "Full review finished."}
        rounds (law/completed-review-rounds [] [request done] identities cr-success)]
    (is (= [{:reviewer "coderabbit" :round-id 2 :stage "planning" :commit_id head}]
           (mapv #(select-keys % [:reviewer :round-id :stage :commit_id]) rounds)))
    (is (= 0 (law/stage-review-rounds rounds [{:stage "code" :created_at "2026-10-03T01:00:10Z"}] "code" #{"coderabbit"})))
    (is (empty? (law/completed-review-rounds [] [request (assoc done :body "Full review finished.\nReview incomplete.")] identities cr-success)))
    (doseq [body ["Acknowledged." "## Review queued\nWorking on it." "Confirmed findings: none yet; review queued."]]
      (is (not (law/full-review? (assoc (review "eta-mu-ai[bot]" "COMMENTED" head) :body body) identities))))
    (is (law/full-review? (assoc (review "eta-mu-ai[bot]" "COMMENTED" head) :body "Full review finished.") identities))
    (is (law/full-review? (assoc (review "chatgpt-codex-connector[bot]" "COMMENTED" head)
                               :body "Here are some automated review suggestions for this pull request.\nP1: Fix the shape.") identities))
    (is (not (law/full-review? (assoc (review "eta-mu-ai[bot]" "DISMISSED" head) :body "Full review finished.") identities)))))

(deftest next-round-on-an-unchanged-head-does-not-reuse-prior-completion
  (let [request {:id 5 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit --> <!-- pr-flow-round:1 -->")}
        done {:id 6 :user {:login "coderabbitai[bot]" :type "Bot"} :created_at "2026-10-03T01:00:20Z"
              :body (str "Full review finished.\n<!-- final_review_risk_coverage: {\"kind\":\"reviewed\",\"sourceCommitId\":\"" head "\",\"coveredCommitId\":\"" head "\"} -->")}
        markerless (assoc request :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->"))
        current (assoc request :id 7 :created_at "2026-10-03T01:01:00Z"
                       :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit --> <!-- pr-flow-round:2 -->"))
        input {:head head :reviewer "coderabbit" :comments [request done] :checks cr-success :round 2 :identities identities}]
    (is (= :request (:status (law/request-verdict input))))
    (is (= :completed (:status (law/request-verdict (assoc input :round 1)))))
    (is (= :pending (:status (law/request-verdict (assoc input :comments [markerless])))))
    (is (= :pending (:status (law/request-verdict (assoc input :comments [request done current])))))
    (is (= :request (:status (law/request-verdict (assoc input :comments [(assoc request :trusted? false)])))))))

(deftest codex-native-no-findings-completes-its-request-cohort-without-rest
  (let [request {:id 5 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "<!-- pr-flow-stage:code --> <!-- pr-flow-round:1 --> <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:codex -->")}
        done {:id 6 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
              :created_at "2026-10-03T01:00:20Z" :resolved-commit-id head
              :body "Codex Review: Didn't find any major issues. :tada:"}
        completed #(law/completed-review-rounds [] [request %] identities)
        rounds (completed done)]
    (is (= [{:reviewer "codex" :round-id 1 :stage "code" :commit_id head}]
           (mapv #(select-keys % [:reviewer :round-id :stage :commit_id]) rounds)))
    (is (= 1 (law/stage-review-rounds rounds [] "code" #{"codex"})))
    (doseq [bad [(dissoc done :resolved-commit-id)
                 (assoc done :resolved-commit-id old-head)
                 (assoc-in done [:user :type] "User")
                 (assoc-in done [:user :login] "fake-codex[bot]")
                 (assoc done :body "Codex Review: queued. No issues reported yet.")
                 (assoc done :body "```text\nCodex Review: Didn't find any major issues.\n```")
                 (assoc done :body "Codex Review: Didn't find any major issues.\nReview incomplete.")]]
      (is (empty? (completed bad))))
    (is (empty? (law/completed-review-rounds [] [(assoc request :trusted? false) done] identities)))
    (let [reviewed (assoc (review "chatgpt-codex-connector[bot]" "APPROVED" head)
                          :id 7 :submitted_at "2026-10-03T01:00:30Z")]
      (is (= 1 (count (law/completed-review-rounds [reviewed] [request done] identities)))))))

(deftest latest-required-context-on-current-head
  (let [old {:name "required" :state "FAILURE" :required? true :headSha head :startedAt "2026-10-03T01:00:00Z" :workflow "CI"}
        latest (assoc old :state "SUCCESS" :startedAt "2026-10-03T02:00:00Z")]
    (is (:pass? (law/merge-gate (assoc baseline :checks [old latest]))))
    (is (:pass? (law/merge-gate (assoc baseline :checks [(assoc old :state "CANCELLED") latest]))))
    (is (not (:pass? (law/merge-gate (assoc baseline :checks [latest (assoc old :startedAt "2026-10-03T03:00:00Z")])))))
    (is (not (:pass? (law/merge-gate (assoc baseline :checks [old (assoc latest :headSha old-head)])))))
    (is (not (:pass? (law/merge-gate (assoc baseline :checks [latest (assoc old :workflow "other CI")])))))))

(deftest approval-alone-does-not-prove-coderabbit-issue-coverage
  (let [e (law/review-evidence head [(review "coderabbitai[bot]" "APPROVED" head)] [] identities)]
    (is (= #{head} (get-in e [:approved-heads "coderabbit"])))
    (is (empty? (:reviewed-heads e)))))

(defn cr-issue-verdict []
  {:id 6 :user {:login "coderabbitai[bot]" :type "Bot"}
   :created_at "2026-10-03T01:00:20Z"
   :body (str "Full review finished.\n<!-- recent_review_start -->\n"
              "No actionable comments were generated in the recent review.\n"
              "Reviewing files at " head ".\n<!-- recent_review_end -->\n"
              "<!-- final_review_risk_coverage:{\"sourceCommitId\":\"" head
              "\",\"coveredCommitId\":\"" head "\",\"kind\":\"reviewed\"} -->")})

(deftest coderabbit-issue-completions-without-checks-have-no-credit
  (let [request {:id 5 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "<!-- pr-flow-stage:code --> <!-- pr-flow-round:1 --> <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:coderabbit -->")}
        done (cr-issue-verdict)
        evidence (law/review-evidence head [] [request done] identities)]
    (is (empty? (:approved-heads evidence)))
    ;; Coverage remains an observed native fact; it is not qualified approval.
    (is (= #{head} (get-in evidence [:reviewed-heads "coderabbit"])))
    (is (empty? (law/completed-review-rounds [] [request done] identities)))))

(deftest coderabbit-issue-credit-requires-current-success-not-requiredness
  (let [request {:id 5 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "<!-- pr-flow-stage:code --> <!-- pr-flow-round:1 --> <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:coderabbit -->")}
        done (cr-issue-verdict)
        row (first cr-success)
        rejected (concat
                  [[] [(dissoc row :headSha)] [(assoc row :headSha old-head)]
                   [(assoc row :name "coderabbit-review-gate")]
                   [(assoc row :description "Review skipped")]
                   [(assoc row :description "Rate limit reached")]
                   [row (assoc row :state "FAILURE")]
                   [(assoc row :startedAt "2026-10-03T01:00:00Z")
                    (assoc row :state "FAILURE" :startedAt "2026-10-03T01:01:00Z")]
                   [(assoc row :startedAt "2026-10-03T01:00:00Z")
                    (assoc row :state "PENDING" :startedAt "2026-10-03T01:01:00Z")]]
                  (for [state ["PENDING" "QUEUED" "IN_PROGRESS" "FAILURE" "SKIPPED" "CANCELLED" "NEUTRAL" "UNKNOWN"]]
                    [(assoc row :state state)]))]
    (doseq [checks rejected required? [false true]]
      (let [checks (mapv #(assoc % :required? required?) checks)
            evidence (law/review-evidence head [] [request done] identities checks)]
        (is (empty? (:approved-heads evidence)) (pr-str checks))
        (is (= #{head} (get-in evidence [:reviewed-heads "coderabbit"])))
        (is (empty? (law/completed-review-rounds [] [request done] identities checks)))))
    (doseq [state ["SUCCESS" "PASS"] required? [false true]]
      (let [checks [(assoc row :state state :required? required?)]
            evidence (law/review-evidence head [] [request done] identities checks)]
        (is (= #{head} (get-in evidence [:approved-heads "coderabbit"])))
        (is (= :explicit-issue-verdict (get-in evidence [:approval-evidence "coderabbit" :channel])))
        (is (= 1 (count (law/completed-review-rounds [] [request done] identities checks))))))
    (let [checks [(assoc row :state "FAILURE" :startedAt "2026-10-03T01:00:00Z" :required? true)
                  (assoc row :startedAt "2026-10-03T01:01:00Z")]]
      (is (= #{head} (get-in (law/review-evidence head [] [request done] identities checks)
                            [:approved-heads "coderabbit"])))
      (is (= 1 (count (law/completed-review-rounds [] [request done] identities checks)))))))

(deftest native-rest-review-credit-is-distinct-from-issue-corroboration
  (let [formal (assoc (review "coderabbitai[bot]" "APPROVED" head)
                      :body "No actionable comments were generated.")
        done (cr-issue-verdict)]
    (doseq [checks [[] [(assoc (first cr-success) :state "FAILURE")]]]
      (let [e (law/review-evidence head [formal] [done] identities checks)]
        (is (= #{head} (get-in e [:approved-heads "coderabbit"])))
        (is (= :github-approved (get-in e [:approval-evidence "coderabbit" :channel])))
        (is (= 1 (count (law/completed-review-rounds [formal] [done] identities checks))))))
    (let [partial (update done :body str "\nReview incomplete.")
          e (law/review-evidence head [formal] [partial] identities [])]
      (is (empty? (:approved-heads e)))
      (is (= :incomplete-review (get-in e [:incomplete-evidence "coderabbit" :reason]))))))

(deftest checked-historical-issue-rounds-survive-a-new-head-without-approval
  (let [request {:id 5 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "<!-- pr-flow-stage:code --> <!-- pr-flow-round:1 --> <!-- pr-flow-review:" old-head
                            " --> <!-- pr-flow-reviewer:coderabbit -->")}
        done (update (cr-issue-verdict) :body #(str/replace % head old-head))
        history [(assoc (first cr-success) :headSha old-head)
                 (assoc (first cr-success) :state "FAILURE")]
        comments [request done]
        completed (law/completed-review-rounds [] comments identities history)]
    (is (= #{old-head} (law/coderabbit-issue-completion-heads comments identities)))
    (is (= 1 (count completed)))
    (is (= old-head (:commit_id (first completed))))
    (is (= 1 (law/stage-review-rounds completed [] "code" #{"coderabbit"})))
    (is (empty? (:approved-heads (law/review-evidence head [] comments identities history))))
    (is (empty? (law/completed-review-rounds [] comments identities cr-success)))
    (is (empty? (law/coderabbit-issue-completion-heads [(assoc request :trusted? false) done] identities)))
    (is (empty? (law/coderabbit-issue-completion-heads [request (assoc-in done [:user :type] "User")] identities)))))

(deftest native-codex-quota-is-unavailability-not-completion
  (let [request {:id 10 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@codex review <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:codex -->")}
        quota {:id 11 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
               :created_at "2026-10-03T01:01:00Z"
               :body "You have reached your Codex usage limits for code reviews."}
        input {:head head :reviewer "codex" :comments [request quota]
               :checks [] :identities identities :now-ms (js/Date.parse "2026-10-03T01:02:00Z")}]
    (is (= :rate-limited (:status (law/request-verdict input))))
    (is (empty? (law/completed-review-rounds [] [request quota] identities)))
    (is (empty? (:approved-heads (law/review-evidence head [] [request quota] identities []))))
    (doseq [body ["Example: You have reached your Codex usage limits for code reviews."
                  "```\nYou have reached your Codex usage limits for code reviews.\n```"]]
      (is (= :pending (:status (law/request-verdict
                                (assoc input :comments [request (assoc quota :body body)]))))))))

(deftest available-cohort-preserves-mandatory-reviewers-and-native-boundaries
  (let [request {:id 10 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@codex review <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:codex -->")}
        quota {:id 11 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
               :created_at "2026-10-03T01:01:00Z"
               :body "You have reached your Codex usage limits for code reviews."}
        roster (dissoc identities "kimi")
        input {:head head :comments [request quota] :reviews [] :checks []
               :identities roster :now-ms (js/Date.parse "2026-10-03T01:02:00Z")}
        available (law/reviewer-availability input)]
    (is (= #{"coderabbit" "mimo"} (:review-participants available)))
    (is (= {:available? false :mandatory? false :status :quota-unavailable
            :request-id 10 :source-id 11 :request-head head :scope :account :head head
            :observed-at "2026-10-03T01:01:00Z" :retry-at-ms nil}
           (get-in available [:review-availability "codex"])))
    (is (= #{"coderabbit" "codex" "mimo"}
           (:review-participants (law/reviewer-availability (assoc input :mandatory #{"codex"})))))
    (doseq [comments [[(assoc quota :body "Review limit reached.")] [(assoc request :trusted? false) (assoc quota :body "Review limit reached.")]
                      [(assoc request :body (str "<!-- pr-flow-review:" old-head " --> <!-- pr-flow-reviewer:codex -->")) (assoc quota :body "Review limit reached.")]
                      [request (assoc-in quota [:user :type] "User")]
                      [request (assoc quota :created_at "invalid")]
                      [request (assoc quota :body "```\nYou have reached your Codex usage limits for code reviews.\n```")]
                      [request quota (assoc request :id 12 :created_at "2026-10-03T01:02:00Z")]]]
      (is (= #{"coderabbit" "codex" "mimo"}
             (:review-participants (law/reviewer-availability (assoc input :comments comments))))))
    (let [restored (assoc (review "chatgpt-codex-connector[bot]" "APPROVED" head)
                          :id 12 :submitted_at "2026-10-03T01:02:00Z" :body "Approved")]
      (is (= #{"coderabbit" "codex" "mimo"}
             (:review-participants (law/reviewer-availability (assoc input :reviews [restored]))))))
    (let [notice (assoc quota :body "Review rate limited.\nNext included review available in 20 minutes.")
          reset (js/Date.parse "2026-10-03T01:21:00Z")]
      (is (false? (get-in (law/reviewer-availability (assoc input :comments [request notice] :now-ms (dec reset)))
                         [:review-availability "codex" :available?])))
      (is (true? (get-in (law/reviewer-availability (assoc input :comments [request notice] :now-ms reset))
                        [:review-availability "codex" :available?]))))
    (let [automatic (law/reviewer-availability (assoc input :comments [quota]))]
      (is (= #{"coderabbit" "mimo"} (:review-participants automatic)))
      (is (nil? (get-in automatic [:review-availability "codex" :request-id])))
      (is (= 11 (get-in automatic [:review-availability "codex" :source-id]))))
    (is (= #{"coderabbit" "codex" "mimo"}
           (:review-participants (law/reviewer-availability
                                  (assoc input :now-ms (js/Date.parse "2026-10-03T00:59:00Z"))))))
    (is (= #{"coderabbit" "codex" "mimo"}
           (:review-participants (law/reviewer-availability
                                  (assoc input :checks [{:name "Codex" :state "PENDING" :headSha head
                                                         :startedAt "2026-10-03T01:02:00Z"}])))))
    (let [prior-request (assoc request :body (str "<!-- pr-flow-review:" old-head " --> <!-- pr-flow-reviewer:codex -->"))
          account (law/reviewer-availability (assoc input :comments [prior-request quota]))]
      (is (= #{"coderabbit" "mimo"} (:review-participants account)))
      (is (= old-head (get-in account [:review-availability "codex" :request-head])))
      (is (= :account (get-in account [:review-availability "codex" :scope]))))
    ;; Failure, skip, pending and incomplete scope cannot invent unavailability.
    (doseq [state ["FAILURE" "SKIPPED" "PENDING"]]
      (is (= #{"coderabbit" "codex" "mimo"}
             (:review-participants (law/reviewer-availability
                                    (assoc input :comments [request]
                                           :checks [{:name "Codex" :headSha head :state state}]))))))))

(deftest current-available-pass-does-not-rewrite-historical-rounds
  (let [configured #{"coderabbit" "codex" "mimo"} available #{"coderabbit" "mimo"}
        done [{:id 1 :reviewer "coderabbit" :commit_id head :submitted_at "2026-10-03T01:02:00Z"}
              {:id 2 :reviewer "mimo" :commit_id head :submitted_at "2026-10-03T01:02:01Z"}]
        rounds #(law/available-review-rounds % [] "code" configured available head)]
    (is (= 1 (rounds done)))
    (is (= 0 (rounds [(first done)])))
    (is (= 0 (rounds (map #(assoc % :commit_id old-head) done))))
    (is (= 0 (rounds [(assoc (first done) :stage "code")
                     (assoc (second done) :stage "planning")])))
    (is (= 1 (rounds (concat done done done done done))))
    (is (= 0 (law/available-review-rounds done [] "code" configured #{} head)))
    (is (= 0 (law/available-review-rounds done [{:stage "planning" :created_at "2026-10-03T01:00:00Z"}]
                                         "code" configured available head)))
    (is (:pass? (law/merge-gate (assoc baseline :rounds 1 :review-participants available
                                     :approved-heads {"mimo" #{head} "coderabbit" #{head}}))))
    (is (not (:pass? (law/merge-gate (assoc baseline :rounds 1 :review-participants available)))))
    (is (not (:pass? (law/merge-gate (assoc baseline :rounds 1 :review-participants available
                                          :required-reviewers #{"codex"}
                                          :approved-heads {"mimo" #{head} "coderabbit" #{head}})))))))

(deftest pending-requests-survive-cohort-and-stage-advancement
  (let [request {:id 10 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:coderabbit --> <!-- pr-flow-stage:planning --> <!-- pr-flow-round:1 -->")}
        input {:head head :reviewer "coderabbit" :comments [request] :checks []
               :identities identities :round 2 :stage "code" :now-ms (js/Date.parse "2026-10-03T01:02:00Z")}
        coverage {:id 11 :user {:login "coderabbitai[bot]" :type "Bot"} :created_at "2026-10-03T01:01:00Z"
                  :body "Full review finished."}
        checked (assoc input :checks cr-success)]
    (is (= :pending (:status (law/request-verdict input))))
    (is (= :pending (:status (law/request-verdict (assoc input :round 1)))))
    (is (= :request (:status (law/request-verdict (assoc checked :comments [request coverage])))))
    (is (= :request (:status (law/request-verdict (assoc checked :round 1 :comments [request coverage])))))
    (is (= :completed (:status (law/request-verdict (assoc checked :round 1 :stage "planning" :comments [request coverage])))))
    (is (= :pending (:status (law/request-verdict
                             (assoc input :comments [request coverage] :checks [{:name "CodeRabbit" :state "PENDING"}])))))))

(deftest checked-coverage-alone-is-not-native-review-completion
  (let [request {:id 1 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:coderabbit --> <!-- pr-flow-round:1 -->")}
        quota {:id 2 :user {:login "coderabbitai[bot]" :type "Bot"} :created_at "2026-10-03T01:01:00Z"
               :body "Review rate limited. Next included review available in 50 minutes."}
        marker (str "<!-- final_review_risk_coverage:{\"kind\":\"reviewed\",\"sourceCommitId\":\""
                    head "\",\"coveredCommitId\":\"" head "\"} -->")
        coverage {:id 3 :user {:login "coderabbitai[bot]" :type "Bot"} :created_at "2026-10-03T01:02:00Z"
                  :body marker}
        checks [{:name "CodeRabbit" :state "SUCCESS" :headSha head}]
        input {:head head :reviewer "coderabbit" :round 1 :comments [request quota coverage]
               :reviews [] :checks checks :identities identities :now-ms (js/Date.parse "2026-10-03T02:00:00Z")}]
    (doseq [body [marker (str "```text\nFull review finished.\n```\n" marker)
                  (str "> Full review finished.\n" marker)]]
      (let [comments [request quota (assoc coverage :body body)]]
        (is (empty? (law/completed-review-rounds [] comments identities checks)))
        (is (empty? (law/coderabbit-issue-completion-heads comments identities)))
        (is (= :request (:status (law/request-verdict (assoc input :comments comments)))))
        (is (= :cooldown (:status (law/request-verdict
                                      (assoc input :comments comments :now-ms (js/Date.parse "2026-10-03T01:30:00Z"))))))))
    (let [done (assoc coverage :body (str "Full review finished.\n" marker))]
      (is (= 1 (count (law/completed-review-rounds [] [request done] identities checks))))
      (is (= :completed (:status (law/request-verdict (assoc input :comments [request done]))))))))

(deftest coverage-marker-cannot-complete-an-attempt-ended-by-quota
  (let [request {:id 10 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit --> <!-- pr-flow-round:1 -->")}
        coverage {:id 11 :user {:login "coderabbitai[bot]" :type "Bot"} :created_at "2026-10-03T01:00:10Z"
                  :body (str "<!-- final_review_risk_coverage: {\"kind\":\"reviewed\",\"sourceCommitId\":\""
                             head "\",\"coveredCommitId\":\"" head "\"} -->")}
        quota {:id 12 :user {:login "coderabbitai[bot]" :type "Bot"} :created_at "2026-10-03T01:01:00Z"
               :body "Review rate limited. Next included review available in 50 minutes."}
        input {:head head :reviewer "coderabbit" :comments [request coverage quota] :checks []
               :identities identities :round 1 :now-ms (js/Date.parse "2026-10-03T02:00:00Z")}]
    (is (= :request (:status (law/request-verdict input))))
    (is (= :pending (:status (law/request-verdict
                             (assoc input :comments [request coverage])))))
    (is (= :cooldown (:status (law/request-verdict (assoc input :now-ms (js/Date.parse "2026-10-03T01:30:00Z"))))))))

(deftest later-automatic-full-review-recovers-capacity-without-completing-a-new-request
  (let [quota {:id 10 :user {:login "coderabbitai[bot]" :type "Bot"}
               :created_at "2026-10-03T01:00:00Z" :body "Review limit reached."}
        done (assoc (review "coderabbitai[bot]" "APPROVED" head)
                    :id 11 :submitted_at "2026-10-03T01:01:00Z" :body "Full review finished.")
        input {:head head :reviewer "coderabbit" :comments [quota] :reviews [done] :checks []
               :identities identities :now-ms (js/Date.parse "2026-10-03T01:02:00Z")}
        request {:id 12 :trusted? true :created_at "2026-10-03T01:02:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->")}]
    (is (= :request (:status (law/request-verdict input))))
    (is (= :pending (:status (law/request-verdict (update input :comments conj request)))))
    (is (= :rate-limited (:status (law/request-verdict
                                  (assoc input :reviews [(assoc done :body "Partial review; omitted input was not reviewed.")])))))))

(deftest native-codex-completion-releases-renewal-only-after-its-request
  (let [request {:id 10 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@codex review <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:codex --> <!-- pr-flow-stage:planning --> <!-- pr-flow-round:1 -->")}
        review (assoc (review "chatgpt-codex-connector[bot]" "APPROVED" head)
                      :id 11 :submitted_at "2026-10-03T01:01:00Z" :body "Review complete.")
        input {:head head :reviewer "codex" :comments [request] :reviews [review] :checks []
               :identities identities :round 2 :stage "code" :now-ms (js/Date.parse "2026-10-03T01:02:00Z")}]
    (is (= :request (:status (law/request-verdict input))))
    (is (= :completed (:status (law/request-verdict (assoc input :round 1 :stage "planning")))))
    (doseq [r [(assoc review :commit_id old-head)
               (assoc review :submitted_at "2026-10-03T00:59:00Z")
               (assoc review :body "Partial review, omitted files remain unreviewed.")
               (assoc-in review [:user :type] "User")]]
      (is (= :pending (:status (law/request-verdict (assoc input :reviews [r]))))))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests)
