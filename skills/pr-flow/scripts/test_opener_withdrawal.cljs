(ns test-opener-withdrawal
  (:require ["fs" :as fs] ["path" :as path]
            [cljs.test :refer [deftest is]] [clojure.string :as str]
            [nbb.core :refer [*file*]] [pr-flow.law :as law]))

(def fixture
  (js->clj (js/JSON.parse (fs/readFileSync
                          (path/join (path/dirname *file*) "fixtures/native-sol3-opener-withdrawal.json") "utf8"))
           :keywordize-keys true))
(def head (get-in fixture [:pr :headRefOid]))
(def withdrawal-url "https://github.com/riatzukiza/sol/pull/3#discussion_r4176945609")
(def handled-body
  (str "Handled: Finding opener withdrew this finding on " head ".\n"
       "Finding: PRRT_kwDOU4VgTs6owkAD comment4176835303\n"
       "Withdrawal: " withdrawal-url "\n"
       "Reason: The actual canonical clone and consumed pin are publicly readable; original severity and history remain preserved."))
(defn normalize [c]
  {:id (:databaseId c) :author (get-in c [:author :login]) :body (:body c) :url (:url c)
   :user {:login (get-in c [:author :login]) :type (get-in c [:author :__typename])}
   :authorized? (= "riatzukiza" (get-in c [:author :login]))
   :created-at (:createdAt c) :updated-at (:updatedAt c)})
(defn input [raw]
  {:id (:id raw) :resolved? (:isResolved raw) :outdated? (:isOutdated raw)
   :head head :pr-author (get-in fixture [:pr :author :login])
   :root-comment-id (get-in raw [:comments :nodes 0 :databaseId])
   :native-context {:repository (:repository fixture) :pr (:pr fixture) :thread raw}
   :identities law/default-reviewer-identities :comments (mapv normalize (get-in raw [:comments :nodes]))})
(def actual (:thread fixture))
;; Local prospective settlement only. This comment was never posted to GitHub;
;; the fixture above retains the actual three-comment native observation.
(def synthetic-handled
  {:id "PRRC_local_fixture_only" :databaseId 990000001 :body handled-body
   :author (get-in fixture [:pr :author]) :url "https://github.com/riatzukiza/sol/pull/3#discussion_r990000001"
   :createdAt "2026-10-04T09:40:00Z" :updatedAt "2026-10-04T09:40:00Z"
   :pullRequestReview {:databaseId 990000002 :state "COMMENTED" :commit {:oid head}}})
(def prospective (-> actual (update-in [:comments :nodes] conj synthetic-handled)
                     (update-in [:comments :totalCount] inc)))
(defn cleared? [t]
  (let [result (law/classify-thread t)]
    (and (:opener-withdrawal result) (:settled? result)
         (empty? (law/unsettled-blockers [result])))))

(deftest genuine-native-withdrawal-needs-writer-settlement
  (let [result (law/classify-thread (input actual))]
    (is (= :p1 (:severity result)))
    (is (law/finding-obligation? result))
    (is (not (:settled? result)))
    (is (seq (law/unsettled-blockers [result])))))

(deftest opener-withdrawal-is-distinct-from-independent-rejection-and-approval
  (let [result (law/classify-thread (input prospective))]
    (is (= :p1 (:severity result)))
    (is (= :handled (:resolution result)))
    (is (law/finding-obligation? result))
    (is (cleared? (input prospective)))
    (is (= 4176945609 (get-in result [:opener-withdrawal :source-id])))
    (is (= withdrawal-url (get-in result [:opener-withdrawal :url])))
    (is (not (:rejection-approved? result)))
    (is (nil? (:rejection-reviewer result)))))

(deftest native-author-opener-head-root-and-context-cannot-be-substituted
  (doseq [t [(assoc (input prospective) :head (apply str (repeat 40 "b")))
             (assoc (input prospective) :root-comment-id 4176835304)
             (assoc (input prospective) :id "PRRT_other")
             (dissoc (input prospective) :native-context)
             (assoc-in (input prospective) [:comments 2 :body] "I withdraw this finding.")
             (assoc-in (input prospective) [:comments 3 :authorized?] false)]]
    (is (not (cleared? t))))
  (doseq [[label raw] [["author impersonation" (assoc-in prospective [:comments :nodes 2 :author] (get-in fixture [:pr :author]))]
                       ["different opener" (assoc-in prospective [:comments :nodes 2 :author :login] "eta-mu-ai")]
                       ["native User, not Bot" (assoc-in prospective [:comments :nodes 2 :author :__typename] "User")]
                       ["missing review binding" (update-in prospective [:comments :nodes 2] dissoc :pullRequestReview)]
                       ["old review head" (assoc-in prospective [:comments :nodes 2 :pullRequestReview :commit :oid] (apply str (repeat 40 "b")))]
                       ["old root head" (assoc-in prospective [:comments :nodes 0 :pullRequestReview :commit :oid] (apply str (repeat 40 "b")))]
                       ["cross-root URL" (assoc-in prospective [:comments :nodes 2 :url] "https://github.com/riatzukiza/sol/pull/4#discussion_r4176945609")]
                       ["partial conversation" (assoc-in prospective [:comments :pageInfo :hasNextPage] true)]
                       ["wrong total" (assoc-in prospective [:comments :totalCount] 3)]
                       ["unresolved" (assoc prospective :isResolved false)]
                       ["outdated" (assoc prospective :isOutdated true)]
                       ["opener self-settlement" (assoc-in prospective [:comments :nodes 3 :author] (get-in actual [:comments :nodes 0 :author]))]
                       ["missing writer type" (update-in prospective [:comments :nodes 3 :author] dissoc :__typename)]
                       ["unbound Handled" (assoc-in prospective [:comments :nodes 3 :body] "Handled: thanks.")]]]
    (is (not (cleared? (input raw))) label)))

(deftest quotes-generated-examples-quota-and-ambiguous-withdrawals-remain-blocking
  (let [body (get-in actual [:comments :nodes 2 :body])]
    (doseq [replacement [(str "> " (str/replace body "\n" "\n> "))
                         (str "```text\n" body "\n```")
                         (str "<details><summary>Generated example</summary>\n" body "\n</details>")
                         "I withdraw this finding."
                         "I might withdraw this finding after full review."
                         "Review limit reached. No review completed."
                         (str body "\nFinding: PRRT_other comment4176835304")
                         (str body "\nWithdrawal is pending.")
                         (str body "\nI do not withdraw this finding; it remains valid.")]]
      (is (not (cleared? (input (assoc-in prospective [:comments :nodes 2 :body] replacement))))))))

(deftest missing-reason-or-live-evidence-cannot-clear-a-major
  (let [body (get-in actual [:comments :nodes 2 :body])]
    (doseq [replacement [(str/replace body #"The finding incorrectly treated[^\n]+" "")
                         (str/replace body #"https?://[^\s)]+" "unavailable")]]
      (is (not (cleared? (input (assoc-in prospective [:comments :nodes 2 :body] replacement))))))))

(deftest later-pushback-edits-and-recovery-order-remain-blocking
  (let [later (assoc (get-in actual [:comments :nodes 2]) :id "PRRC_local_later" :databaseId 990000003
                     :url "https://github.com/riatzukiza/sol/pull/3#discussion_r990000003"
                     :createdAt "2026-10-04T09:41:00Z" :updatedAt "2026-10-04T09:41:00Z"
                     :body "The finding still reproduces. I retract my withdrawal.")]
    (is (not (cleared? (input (-> prospective (update-in [:comments :nodes] conj later)
                                 (update-in [:comments :totalCount] inc))))))
    (doseq [raw [(assoc-in prospective [:comments :nodes 0 :updatedAt] "2026-10-04T09:41:00Z")
                (assoc-in prospective [:comments :nodes 2 :updatedAt] "2026-10-04T09:41:00Z")
                (assoc-in prospective [:comments :nodes 3 :createdAt] "2026-10-04T09:31:00Z")
                (assoc-in prospective [:comments :nodes 2 :createdAt] "invalid")
                (assoc-in prospective [:comments :nodes 2 :updatedAt] "2026-10-04T09:30:00Z")
                (-> prospective (update-in [:comments :nodes] #(vec (concat (take 3 %) [later] (drop 3 %))))
                    (update-in [:comments :totalCount] inc))]]
      (is (not (cleared? (input raw)))))
    (is (not (cleared? (assoc (input prospective) :issue-comments
                             [{:id 990000004 :source-channel :github-issue-comment
                               :user {:login "coderabbitai[bot]" :type "Bot"}
                               :body "Finding: PRRT_kwDOU4VgTs6owkAD comment4176835303\nThis remains valid."
                               :created_at "2026-10-04T09:41:00Z" :updated_at "2026-10-04T09:41:00Z"}]))))))

(deftest original-major-history-and-other-gates-are-retained
  (let [t (law/classify-thread (input prospective))
        g {:head head :threads [t] :checks [{:name "laws" :state "SUCCESS" :required? true}]
           :approved-heads {"mimo" #{head}} :rounds 5 :review-participants #{"mimo"}}]
    (is (:pass? (law/merge-gate g)))
    (doseq [bad [(assoc g :approved-heads {})
                 (assoc g :rounds 0 :review-participants #{"mimo" "coderabbit"})
                 (assoc g :checks [{:name "laws" :state "FAILURE" :required? true}])
                 (assoc g :checks [{:name "laws" :state "SKIPPED" :required? true}])
                 (assoc g :checks [{:name "CodeRabbit" :state "SUCCESS" :description "rate limit" :required? true}])
                 (assoc g :required-reviewers #{"codex"})]]
      (is (not (:pass? (law/merge-gate bad)))))
    (doseq [raw [(update-in prospective [:comments :nodes] #(vec (concat (take 2 %) (drop 3 %))))
                (assoc-in prospective [:comments :nodes 2 :body] "✅ Review thread resolved.")]]
      (is (not (cleared? (input raw)))))))
