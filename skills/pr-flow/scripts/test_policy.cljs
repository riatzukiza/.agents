#!/usr/bin/env nbb
(ns test-policy
  (:require [cljs.test :refer [deftest is run-tests]]
            [pr-flow.law :as law]))

(def head (apply str (repeat 40 "a")))
(def old-head (apply str (repeat 40 "b")))
(def identities {"coderabbit" #{"coderabbitai[bot]"}
                 "codex" #{"chatgpt-codex-connector[bot]"}
                 "mimo" #{"eta-mu-ai[bot]"}
                 "kimi" #{"verified-kimi-app[bot]"}})
(defn review [login state sha]
  {:id 1 :user {:login login :type "Bot"} :state state :commit_id sha
   :submitted_at "2026-10-03T01:00:00Z"})
(def baseline {:head head :approved-heads {"mimo" #{head}}
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

(deftest mandatory-override-remains-all-of
  (let [defaults {:review/required #{} :review/by-repo-name {"knoxx" #{"coderabbit" "codex"}}}]
    (is (= #{} (law/required-reviewers-for defaults "riatzukiza/.agents" nil)))
    (is (= #{"codex"} (law/required-reviewers-for defaults "riatzukiza/.agents" #{"codex"})))
    (is (= #{"coderabbit" "codex" "mimo"} (law/required-reviewers-for defaults "open-hax/knoxx" #{"mimo"}))))
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
    (is (= #{head} (get-in (law/review-evidence head [] [cr] identities) [:approved-heads "coderabbit"])))
    (is (= #{head} (get-in (law/review-evidence head [] [codex] identities) [:approved-heads "codex"])))
    (is (= #{head} (get-in (law/review-evidence head [passing] [] identities) [:approved-heads "mimo"])))
    (doseq [bad [(assoc cr :body marker)
                 (assoc cr :body (str "Full review triggered. " marker))
                 (assoc cr :body (str "No actionable comments were generated in the recent review. " marker))
                 (assoc-in cr [:user :type] "User")
                 (assoc codex :resolved-commit-id old-head)
                 (dissoc codex :resolved-commit-id)
                 (assoc codex :body "Codex Review: queued. No issues reported yet.")]]
      (is (empty? (:approved-heads (law/review-evidence head [] [bad] identities)))))
    (doseq [body ["No confirmed findings yet; review queued." "Confirmed findings: none.\nReview incomplete." "No issues found.\nUnreviewed files remain."]]
      (is (empty? (:approved-heads (law/review-evidence head [(assoc passing :body body)] [] identities)))))
    (is (empty? (:approved-heads (law/review-evidence old-head [passing] [cr codex] identities))))
    (let [revocation (assoc (review "coderabbitai[bot]" "CHANGES_REQUESTED" head)
                            :submitted_at "2026-10-03T01:03:00Z")]
      (is (empty? (:approved-heads (law/review-evidence head [revocation] [cr] identities)))))))

(deftest request-dedupe-cooldown-and-hard-budget
  (let [request {:trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->")}
        input {:head head :reviewer "coderabbit" :comments [request] :checks [] :rounds 1 :max-loops 6
               :now-ms 1790989260000 :identities identities}]
    (is (= :pending (:status (law/request-verdict input))))
    (is (= :request (:status (law/request-verdict (assoc input :head old-head)))))
    (is (= :request (:status (law/request-verdict (assoc input :comments [(assoc request :trusted? false)])))))
    (is (= :budget-exhausted (:status (law/request-verdict (assoc input :rounds 6 :comments [])))))
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

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests)
