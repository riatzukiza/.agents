(ns test-informational
  (:require [cljs.test :refer [deftest is]]
            ["fs" :as fs] ["path" :as path] ["crypto" :as crypto]
            [nbb.core :refer [*file*]]
            [clojure.string :as str] [clojure.edn :as edn]
            [pr-flow.actionability :as actionability] [pr-flow.law :as law]))

(def captures (:snapshots (js->clj (js/JSON.parse
                          (fs/readFileSync (path/join (path/dirname *file*) "fixtures/native-author-walkthroughs.json") "utf8"))
                          :keywordize-keys true)))
(def policy {:version 1 :status :provisional
             :identities #{{:login "opencode-agent[bot]" :id 219766164 :node-id "BOT_kgDODRldlA"}}})
(defn sha [s] (.digest (.update (crypto/createHash "sha256") s "utf8") "hex"))
(defn hash-body [c] (assoc c :body-sha256 (sha (:body c))))
(defn author-row [a] [(:id a) (:databaseId a) (:login a) (:__typename a)])
(defn manifest [s]
  (let [{:keys [repository pr thread]} s]
    [1 [(:id repository) (:databaseId repository) (:nameWithOwner repository)]
     [(:id pr) (:number pr) (:headRefOid pr) (author-row (:author pr))]
     [(:id thread) (:isResolved thread) (:isOutdated thread) (:path thread) (:line thread)]
     (mapv (fn [c]
             (let [r (:pullRequestReview c)]
               [(:id c) (:databaseId c) (:url c) (author-row (:author c))
                (:createdAt c) (:updatedAt c) (get-in c [:commit :oid]) (get-in c [:originalCommit :oid])
                (sha (:body c)) (sha (:diffHunk c))
                [(:id r) (:databaseId r) (:state r) (:updatedAt r) (author-row (:author r))
                 (get-in r [:commit :oid]) (sha (:body r))]])) (get-in thread [:comments :nodes]))]))
(defn input [s]
  (let [s (update-in s [:thread :comments :nodes]
                    #(mapv (fn [c] (-> (hash-body c) (assoc :diff-sha256 (sha (:diffHunk c)))
                                        (update :pullRequestReview hash-body))) %))
        nodes (get-in s [:thread :comments :nodes])]
    {:id (get-in s [:thread :id]) :head (get-in s [:pr :headRefOid])
     :pr-author (get-in s [:pr :author :login]) :resolved? (get-in s [:thread :isResolved])
     :root-comment-id (:databaseId (first nodes))
     :actionability-policy policy :native-context s
     :context-manifest (manifest s) :context-digest (sha (pr-str (manifest s)))
     :comments (mapv (fn [c] {:id (:databaseId c) :author (get-in c [:author :login])
                             :user {:login (get-in c [:author :login]) :type (get-in c [:author :__typename])}
                             :body (:body c) :url (:url c) :created-at (:createdAt c) :updated-at (:updatedAt c)
                             :authorized? true}) nodes)}))
(defn scoped [t] ["actionability/v1" (get-in t [:native-context :repository :id])
                  (get-in t [:native-context :pr :id]) (:id t) (:root-comment-id t)
                  (:context-digest t) "resolved-author-only-empty-reviews"])
(defn native-comment [id user time body]
  (hash-body {:id id :node_id (str "IC_fixture_" id) :user user :created_at time :updated_at time
              :html_url (str "https://example.invalid/issuecomment-" id) :body body :source-channel :github-issue-comment}))
(defn evidence [t]
  (let [author (get-in t [:native-context :pr :author])
        p (assoc (native-comment 7001 {:id (:databaseId author) :node_id (:id author) :login (:login author) :type "User"}
                          "2026-10-03T14:00:00Z" (str "Actionability proposal v1 for " (:head t) ":\n" (pr-str (scoped t))))
                 :authorized? true)
        a (native-comment 7002 {:id 219766164 :node_id "BOT_kgDODRldlA" :login "opencode-agent[bot]" :type "Bot"}
                   "2026-10-03T14:01:00Z"
                   (str "Actionability assessment v1 for " (:head t) ":\n"
                        (pr-str (into (scoped t) [(:id p) (:body-sha256 p) "informational"
                         "complete-context/no-defect/no-request/no-question"
                         "Both comments explain already recorded verification; neither requests work or an answer."
                         "https://example.invalid/inspected-context"]))))]
    (assoc t :issue-comments [p a])))
(defn gate [t]
  (law/merge-gate {:threads [t] :head (:head t) :snapshot-head (:head t)
                   :approved-heads {"mimo" #{(:head t)}} :rounds 5 :review-participants #{"mimo"}
                   :checks [{:name "laws" :state "SUCCESS"}]}))

(deftest current-native-walkthrough-needs-independent-evidence
  (doseq [s captures]
    (let [t (input s) without (law/classify-thread t) with (law/classify-thread (evidence t))]
      (is (not (:pass? (gate without))))
      (is (false? (:settled? with)))
      (is (= :informational (get-in with [:actionability :kind])))
      (is (:pass? (gate with))))))

(deftest candidate-flags-and-self-handled-never-supply-a-disposition
  (doseq [s captures]
    (is (not (:pass? (gate (assoc (law/classify-thread (input s))
                                 :actionability {:kind :informational :status :qualified})))))))

(defn change-issue [t index f]
  (update-in t [:issue-comments index] #(hash-body (f %))))
(defn change-payload [c f]
  (let [[header v] (str/split (:body c) #"\n")]
    (assoc c :body (str header "\n" (pr-str (f (edn/read-string v)))))))
(defn withdrawal [t source-id]
  (native-comment 7003 (:user (second (:issue-comments t))) "2026-10-03T14:02:00Z"
     (str "Actionability withdrawal v1 for " (:head t) ":\n"
          (pr-str (into (scoped t) [source-id (:body-sha256 (second (:issue-comments t))) "Independent judgment withdrawn."])))))
(defn refresh-context [t f]
  (merge (input (f (:native-context t))) (select-keys t [:issue-comments :actionability-observations])))

(deftest ordered-manifest-and-all-metadata-are-bound
  (doseq [s captures]
    (let [t (input s)]
      (is (= (:context-manifest t) (actionability/context-manifest (:native-context t))))
      (is (= (:context-digest t) (sha (pr-str (:context-manifest t)))))
      (is (= (vec (map :id (get-in s [:thread :comments :nodes])))
             (mapv first (last (:context-manifest t))))))))

(deftest native-authentication-scope-and-freshness-fail-closed
  (doseq [s captures]
    (let [t (evidence (input s))]
      (doseq [[name f]
              {"missing separate policy" #(dissoc % :actionability-policy)
               "rejection list is not admission" #(assoc % :actionability-policy {:identities #{}})
               "wrong repo" #(assoc-in % [:native-context :repository :id] "R_wrong")
               "wrong PR" #(assoc-in % [:native-context :pr :id] "PR_wrong")
               "wrong head" #(assoc % :head (apply str (repeat 40 "a")))
               "wrong root" #(update % :root-comment-id inc)
               "wrong thread" #(assoc % :id "PRRT_wrong")
               "missing native type" #(update-in % [:native-context :pr :author] dissoc :__typename)
               "missing enclosing review" #(update-in % [:native-context :thread :comments :nodes 0] dissoc :pullRequestReview)
               "nonempty enclosing review" #(assoc-in % [:native-context :thread :comments :nodes 0 :pullRequestReview :body] "Please fix a defect.")
               "incomplete comments" #(assoc-in % [:native-context :thread :comments :pageInfo :hasNextPage] true)
               "wrong comment count" #(assoc-in % [:native-context :thread :comments :totalCount] 3)
               "reordered comments" #(update-in % [:native-context :thread :comments :nodes] (comp vec reverse))
               "missing comment" #(update-in % [:native-context :thread :comments :nodes] pop)
               "digest mismatch" #(assoc % :context-digest (apply str (repeat 64 "a")))
               "spoof login" #(change-issue % 1 (fn [c] (assoc-in c [:user :login] "fake-opencode[bot]")))
               "spoof numeric ID" #(change-issue % 1 (fn [c] (update-in c [:user :id] inc)))
               "spoof node ID" #(change-issue % 1 (fn [c] (assoc-in c [:user :node_id] "BOT_wrong")))
               "human publisher" #(change-issue % 1 (fn [c] (assoc-in c [:user :type] "User")))
               "generic publisher" #(change-issue % 1 (fn [c] (assoc-in c [:user :login] "github-actions[bot]")))
               "missing source ID" #(change-issue % 1 (fn [c] (dissoc c :id)))
               "missing source channel" #(change-issue % 1 (fn [c] (dissoc c :source-channel)))
               "unauthorized writer" #(change-issue % 0 (fn [c] (assoc c :authorized? false)))
               "edited proposal" #(change-issue % 0 (fn [c] (assoc c :updated_at "2026-10-03T14:00:10Z")))
               "edited/restored assessment" #(change-issue % 1 (fn [c] (assoc c :updated_at "2026-10-03T14:01:10Z")))
               "assessment before proposal" #(change-issue % 1 (fn [c] (assoc c :created_at "2026-10-03T13:59:00Z" :updated_at "2026-10-03T13:59:00Z")))
               "reused other proposal" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 7 9001)))))
               "wrong proposal hash" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 8 (apply str (repeat 64 "b")))))))
               "stale proposal header" #(change-issue % 0 (fn [c] (update c :body str/replace (:head %) (apply str (repeat 40 "a")))))
               "stale assessment header" #(change-issue % 1 (fn [c] (update c :body str/replace (:head %) (apply str (repeat 40 "a")))))
               "assessment for another root" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (update v 4 inc)))))
               "finding assessment" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 9 "finding")))))
               "uncertain assessment" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 9 "uncertain")))))
               "generic assent" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 11 "Agree.")))))
               "missing evidence" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 12 "none")))))
               "unknown version" #(change-issue % 1 (fn [c] (update c :body str/replace "v1 for" "v2 for")))
               "quoted assessment" #(change-issue % 1 (fn [c] (update c :body (fn [b] (str "> " b)))))
               "fenced assessment" #(change-issue % 1 (fn [c] (update c :body (fn [b] (str "```\n" b "\n```")))))
               "generated assessment" #(change-issue % 1 (fn [c] (update c :body (fn [b] (str "<details>" b "</details>")))))
               "duplicate fields/extra payload" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (conj v "informational")))))
               "withdrawal" #(update % :issue-comments conj (withdrawal % 7002))}]
        (let [changed (law/classify-thread (f t))]
          (is (law/finding-obligation? changed) name)
          (is (not (:pass? (gate changed))) name))))))

(deftest genuine-defects-and-new-participation-are-obligations
  (doseq [s captures]
    (let [t (evidence (input s))]
      (doseq [author [(get-in s [:pr :author]) {:id "U_reviewer" :databaseId 20 :login "reviewer" :__typename "User"}
                     {:id "B_reviewer" :databaseId 21 :login "reviewer[bot]" :__typename "Bot"}]]
        (let [c (-> (get-in s [:thread :comments :nodes 0])
                    (assoc :id "PRRC_new" :databaseId 8000 :author author :body "P1: Please fix the unsafe credential boundary."
                           :createdAt "2026-10-03T14:03:00Z" :updatedAt "2026-10-03T14:03:00Z"))
              changed (refresh-context t #(-> % (update-in [:thread :comments :nodes] conj c)
                                                 (update-in [:thread :comments :totalCount] inc)))]
          (is (not (:pass? (gate (law/classify-thread changed)))))
          (is (not (:pass? (gate (law/classify-thread (dissoc changed :issue-comments))))))))
      (doseq [f [#(assoc-in % [:thread :comments :nodes 0 :body] "Please answer this unlabelled reviewer question.")
                 #(assoc-in % [:thread :comments :nodes 0 :updatedAt] "2026-10-03T14:03:00Z")
                 #(assoc-in % [:thread :comments :nodes 0 :pullRequestReview :updatedAt] "2026-10-03T14:03:00Z")]]
        (is (not (:pass? (gate (law/classify-thread (refresh-context t f))))))))))

(deftest adverse-conflicts-and-observed-revocations-cannot-be-restored
  (doseq [s captures]
    (let [t (evidence (input s))
          accepted (first (:observations (actionability/disposition t)))
          history (assoc t :actionability-observations [accepted])
          adverse (-> (second (:issue-comments t)) (assoc :id 7004 :node_id "IC_7004")
                      (change-payload #(assoc % 9 "finding")) hash-body)
          conflict (update history :issue-comments conj adverse)
          changed (change-issue history 1 #(assoc % :updated_at "2026-10-03T14:02:00Z"))
          revoked (first (:observations (actionability/disposition changed)))
          restored (assoc t :actionability-observations [accepted revoked])]
      (is (= :revoked (:status revoked)))
      (is (not (:pass? (gate (law/classify-thread conflict)))))
      (is (not (:pass? (gate (law/classify-thread restored)))))
      (is (empty? (:observations (actionability/disposition history))))
      (is (:pass? (gate (law/classify-thread (update t :issue-comments conj (withdrawal t 9999)))))))))

(deftest disposition-never-supplies-any-other-gate-credit
  (doseq [s captures]
    (let [t (law/classify-thread (evidence (input s))) head (:head t)
          baseline {:threads [t] :head head :snapshot-head head :approved-heads {"mimo" #{head}}
                    :rounds 5 :review-participants #{"mimo"} :checks [{:name "laws" :state "SUCCESS"}]}]
      (is (false? (:settled? t))) (is (nil? (:resolution t)))
      (is (false? (:rejection-approved? t)))
      (is (empty? (law/unsettled-blockers [(assoc t :severity :p1)])))
      (doseq [negative [(assoc baseline :approved-heads {}) (assoc baseline :rounds 0)
                        (assoc baseline :review-bodies-unanswered 1)
                        (assoc baseline :checks [{:name "laws" :required? true :state "FAILURE"}])
                        (assoc baseline :checks [{:name "laws" :required? true :state "CANCELLED"}])
                        (assoc baseline :threads [(assoc t :resolved? false)])
                        (update baseline :threads conj (law/classify-thread {:id "other" :resolved? true
                                                                            :comments [{:author "reviewer" :body "P1: real defect"}]}))]]
        (is (not (:pass? (law/merge-gate negative)))))
      (let [a (second (:issue-comments t))
            identities (assoc law/default-reviewer-identities "opencode" #{"opencode-agent[bot]"})
            e (law/review-evidence head [] [a] identities)]
        (is (empty? (:approved-heads e)))
        (is (empty? (:reviewed-heads e)))
        (is (empty? (law/completed-review-rounds [] [a] identities)))))))

(deftest native-source-id-and-url-withdrawals-reuse-the-existing-law
  (doseq [s captures]
    (let [t (evidence (input s)) bot (:user (second (:issue-comments t)))]
      (doseq [body ["I withdraw my agreement in issuecomment7002."
                   "I withdraw my agreement in https://example.invalid/issuecomment-7002."]]
        (is (not (:pass? (gate (law/classify-thread
                                (update t :issue-comments conj (native-comment 7003 bot "2026-10-03T14:02:00Z" body))))))))
      (doseq [body ["> I withdraw my agreement in issuecomment7002."
                   "```\nI withdraw my agreement in issuecomment7002.\n```"
                   "I withdraw my agreement in issuecomment9999."]]
        (is (:pass? (gate (law/classify-thread
                           (update t :issue-comments conj (native-comment 7003 bot "2026-10-03T14:02:00Z" body)))))))
      (is (:pass? (gate (law/classify-thread
                         (update t :issue-comments conj
                                 (native-comment 7003 (assoc bot :id 999) "2026-10-03T14:02:00Z"
                                                 "I withdraw my agreement in issuecomment7002.")))))))))

(deftest latest-proposal-and-ambiguous-assessment-cannot-reuse-an-old-pass
  (doseq [s captures]
    (let [t (evidence (input s)) p (first (:issue-comments t)) a (second (:issue-comments t))
          later (assoc p :id 7004 :node_id "IC_7004" :created_at "2026-10-03T14:02:00Z" :updated_at "2026-10-03T14:02:00Z")]
      (doseq [changed [(update t :issue-comments conj later)
                       (update t :issue-comments conj (assoc later :updated_at "2026-10-03T14:03:00Z"))
                       (change-issue t 0 #(assoc-in % [:user :id] 219766164))
                       (update t :issue-comments conj
                               (-> a (assoc :id 7005 :node_id "IC_7005")
                                   (update :body str/replace "v1 for" "v2 for") hash-body))]]
        (is (not (:pass? (gate (law/classify-thread changed)))))))))

(deftest actual-opencode-footer-format-is-transport-only
  (doseq [s captures]
    (let [t (evidence (input s)) repo (get-in s [:repository :nameWithOwner])
          native-footer (change-issue t 1 #(update % :body str "\n\n[github run](/" repo "/actions/runs/37120076384)"))]
      (is (:pass? (gate (law/classify-thread native-footer))))
      (is (false? (:settled? (law/classify-thread native-footer))))
      (doseq [suffix ["\n\nPlease also fix a real defect." "\n\n[github run](/wrong/repo/actions/runs/1)"
                      "\n\nActionability assessment v1 for unknown:" "\nExtra payload"]]
        (is (not (:pass? (gate (law/classify-thread (change-issue t 1 #(update % :body str suffix)))))))))))

(deftest other-finding-records-do-not-contaminate-a-qualified-thread
  (doseq [s captures]
    (let [t (evidence (input s)) other (-> (second (:issue-comments t))
                                         (assoc :id 8001 :node_id "IC_8001")
                                         (change-payload #(-> % (assoc 3 "PRRT_other" 9 "finding") (update 4 inc)))
                                         hash-body)]
      (is (:pass? (gate (law/classify-thread (update t :issue-comments conj other))))))))
