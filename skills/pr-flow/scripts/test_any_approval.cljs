;; SPDX-License-Identifier: GPL-3.0-or-later
;; Laws of the any-approval merge gate (Err, 2026-10-29). One deftest per law.
(ns test-any-approval
  (:require [cljs.test :refer [deftest is testing run-tests]]
            [pr-flow.law :as law]
            [pr-flow.permissions :as permissions]))

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
                   ["verified writer" (review (user "dev") "APPROVED" h)]
                   ["Err" (review (user "err") "APPROVED" h)]]]
    (testing who
      (let [g (gate (cond r {:reviews [r]}
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
    (is (:pass? (gate {:maintainers {"x" "write"} :reviews [(review (user "x") "CHANGES_REQUESTED" old "2026-10-29T10:00:00Z")
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

(deftest waived-reviewers-cannot-hide-required-checks
  (doseq [state ["FAILURE" "PENDING" "SKIPPED" "CANCELLED"]]
    (let [row {:name "Kimi" :state state :headSha h}
          config {:approved-heads {"mimo" #{h}} :waived #{"kimi"}}]
      (is (:pass? (gate (assoc config :checks [row]))) "optional waiver remains effective")
      (is (not (:pass? (gate (assoc config :checks [(assoc row :required? true)]))))
          (str "required Kimi " state " remains blocking"))))
  (is (:pass? (gate {:approved-heads {"mimo" #{h}} :waived #{"kimi"}
                    :checks [{:name "Kimi" :state "SUCCESS" :headSha h :required? true}]}))))

(deftest equal-timestamp-standing-uses-native-review-id
  (doseq [[earlier later passes?] [["APPROVED" "CHANGES_REQUESTED" false]
                                  ["CHANGES_REQUESTED" "APPROVED" true]
                                  ["CHANGES_REQUESTED" "DISMISSED" true]]]
    (let [a (assoc (review (user "err") earlier h) :id 10)
          b (assoc (review (user "err") later h) :id 20)]
      (doseq [rs [[a b] [b a]]]
        (is (= passes? (:pass? (gate {:approved-heads {"mimo" #{h}} :reviews rs})))))))
  (let [a (review (user "err") "APPROVED" h)
        b (review (user "err") "CHANGES_REQUESTED" h)]
    (doseq [rs [[a b] [b a]]]
      (is (not (:pass? (gate {:approved-heads {"mimo" #{h}} :reviews rs})))
          "ambiguous ties without native IDs remain conservative"))))

(deftest ambiguous-approvals-cannot-establish-a-head
  (doseq [id [nil 10]]
    (let [a (assoc (review (user "err") "APPROVED" h) :id id)
          b (assoc (review (user "err") "APPROVED" old) :id id)]
      (doseq [rs [[a b] [b a]]]
        (is (not (:pass? (gate {:reviews rs}))))
        (is (empty? (law/current-head-approvers h rs))))))
  (let [r (assoc (review (user "err") "APPROVED" h) :id 10)]
    (is (:pass? (gate {:reviews [r r]}))) "identical duplicates remain valid"))

(deftest human-quorum-requires-verified-write-access
  (doseq [role [nil "read" "triage" "owner" true "WRITE"]]
    (let [g (gate {:reviews [(review (user "outsider") "APPROVED" h)]
                  :maintainers {"outsider" role}})]
      (is (not (:pass? g)))
      (is (empty? (:approving-reviewers g)))))
  (doseq [role ["write" "maintain" "admin"]]
    (is (:pass? (gate {:reviews [(review (user "writer") "APPROVED" h)]
                      :maintainers {"writer" role}}))))
  (is (not (:pass? (gate {:approved-heads {"antigravity" #{h}}})))
      "unverified artifact channel never supplies quorum"))

(deftest native-maintain-role-is-not-lost-to-base-permission
  (is (= "maintain" (permissions/observed-role {:permission "write" :role_name "maintain"})))
  (is (= "write" (permissions/observed-role {:permission "write" :role_name "custom-role"})))
  (doseq [response [nil {} {:permission "read"} {:role_name "custom-role" :permission "read"}]]
    (is (nil? (permissions/observed-role response)))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests)
