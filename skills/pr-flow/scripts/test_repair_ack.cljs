;; SPDX-License-Identifier: GPL-3.0-or-later
(ns test-repair-ack
  (:require [cljs.test :refer [deftest is]]
            ["fs" :as fs] ["path" :as path] [nbb.core :refer [*file*]] [clojure.string :as str]
            [pr-flow.law :as law]
            [pr-flow.native-context :as native]))

;; Entirely synthetic; no historical comment or deleted root is reconstructed.
(def head (apply str (repeat 40 "a")))
(def old-head (apply str (repeat 40 "b")))
(def author {:login "writer" :databaseId 1 :id "USER_1" :__typename "User"})
(def bot {:login "coderabbitai" :databaseId 2 :id "BOT_2" :__typename "Bot"})
(def repository {:nameWithOwner "owner/repo" :databaseId 3 :id "REPO_3"})
(def synthetic-pr {:id "PR_4" :number 4 :state "OPEN" :isDraft false :headRefOid head :author author})
(defn node [id a body time commit]
  {:id (str "COMMENT_" id) :databaseId id :author a :body body :createdAt time :updatedAt time
   :url (str "https://github.com/owner/repo/pull/4#discussion_r" id) :diffHunk "@@ source @@"
   :commit {:oid head} :originalCommit {:oid old-head}
   :pullRequestReview {:id (str "REVIEW_" id) :databaseId (+ id 100) :author a :state "COMMENTED"
                       :body "" :updatedAt time :commit {:oid commit}}})
(def repair (node 10 author "Fixed in af114f39: Repaired the collector and verified the regression with native custody tests."
                  "2026-10-23T10:00:00Z" old-head))
(def historical (node 11 bot "Verified the fix." "2026-10-23T10:01:00Z" old-head))
(defn normalized [c]
  {:id (:databaseId c) :author (get-in c [:author :login]) :body (:body c) :url (:url c)
   :created-at (:createdAt c) :updated-at (:updatedAt c)
   :authorized? (= author (:author c)) :user {:login (get-in c [:author :login]) :type (get-in c [:author :__typename])}})
(defn input [nodes]
  {:id "PRRT_fixture" :root-comment-id 10 :head head :pr-author "writer" :resolved? true :outdated? false
   :issue-comments [] :identities law/default-reviewer-identities
   :comments (mapv normalized nodes)
   :native-context {:repository repository :pr synthetic-pr
                    :thread {:id "PRRT_fixture" :isResolved true :isOutdated false :path "src.clj" :line 1
                             :comments {:nodes nodes :totalCount (count nodes) :pageInfo {:hasNextPage false}}}}})
(defn assessment [_prefix]
  (node 12 bot (str "Repair acknowledgement v1 for " head ":\n"
                   "Finding: PRRT_fixture comment10\n"
                   "Verified: complete-native-context/author-repair/no-unaddressed-finding\n"
                   "Reason: The surviving author statement records the verified repair and contains no new request.\n"
                   "Evidence: https://github.com/owner/repo/blob/" head "/src.clj")
        "2026-10-23T10:02:00Z" head))
(def prefix [repair historical])
(def fresh (assessment prefix))
(def qualified (input (conj prefix fresh)))
(defn admitted? [t] (boolean (:repair-acknowledgement (law/classify-thread t))))

(deftest bounded-independent-repair-proof-keeps-raw-history
  (is (admitted? qualified))
  (let [result (law/classify-thread qualified)]
    (is (= (:comments qualified) (:comments result)))
    (is (= (:native-context qualified) (:native-context result)))
    (is (= :fixed (:resolution result))) (is (:settled? result))
    (is (law/finding-obligation? result))
    (is (not (:rejection-approved? result)))
    (is (= 112 (get-in result [:repair-acknowledgement :review-id])))))

(deftest historical-ack-and-author-self-settlement-stay-denied
  (doseq [t [(input [repair]) (input prefix)
             (input [repair (assoc repair :id "other" :databaseId 13)])
             (assoc qualified :issue-comments nil)]]
    (is (not (admitted? t))) (is (not (:settled? (law/classify-thread t))))))

(deftest exact-scope-review-head-identity-and-custody-required
  (doseq [[label t]
          [["stale immutable enclosing review despite current comment commit" (assoc-in qualified [:native-context :thread :comments :nodes 2 :pullRequestReview :commit :oid] old-head)]
           ["missing native context" (dissoc qualified :native-context)]
           ["missing roster" (assoc qualified :identities {})]
           ["ambiguous physical provider" (assoc-in qualified [:identities "mimo"] #{"coderabbitai[bot]"})]
           ["normalized root mismatch" (assoc qualified :root-comment-id 9)]
           ["head changed" (assoc qualified :head old-head)]
           ["unresolved" (assoc qualified :resolved? false)]
           ["normalized flag disagreement" (assoc qualified :outdated? true)]
           ["writer unauthorized" (assoc-in qualified [:comments 0 :authorized?] false)]
           ["native body mismatch" (assoc-in qualified [:comments 0 :body] "Fixed: different")]
           ["partial pagination" (assoc-in qualified [:native-context :thread :comments :pageInfo :hasNextPage] true)]
           ["unknown resolution" (assoc-in qualified [:native-context :thread :isResolved] nil)]]]
    (is (not (admitted? t)) label)))

(defn replace-nodes [nodes] (input nodes))
(deftest native-edits-quotes-negatives-and-physical-collisions-refuse
  (doseq [[label change]
          [["stale immutable head" #(assoc-in % [:pullRequestReview :commit :oid] old-head)]
           ["pending review" #(assoc-in % [:pullRequestReview :state] "PENDING")]
           ["wrong review author" #(assoc-in % [:pullRequestReview :author] author)]
           ["Bot is User" #(assoc-in % [:author :__typename] "User")]
           ["unknown Bot" #(assoc-in % [:author :login] "unknown")]
           ["numeric identity collision" #(-> % (assoc-in [:author :databaseId] 1) (assoc-in [:pullRequestReview :author :databaseId] 1))]
           ["node identity collision" #(-> % (assoc-in [:author :id] "USER_1") (assoc-in [:pullRequestReview :author :id] "USER_1"))]
           ["edited assessment" #(assoc % :updatedAt "2026-10-23T10:03:00Z")]
           ["late enclosing review edit" #(assoc-in % [:pullRequestReview :updatedAt] "2026-10-23T10:03:00Z")]
           ["wrong thread" #(update % :body str/replace "PRRT_fixture" "PRRT_other")]
           ["wrong root" #(update % :body str/replace "comment10" "comment9")]
           ["quoted protocol" #(update % :body (fn [b] (str "> " (str/replace b "\n" "\n> "))))]
           ["fenced protocol" #(update % :body (fn [b] (str "```\n" b "\n```")))]
           ["generic confirmation" #(assoc % :body "Verified the fix.")]
           ["mixed negative" #(update % :body str/replace "Reason: " "Reason: This is not verified. ")]
           ["extra negative line" #(update % :body str "\nThis still reproduces.")]
           ["stale source link" #(update % :body str/replace (str "/blob/" head) (str "/blob/" old-head))]]]
    (is (not (admitted? (replace-nodes (conj prefix (change fresh))))) label)))

(deftest later-native-replies-issues-or-edits-revoke-without-erasing-history
  (let [later (node 13 bot "The finding still reproduces." "2026-10-23T10:03:00Z" head)]
    (is (not (admitted? (replace-nodes (conj prefix fresh later))))))
  (doseq [t [(assoc qualified :issue-comments [{:id 99 :body "Finding: PRRT_fixture comment10\nStill broken."
                                               :created_at "2026-10-23T10:03:00Z" :updated_at "2026-10-23T10:03:00Z"}])
             (replace-nodes [(assoc repair :updatedAt "2026-10-23T10:03:00Z") historical fresh])
             (replace-nodes [(assoc repair :body "P1: A genuine author finding still requires independent settlement.") historical fresh])]]
    (is (not (admitted? t)))))

(deftest no-approval-round-or-required-check-credit
  (let [t (law/classify-thread qualified)
        gate {:head head :threads [t] :rounds 5 :review-participants #{"mimo"}
              :approved-heads {"mimo" #{head}} :checks [{:name "law" :required? true :state "SUCCESS"}]}]
    (is (:pass? (law/merge-gate gate)))
    (doseq [bad [(assoc gate :approved-heads {}) (assoc gate :rounds 0)
                 (assoc gate :required-reviewers #{"codex"})
                 (assoc-in gate [:checks 0 :state] "FAILURE")
                 (update gate :threads conj (law/classify-thread {:id "other" :resolved? false :comments [{:author "writer" :body "P1: actual defect"}]}))]]
      (is (not (:pass? (law/merge-gate bad)))))))

(def capture
  (js->clj (js/JSON.parse (fs/readFileSync
                          (path/join (path/dirname *file*) "fixtures/native-foresight144-author-repair-ack.json") "utf8"))
           :keywordize-keys true))
(defn collect-native [raw issues]
  (let [r (:repository capture) p (:pr capture)]
    (native/collect-context
     {:read-json (fn [& args]
                   (if (= "graphql" (second args))
                     {:data {:repository (assoc r :pullRequest
                                               (assoc p :reviewThreads {:nodes [raw] :pageInfo {:hasNextPage false}}))}}
                     [issues]))
      :authorized-author? (fn [_ login] (= login (get-in p [:author :login])))
      :read-history (fn [_] [])}
     (:nameWithOwner r) (:number p))))
(defn native-assessment [raw]
  (let [h (get-in capture [:pr :headRefOid]) id (:id raw) root (get-in raw [:comments :nodes 0 :databaseId])
        old (last (get-in raw [:comments :nodes])) time "2026-10-24T11:00:00Z"]
    (-> old
        (assoc :id "PRRC_synthetic_fresh" :databaseId 990040001 :createdAt time :updatedAt time
               :url "https://github.com/open-hax/foresight/pull/144#discussion_r990040001"
               :body (str "Repair acknowledgement v1 for " h ":\n"
                          "Finding: " id " comment" root "\n"
                          "Verified: complete-native-context/author-repair/no-unaddressed-finding\n"
                          "Reason: The native author statement describes the independently verified HAC repair with no new finding.\n"
                          "Evidence: https://github.com/open-hax/foresight/blob/" h "/" (:path raw)))
        (assoc-in [:pullRequestReview :id] "PRR_synthetic_fresh")
        (assoc-in [:pullRequestReview :databaseId] 990040002)
        (assoc-in [:pullRequestReview :commit :oid] h)
        (assoc-in [:pullRequestReview :updatedAt] time))))
(defn wrapped [body historical]
  (str "<!-- coderabbit-ai-generated:v1 -->\n\n" body
       (subs historical (str/index-of historical "\n\n---\n\n<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->"))))

(deftest native-prospective-append-resolution-readback-through-existing-collector
  (doseq [raw (:threads capture)]
    (let [historical (:body (last (get-in raw [:comments :nodes])))
          fresh (native-assessment raw)
          fresh (update fresh :body wrapped historical)
          before (assoc raw :isResolved false)
          appended (-> before (update-in [:comments :nodes] conj fresh) (update-in [:comments :totalCount] inc))
          readback (assoc appended :isResolved true)
          classify (fn [r issues] (law/classify-thread (first (:threads (collect-native r issues)))))
          result (classify readback [])]
      ;; Real native Fixed in <short SHA> roots and wrappers stay byte-faithful.
      (is (str/starts-with? (get-in raw [:comments :nodes 0 :body]) "Fixed in af114f39: "))
      (is (not (:repair-acknowledgement (classify raw []))))
      (is (not (:settled? (classify before []))))
      (is (not (:repair-acknowledgement (classify appended []))))
      (is (:repair-acknowledgement result)) (is (:settled? result)) (is (= :fixed (:resolution result)))
      (is (= (get-in raw [:comments :nodes]) (mapv #(-> % (dissoc :body-sha256 :diff-sha256) (update :pullRequestReview dissoc :body-sha256))
                      (butlast (get-in result [:native-context :thread :comments :nodes])))))
      (is (:repair-acknowledgement (classify readback [{:body "Unrelated PR review request"
                                                      :created_at "2026-10-24T11:01:00Z" :updated_at "2026-10-24T11:01:00Z"}])))
      (is (not (:repair-acknowledgement
                (classify readback [{:body (str "Finding: " (:id raw) " comment" (get-in raw [:comments :nodes 0 :databaseId]) "\nThe finding still reproduces.")
                                     :created_at "2026-10-24T11:01:00Z" :updated_at "2026-10-24T11:01:00Z"}]))))
      (is (not (:repair-acknowledgement
                (classify (assoc-in readback [:comments :nodes 2 :pullRequestReview :commit :oid]
                                    (get-in raw [:comments :nodes 1 :pullRequestReview :commit :oid])) []))))
      (doseq [body [(str "> " (:body fresh))
                   (str "```text\n" (:body fresh) "\n```")
                   (str "<!-- arbitrary -->\n" (:body fresh))
                   (str/replace (:body fresh) "<!-- coderabbit-ai-generated:v1 -->\n\n"
                                "<!-- coderabbit-ai-generated:v1 -->\n\n<details>Example</details>\n")
                   (str/replace (:body fresh) "\n\n---\n\n<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->"
                                "\nThis still reproduces.\n\n---\n\n<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->")]]
        (is (not (:repair-acknowledgement (classify (assoc-in readback [:comments :nodes 2 :body] body) []))))))))

(deftest contradictory-reason-and-empty-or-invalid-source-path-refuse
  (doseq [reason ["I found a security vulnerability in this repair; please fix it"
                  "An outstanding defect remains in the implementation and requires correction"
                  "The implementation might preserve the intended behavior but remains uncertain"]]
    (let [bad (update fresh :body str/replace
                      #"Reason: [^\n]+"
                      (str "Reason: " reason))]
      (is (not (admitted? (replace-nodes (conj prefix bad)))))))
  (doseq [path ["" "/" "../src.clj" "dir/../src.clj" "src.clj?raw=1" "src.clj#L1" "dir//src.clj"]]
    (let [bad (update fresh :body str/replace (str "/blob/" head "/src.clj")
                      (str "/blob/" head "/" path))]
      (is (not (admitted? (replace-nodes (conj prefix bad))))))))

(def envelope-capture
  (js->clj (js/JSON.parse (fs/readFileSync
                         (path/join (path/dirname *file*) "fixtures/native-foresight144-repair-envelope.json") "utf8"))
           :keywordize-keys true))
(defn fixture-body [c]
  ;; Raw native carrier retained; only explicit synthetic binding coordinates
  ;; are substituted for this lexical regression. This is no operational proof.
  (-> (:body c)
      (str/replace "186ad1468de761330ce3528146a21ad572265d00" head)
      (str/replace "PRRT_kwDOT3TWPM6vcq2j comment4355453041" "PRRT_fixture comment10")
      (str/replace "PRRT_kwDOT3TWPM6vcq20 comment4355453881" "PRRT_fixture comment10")
      (str/replace "`@riatzukiza`" "`@writer`")
      (str/replace "https://github.com/open-hax/foresight/blob/" "https://github.com/owner/repo/blob/")))

(deftest observed-native-shell-carrier-and-author-mention
  (doseq [[_ c] envelope-capture]
    (let [body (fixture-body c)
          check (fn [b] (admitted? (input (conj prefix (assoc fresh :body b)))))]
      ;; Actual reasons retain requires and conservatively refuse. Separate
      ;; synthetic affirmative prose isolates the native transport regression.
      (is (not (check body)))
      (let [affirmative (str/replace body #"Reason: [^\n]+"
                                    "Reason: Independently verified the complete source and surviving repair statement against the current planning criteria.")]
        (is (check affirmative))
      (doseq [bad [(str/replace affirmative "🧩 Analysis chain" "Example")
                   (str/replace affirmative "```shell" "```text")
                   (str/replace affirmative "`@writer`" "`@someone-else`")
                   (str/replace affirmative "</details>\n\n" "</details>\n\nStill broken.\n")
                   (str/replace affirmative "🏁 Script executed:" "Repair acknowledgement v1 for fake:")
                   (str/replace affirmative "Reason: " "Reason: Please fix this. ")
                   (str/replace affirmative "Reason: " "Reason: A defect requires correction. ")
                   (str/replace affirmative "Reason: " "Reason: This remains uncertain. ")
                   (str/replace affirmative "Reason: " "Reason: This is not verified. ")
                   (str/replace affirmative "Reason: " "Reason: Fixing this remains necessary. ")
                   (str/replace affirmative "Reason: " "Reason: Additional authorization is required before acceptance. ")
                   (str/replace affirmative "Reason: " "Reason: The delegation boundary is to be corrected before acceptance. ")
                   (str/replace affirmative "Evidence: " "Extra live line\nEvidence: ")]]
        (is (not (check bad))))))))

(deftest source-description-does-not-admit-corrective-requires
  (doseq [reason ["Additional authorization is required before acceptance."
                  "The delegation boundary is to be corrected before acceptance."
                  "The exact-head design requires additional authorization checks before this repair is complete."
                  "Exact-head Line 74 requires removal of the authorization bypass before acceptance."
                  "The exact-head design requires fixing the unsafe actor binding"
                  "Exact-head Line 74 requires correction of the producer defect"
                  "The author requires source review before this can be verified"]]
    (is (not (admitted? (input (conj prefix (update fresh :body str/replace #"Reason: [^\n]+" (str "Reason: " reason)))))))))
