;; SPDX-License-Identifier: GPL-3.0-or-later
;; Laws of the any-approval merge gate (Err, 2026-10-29). One deftest per law.
(ns test-any-approval
  (:require [cljs.test :refer [deftest is testing run-tests]]
            [pr-flow.law :as law]))

(def h (apply str (repeat 40 "a")))
(def old (apply str (repeat 40 "b")))
(defn user [login] {:login login :type "User"})
(defn bot [login] {:login login :type "Bot"})
(defn review [u state commit & [at]] {:user u :state state :commit_id commit :submitted_at (or at "2026-10-29T12:00:00Z")})
(def maintainers {"err" "admin" "ops" "maintain" "dev" "write"})
(def green [{:name "laws" :state "SUCCESS" :required? true :headSha h}])
(def base {:head h :snapshot-head h :threads [] :checks green :maintainers maintainers
           :pr-author "agent-codex[bot]" :reviews []})
(defn gate [m] (law/merge-gate (merge base m)))
(defn ev [m] (merge {:schema law/human-approval-schema :head h :approver "err" :approver-type "User"
                     :source {:kind :issue-comment :id 42}} m))

(deftest L1-any-approval-counts
  (doseq [[who r] [["listed bot" nil]
                   ["Antigravity (evidence channel)" :antigravity]
                   ["any human" (review (user "someone") "APPROVED" h)]
                   ["Err" (review (user "err") "APPROVED" h)]]]
    (testing who
      (let [g (gate (cond (= :antigravity r) {:approved-heads {"antigravity" #{h}}}
                          r {:reviews [r]}
                          :else {:approved-heads {"mimo" #{h}}}))]
        (is (:pass? g) (pr-str (:reasons g))))))
  (is (not (:pass? (gate {}))) "zero approvals never pass")
  (is (not (:pass? (gate {:reviews [(review (bot "x[bot]") "APPROVED" h)]})))
      "raw Bot APPROVED counts only via trusted evidence (scope/revocation rules)"))

(deftest L2-approval-binds-to-current-head
  (is (not (:pass? (gate {:reviews [(review (user "err") "APPROVED" old)]}))))
  (is (not (:pass? (gate {:approved-heads {"mimo" #{old}}}))))
  (is (not (:pass? (gate {:reviews [(review (user "err") "APPROVED" h)] :head nil})))))

(deftest L3-any-change-request-blocks
  (doseq [u [(user "someone") (bot "coderabbitai[bot]") (user "err")]]
    (is (not (:pass? (gate {:approved-heads {"mimo" #{h}}
                            :reviews [(review u "CHANGES_REQUESTED" old)]})))
        "sticky across pushes, from anyone"))
  (testing "superseded only by the same reviewer's later approval or a dismissal"
    (is (:pass? (gate {:reviews [(review (user "x") "CHANGES_REQUESTED" old "2026-10-29T10:00:00Z")
                                 (review (user "x") "APPROVED" h "2026-10-29T11:00:00Z")]})))
    (is (:pass? (gate {:approved-heads {"mimo" #{h}}
                       :reviews [(review (user "x") "DISMISSED" old "2026-10-29T11:00:00Z")
                                 (review (user "x") "CHANGES_REQUESTED" old "2026-10-29T10:00:00Z")]})))
    (is (not (:pass? (gate {:reviews [(review (user "x") "CHANGES_REQUESTED" h "2026-10-29T10:00:00Z")
                                      (review (user "x") "COMMENTED" h "2026-10-29T11:00:00Z")
                                      (review (user "y") "APPROVED" h)]})))
        "a later COMMENTED review does not clear a change request")))

(deftest L4-any-unresolved-thread-blocks
  (is (not (:pass? (gate {:approved-heads {"mimo" #{h}} :threads [{:resolved? false :severity :info}]})))))

(deftest L5-failing-required-check-blocks-even-with-human-approval
  (let [g (gate {:reviews [(review (user "err") "APPROVED" h)]
                 :checks [{:name "laws" :state "FAILURE" :required? true :headSha h}]})]
    (is (not (:pass? g)))
    (is (:human-approved? g))))

(deftest L6-any-failing-check-needs-human-maintainer
  (let [failing (conj green {:name "lint" :state "FAILURE" :required? false :headSha h})]
    (is (not (:pass? (gate {:approved-heads {"mimo" #{h}} :checks failing}))) "bot approval alone")
    (is (not (:pass? (gate {:checks failing :reviews [(review (user "dev") "APPROVED" h)]}))) "write role is not maintainer")
    (is (not (:pass? (gate {:checks failing :reviews [(review (bot "x[bot]") "APPROVED" h)]
                            :maintainers {"x[bot]" "admin"}}))) "bots are never the human")
    (is (not (:pass? (gate {:checks failing :reviews [(review (user "err") "APPROVED" old)]}))) "older head")
    (is (:pass? (gate {:checks failing :reviews [(review (user "err") "APPROVED" h)]})))
    (is (:pass? (gate {:checks failing :reviews [(review (user "ops") "APPROVED" h)]})) "maintain role")
    (is (not (:pass? (gate {:checks failing :pr-author "err" :approved-heads {"mimo" #{h}}
                            :human-approval-evidence [(ev {})]}))) "approver may not be the PR author")))

(deftest L7-human-approval-evidence-is-verifiable
  (let [failing (conj green {:name "lint" :state "FAILURE" :required? false :headSha h})
        g #(gate {:checks failing :approved-heads {"mimo" #{h}} :human-approval-evidence [%]})]
    (is (:pass? (g (ev {}))))
    (doseq [[why bad] [["wrong head" (ev {:head old})] ["wrong schema" (ev {:schema "x"})]
                       ["bot approver" (ev {:approver-type "Bot"})] ["non-maintainer" (ev {:approver "dev"})]
                       ["unknown source" (ev {:source {:kind :slack :id 1}})] ["no source id" (ev {:source {:kind :issue-comment}})]]]
      (is (not (:pass? (g bad))) why))
    (testing "native comment marker parses to evidence"
      (let [e (law/human-approval-comment->evidence
               {:id 7 :user (user "err") :created_at "2026-10-29T12:00:00Z"
                :body (str "Override OK <!-- pr-flow-human-approval:v1 head=" h " -->")})]
        (is (:pass? (g e)))
        (is (nil? (law/human-approval-comment->evidence {:id 8 :user (user "err") :body "lgtm"})))))))

(def deferred-p3 {:id "t" :severity :p3 :resolution :deferred :settled? true :resolved? true
                  :comments [{:author "x" :body "P3"}]})

(deftest L8-no-round-limit-and-no-early-fix-obligation
  (doseq [rounds [nil 0 1 4 5 6 50]]
    (let [g (gate {:approved-heads {"mimo" #{h}} :rounds rounds
                   :review-participants #{"mimo" "codex"} :min-review-rounds 5})]
      (is (:pass? g) (pr-str (:reasons g)))
      (is (not (contains? g :loop-verdict)))))
  (is (= 0 (law/unanswered-review-count
            [{:id 5 :user (user "r") :state "COMMENTED" :submitted_at "2026-10-29T01:00:00Z"
              :body "<summary><em>🟡 Minor</em> · x · <code>x:1</code></summary><!-- cr-comment:v1:m1 -->"}]
            [{:user (user "w") :created_at "2026-10-29T01:01:00Z"
              :body "Handled: review-id:5\n- Deferred cr-comment:v1:m1: issue https://github.com/o/r/issues/1"}]
            {:rounds 0}))
      "a non-blocking body item may be deferred in round 0"))

(deftest L10-unrequested-reviewer-is-never-awaited
  ;; Err 2026-10-29: Codex auto-review is off; it runs only when requested.
  (doseq [checks [green
                  (conj green {:name "Codex" :state "PENDING" :headSha h})
                  (conj green {:name "Codex" :state "SKIPPED" :headSha h})]]
    (is (:pass? (gate {:approved-heads {"mimo" #{h}} :checks checks
                       :review-participants #{"mimo" "codex"}}))))
  (is (not (:pass? (gate {:approved-heads {"mimo" #{h}} :required-reviewers #{"codex"}})))
      "only an explicit request/override makes Codex mandatory"))

(deftest L9-gate-is-a-pure-fold
  (let [in {:reviews [(review (user "err") "APPROVED" h)]}]
    (is (= (gate in) (gate in)))
    (is (= (:pass? (gate in)) (:pass? (gate (update in :reviews reverse)))))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests)
