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
  (doseq [outdated? [false true]
          :let [prospective (assoc prospective :isOutdated outdated?)]]
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
                         ["unknown original root head" (assoc-in prospective [:comments :nodes 0 :pullRequestReview :commit :oid] nil)]
                         ["cross-root URL" (assoc-in prospective [:comments :nodes 2 :url] "https://github.com/riatzukiza/sol/pull/4#discussion_r4176945609")]
                         ["partial conversation" (assoc-in prospective [:comments :pageInfo :hasNextPage] true)]
                         ["wrong total" (assoc-in prospective [:comments :totalCount] 3)]
                         ["unresolved" (assoc prospective :isResolved false)]
                         ["unknown outdated flag" (dissoc prospective :isOutdated)]
                         ["opener self-settlement" (assoc-in prospective [:comments :nodes 3 :author] (get-in actual [:comments :nodes 0 :author]))]
                         ["missing writer type" (update-in prospective [:comments :nodes 3 :author] dissoc :__typename)]
                         ["unbound Handled" (assoc-in prospective [:comments :nodes 3 :body] "Handled: thanks.")]]]
      (is (not (cleared? (input raw))) label))))

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

(def eta-fixture
  (js->clj (js/JSON.parse (fs/readFileSync
                          (path/join (path/dirname *file*) "fixtures/native-eta15-old-head-opener-withdrawal.json") "utf8"))
           :keywordize-keys true))
(def eta-head (get-in eta-fixture [:pr :headRefOid]))
(def eta-root (get-in eta-fixture [:thread :comments :nodes 0]))
(def eta-thread-id (get-in eta-fixture [:thread :id]))
(def eta-withdrawal-url "https://github.com/riatzukiza/eta-mu/pull/15#discussion_r990010001")

;; The captured seventeen-comment conversation is never altered. These two
;; clearly synthetic successors model a fresh current-head recheck and writer;
;; neither exists on GitHub and neither supplies native disposition or approval.
(def eta-fresh-withdrawal
  {:id "PRRC_local_current_withdrawal_only" :databaseId 990010001
   :author (:author eta-root) :url eta-withdrawal-url
   :createdAt "2026-10-14T10:00:00Z" :updatedAt "2026-10-14T10:00:00Z"
   :body (str "I withdraw this finding.\n\n"
              "My finding incorrectly required a repository field despite the actual containing-repository validation contract.\n"
              "Evidence: https://github.com/riatzukiza/eta-mu/blob/" eta-head "/receipts.edn")
   :pullRequestReview {:id "PRR_local_current_withdrawal_only" :databaseId 990010002
                       :state "COMMENTED" :author (:author eta-root) :commit {:oid eta-head}}})
(def eta-fresh-writer
  {:id "PRRC_local_current_writer_only" :databaseId 990010003
   :author (get-in eta-fixture [:pr :author])
   :url "https://github.com/riatzukiza/eta-mu/pull/15#discussion_r990010003"
   :createdAt "2026-10-14T10:01:00Z" :updatedAt "2026-10-14T10:01:00Z"
   :body (str "Handled: The finding opener withdrew this finding on " eta-head ".\n"
              "Finding: " eta-thread-id " comment" (:databaseId eta-root) "\n"
              "Withdrawal: " eta-withdrawal-url "\n"
              "Reason: Preserve the immutable original review and historical rows; actual containing-repository validation supplies context.")
   :pullRequestReview {:id "PRR_local_current_writer_only" :databaseId 990010004
                       :state "COMMENTED" :commit {:oid eta-head}}})
(def eta-prospective
  (-> (:thread eta-fixture)
      (update-in [:comments :nodes] into [eta-fresh-withdrawal eta-fresh-writer])
      (update-in [:comments :totalCount] + 2)))
(defn eta-input [raw]
  {:id (:id raw) :resolved? (:isResolved raw) :outdated? (:isOutdated raw)
   :head eta-head :pr-author (get-in eta-fixture [:pr :author :login])
   :root-comment-id (get-in raw [:comments :nodes 0 :databaseId])
   :native-context {:repository (:repository eta-fixture) :pr (:pr eta-fixture) :thread raw}
   :identities law/default-reviewer-identities :comments (mapv normalize (get-in raw [:comments :nodes]))})

(deftest immutable-old-review-can-be-withdrawn-on-a-fresh-current-head
  (let [input (eta-input eta-prospective) result (law/classify-thread input)
        original-head (get-in eta-root [:pullRequestReview :commit :oid])]
    (is (not= original-head eta-head))
    (is (= eta-root (get-in eta-prospective [:comments :nodes 0])))
    (is (= (get-in eta-fixture [:thread :comments :nodes])
           (vec (take 17 (get-in eta-prospective [:comments :nodes])))))
    (is (cleared? input))
    (is (= :p1 (:severity result)))
    (is (law/finding-obligation? result))
    (is (not (:rejection-approved? result)))
    (is (= original-head (get-in result [:opener-withdrawal :original-head])))
    (is (= 5511615370 (get-in result [:opener-withdrawal :original-review-id])))
    (is (= eta-head (get-in result [:opener-withdrawal :head])))))

(deftest old-provenance-never-replaces-current-withdrawal-or-writer-custody
  (doseq [outdated? [false true]
          :let [eta-prospective (assoc eta-prospective :isOutdated outdated?)]]
    (let [wi 17 si 18 old-head (get-in eta-root [:pullRequestReview :commit :oid])]
      (doseq [[label raw]
              [["raw stale752 statements" (:thread eta-fixture)]
               ["no writer" (-> eta-prospective (update-in [:comments :nodes] pop)
                                 (update-in [:comments :totalCount] dec))]
               ["stale withdrawal" (assoc-in eta-prospective [:comments :nodes wi :pullRequestReview :commit :oid] old-head)]
               ["stale writer" (assoc-in eta-prospective [:comments :nodes si :pullRequestReview :commit :oid] old-head)]
               ["unknown original commit" (assoc-in eta-prospective [:comments :nodes 0 :pullRequestReview :commit :oid] nil)]
               ["malformed original commit" (assoc-in eta-prospective [:comments :nodes 0 :pullRequestReview :commit :oid] "unknown")]
               ["missing original review id" (update-in eta-prospective [:comments :nodes 0 :pullRequestReview] dissoc :databaseId)]
               ["invalid original review id" (assoc-in eta-prospective [:comments :nodes 0 :pullRequestReview :databaseId] 0)]
               ["missing original review node id" (update-in eta-prospective [:comments :nodes 0 :pullRequestReview] dissoc :id)]
               ["blank original review node id" (assoc-in eta-prospective [:comments :nodes 0 :pullRequestReview :id] " ")]
               ["unknown original review state" (assoc-in eta-prospective [:comments :nodes 0 :pullRequestReview :state] "UNKNOWN")]
               ["unpublished original review" (assoc-in eta-prospective [:comments :nodes 0 :pullRequestReview :state] "PENDING")]
               ["wrong original reviewer" (assoc-in eta-prospective [:comments :nodes 0 :pullRequestReview :author :login] "different")]
               ["wrong original reviewer type" (assoc-in eta-prospective [:comments :nodes 0 :pullRequestReview :author :__typename] "User")]
               ["different withdrawing opener" (assoc-in eta-prospective [:comments :nodes wi :author :login] "eta-mu-ai")]
               ["writer is Bot" (assoc-in eta-prospective [:comments :nodes si :author :__typename] "Bot")]
               ["wrong root binding" (assoc-in eta-prospective [:comments :nodes si :body]
                                              (str/replace (:body eta-fresh-writer) "comment4265471027" "comment4265471028"))]
               ["wrong withdrawal URL" (assoc-in eta-prospective [:comments :nodes wi :url]
                                                 "https://github.com/riatzukiza/eta-mu/pull/16#discussion_r990010001")]
               ["partial pagination" (assoc-in eta-prospective [:comments :pageInfo :hasNextPage] true)]
               ["wrong total" (update-in eta-prospective [:comments :totalCount] dec)]
               ["unresolved" (assoc eta-prospective :isResolved false)]
               ["unknown outdated flag" (dissoc eta-prospective :isOutdated)]
               ["later original edit" (assoc-in eta-prospective [:comments :nodes 0 :updatedAt] "2026-10-14T10:02:00Z")]
               ["late withdrawal edit" (assoc-in eta-prospective [:comments :nodes wi :updatedAt] "2026-10-14T10:02:00Z")]
               ["unknown chronology" (assoc-in eta-prospective [:comments :nodes wi :createdAt] "invalid")]]]
        (is (not (cleared? (eta-input raw))) label))
      (is (not (cleared? (assoc-in (eta-input eta-prospective) [:comments si :authorized?] false))))
      (doseq [[field value] [[:number 16] [:headRefOid old-head]]]
        (is (not (cleared? (assoc-in (eta-input eta-prospective) [:native-context :pr field] value)))))
      (is (not (cleared? (assoc-in (eta-input eta-prospective) [:native-context :repository :nameWithOwner] "other/eta-mu")))))))

(deftest old-review-withdrawal-supplies-no-other-merge-credit
  (let [t (law/classify-thread (eta-input eta-prospective))
        g {:head eta-head :threads [t]
           :checks [{:name "laws" :state "SUCCESS" :required? true}]
           :approved-heads {"mimo" #{eta-head}} :rounds 5 :review-participants #{"mimo"}}]
    (is (:pass? (law/merge-gate g)))
    (doseq [bad [(assoc g :approved-heads {})
                 (assoc g :approved-heads {"mimo" #{(get-in eta-root [:pullRequestReview :commit :oid])}})
                 (assoc g :rounds 0 :review-participants #{"mimo" "coderabbit"})
                 (assoc g :required-reviewers #{"codex"})
                 (assoc g :checks [{:name "laws" :state "FAILURE" :required? true}])
                 (assoc g :checks [{:name "laws" :state "SKIPPED" :required? true}])
                 (update g :threads conj (law/classify-thread
                                          {:id "other-finding" :resolved? false
                                           :comments [{:author "coderabbitai" :body "P1: independent defect"}]}))]]
      (is (not (:pass? (law/merge-gate bad)))))))

(deftest qualified-withdrawal-retains-known-location-and-original-history
  (doseq [[label original base to-input]
          [["current original review" actual prospective input]
           ["immutable older original review" (:thread eta-fixture) eta-prospective eta-input]]
          outdated? [false true]
          :let [raw (assoc base :isOutdated outdated?)
                t (to-input raw)
                result (law/classify-thread t)
                original-comments (get-in original [:comments :nodes])]]
    (is (cleared? t) (str label " / outdated=" outdated?))
    (is (= :p1 (:severity result)))
    (is (law/finding-obligation? result))
    (is (not (:rejection-approved? result)))
    (is (= (first original-comments) (get-in raw [:comments :nodes 0])))
    (is (= original-comments
           (vec (take (count original-comments) (get-in raw [:comments :nodes])))))))

(deftest location-agreement-does-not-replace-complete-current-custody
  (doseq [[label base to-input] [["current original review" prospective input]
                               ["immutable older original review" eta-prospective eta-input]]
          outdated? [false true]
          :let [raw (assoc base :isOutdated outdated?)
                t (to-input raw)
                wi (- (count (get-in raw [:comments :nodes])) 2)
                si (inc wi)
                withdrawal (get-in raw [:comments :nodes wi])
                same-time (:updatedAt withdrawal)]]
    (doseq [bad [(dissoc t :outdated?)
                 (assoc t :outdated? nil)
                 (assoc t :outdated? "true")
                 (assoc t :outdated? 1)
                 (assoc t :outdated? (not outdated?))
                 (update-in t [:native-context :thread] dissoc :isOutdated)
                 (assoc-in t [:native-context :thread :isOutdated] nil)
                 (assoc-in t [:native-context :thread :isOutdated] "true")
                 (assoc-in t [:native-context :thread :isOutdated] 1)
                 (assoc-in t [:native-context :thread :isOutdated] (not outdated?))
                 (assoc t :resolved? false)
                 (assoc-in t [:native-context :thread :isResolved] false)]]
      (is (not (cleared? bad)) (str label " / malformed or discordant native custody")))
    (doseq [[reason bad]
            [["equal writer chronology" (-> raw
                                             (assoc-in [:comments :nodes si :createdAt] same-time)
                                             (assoc-in [:comments :nodes si :updatedAt] same-time))]
             ["quoted withdrawal" (assoc-in raw [:comments :nodes wi :body]
                                            (str "> " (str/replace (:body withdrawal) "\n" "\n> ")))]
             ["retracted withdrawal" (assoc-in raw [:comments :nodes wi :body]
                                               (str (:body withdrawal) "\nI retract my withdrawal; the finding remains valid."))]]]
      (is (not (cleared? (to-input bad))) (str label " / " reason)))))
