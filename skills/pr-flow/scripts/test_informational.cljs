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
     :actionability-policy policy :actionability-observations [] :native-context s
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

(deftest unavailable-history-never-admits-or-emits-partial-observations
  (doseq [s captures]
    (let [t (evidence (input s)) accepted (first (:observations (actionability/disposition t)))]
      (doseq [history [nil]]
        (let [bad (assoc t :actionability-observations history)
              d (actionability/disposition bad)]
          (is (= :finding (:kind d)))
          (is (= :unavailable (:status d)))
          (is (empty? (:observations d)))
          (is (not (:pass? (gate (law/classify-thread bad)))))))
      (let [missing (dissoc t :actionability-observations)]
        (is (= :unavailable (:status (actionability/disposition missing))))
        (is (empty? (:observations (actionability/disposition missing)))))
      (doseq [history [[] [accepted]]]
        (let [healthy (assoc t :actionability-observations history)]
          (is (= :informational (:kind (actionability/disposition healthy))))
          (is (:pass? (gate (law/classify-thread healthy)))))))))

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

(defn persist-disposition [t]
  ;; Match collection: classify, append emitted observations, reclassify on read.
  (update t :actionability-observations (fnil into [])
          (get-in (law/classify-thread t) [:actionability :observations])))
(defn another-positive [t id time]
  (let [source (second (:issue-comments t))]
    (update t :issue-comments conj (native-comment id (:user source) time (:body source)))))
(defn qualified-pair [s]
  (-> (evidence (input s)) persist-disposition
      (another-positive 7004 "2026-10-03T14:02:00Z") persist-disposition))
(defn revoked-ids [t]
  (set (map :assessment-id (filter #(= :revoked (:status %)) (:actionability-observations t)))))

(deftest newer-positive-survives-persisted-and-repeated-collection
  (doseq [s captures]
    (let [initial (persist-disposition (evidence (input s)))
          newer (another-positive initial 7004 "2026-10-03T14:02:00Z")
          decision (:actionability (law/classify-thread newer))
          persisted (persist-disposition newer)]
      (is (= :informational (:kind decision)))
      (is (= [[7004 :qualified]] (mapv (juxt :assessment-id :status) (:observations decision))))
      (doseq [read (take 4 (iterate persist-disposition persisted))]
        (let [classified (law/classify-thread read)]
          (is (= :informational (get-in classified [:actionability :kind])))
          (is (= 7004 (get-in classified [:actionability :assessment-id])))
          (is (:pass? (gate classified)))
          (is (false? (:settled? classified)))
          (is (empty? (:observations (actionability/disposition read))))
          (is (empty? (revoked-ids read)))))
      (let [third (persist-disposition (another-positive persisted 7005 "2026-10-03T14:03:00Z"))]
        (is (:pass? (gate (law/classify-thread third))))
        (is (= #{7002 7004 7005} (set (map :assessment-id (:actionability-observations third)))))
        (is (empty? (:observations (actionability/disposition third))))))))

(deftest missing-positive-revokes-only-the-missing-source-and-cannot-be-restored
  (doseq [s captures id [7002 7004]]
    (let [pair (qualified-pair s)
          missing (update pair :issue-comments #(filterv (fn [c] (not= id (:id c))) %))
          persisted (persist-disposition missing)
          survivor (if (= id 7002) 7004 7002)
          restored (assoc pair :actionability-observations (:actionability-observations persisted))]
      (is (= #{id} (revoked-ids persisted)))
      (doseq [read (take 3 (iterate persist-disposition persisted))]
        (is (:pass? (gate (law/classify-thread read))))
        (is (= survivor (get-in (law/classify-thread read) [:actionability :assessment-id])))
        (is (empty? (:observations (actionability/disposition read)))))
      (is (not (:pass? (gate (law/classify-thread restored)))))
      (is (not (:pass? (gate (law/classify-thread (persist-disposition restored)))))))))

(deftest replacement-never-erases-real-edits-withdrawals-or-conflicts
  (doseq [s captures]
    (let [pair (qualified-pair s) bot (:user (second (:issue-comments pair)))
          changes (concat
                   (for [index [1 2]]
                     [(str "edited source " index)
                      (change-issue pair index #(assoc % :updated_at "2026-10-03T14:03:00Z"))])
                   (for [id [7002 7004] body [(str "I withdraw my agreement in issuecomment" id ".")
                                             (str "I withdraw my agreement in https://example.invalid/issuecomment-" id ".")]]
                     [(str "withdrawal " body)
                      (update pair :issue-comments conj (native-comment 7005 bot "2026-10-03T14:03:00Z" body))])
                   (for [id [7002 7004]]
                     [(str "protocol withdrawal " id)
                      (update pair :issue-comments conj
                              (assoc (withdrawal pair id) :created_at "2026-10-03T14:03:00Z"
                                     :updated_at "2026-10-03T14:03:00Z"))])
                   [["adverse assessment" (update pair :issue-comments conj
                                                 (-> (nth (:issue-comments pair) 2)
                                                     (assoc :id 7005 :node_id "IC_7005")
                                                     (change-payload #(assoc % 9 "finding")) hash-body))]
                    ["native thread edit" (refresh-context pair #(assoc-in % [:thread :comments :nodes 0 :updatedAt]
                                                                           "2026-10-03T14:03:00Z"))]
                    ["enclosing review edit" (refresh-context pair #(assoc-in % [:thread :comments :nodes 0 :pullRequestReview :updatedAt]
                                                                              "2026-10-03T14:03:00Z"))]])]
      (doseq [[label changed] changes]
        (let [persisted (persist-disposition changed)
              restored (assoc pair :actionability-observations (:actionability-observations persisted))]
          (is (not (:pass? (gate (law/classify-thread changed)))) label)
          (is (= #{7002 7004} (revoked-ids persisted)) label)
          (is (not (:pass? (gate (law/classify-thread persisted)))) label)
          (is (not (:pass? (gate (law/classify-thread restored)))) label))))))

(deftest new-proposal-requires-own-fresh-assessment-after-real-revocation
  (doseq [s captures]
    (let [pair (qualified-pair s)
          proposal (-> (first (:issue-comments pair))
                       (assoc :id 7006 :node_id "IC_7006" :html_url "https://example.invalid/issuecomment-7006"
                              :created_at "2026-10-03T14:04:00Z" :updated_at "2026-10-03T14:04:00Z"))
          waiting (persist-disposition (update pair :issue-comments conj proposal))
          source (-> (nth (:issue-comments pair) 2)
                     (change-payload #(assoc % 7 (:id proposal) 8 (:body-sha256 proposal))) hash-body)
          fresh (native-comment 7007 (:user source) "2026-10-03T14:05:00Z" (:body source))
          complete (persist-disposition (update waiting :issue-comments conj fresh))]
      (is (not (:pass? (gate (law/classify-thread waiting)))))
      (is (= #{7002 7004} (revoked-ids waiting)))
      (doseq [read (take 3 (iterate persist-disposition complete))]
        (is (:pass? (gate (law/classify-thread read))))
        (is (= 7007 (get-in (law/classify-thread read) [:actionability :assessment-id])))
        (is (= #{7002 7004} (revoked-ids read)))
        (is (empty? (:observations (actionability/disposition read))))))))

(deftest nil-issue-body-defaults-to-finding-without-hiding-malformed-native-headers
  (let [evaluate (fn [t] (try (actionability/disposition t) (catch :default _ ::threw)))]
    (doseq [comment [{:body nil} {}]]
      (let [result (evaluate {:issue-comments [comment] :actionability-observations []})]
        (is (= :finding (:kind result)))
        (is (= :absent (:status result)))
        (is (empty? (:observations result)))))
    (doseq [s captures]
      (let [t (persist-disposition (evidence (input s)))]
        (doseq [f [#(assoc % :body nil) #(dissoc % :body)]]
          (let [changed (update-in t [:issue-comments 1] f)
                result (evaluate changed)]
            (is (= :finding (:kind result)))
            (is (= [[7002 :revoked]] (mapv (juxt :assessment-id :status) (:observations result))))
            (when (= :finding (:kind result))
              (let [persisted (persist-disposition changed)
                    restored (assoc t :actionability-observations (:actionability-observations persisted))]
                (is (not (:pass? (gate (law/classify-thread persisted)))))
                (is (not (:pass? (gate (law/classify-thread restored)))))))))
        (let [source (second (:issue-comments t))
              malformed (native-comment 7008 (:user source) "2026-10-03T14:03:00Z"
                                        (str "Actionability assessment v1 for " (:head t) ":\nnot-a-canonical-vector"))
              changed (update t :issue-comments conj malformed)]
          (is (= :finding (:kind (evaluate changed))))
          (is (not (:pass? (gate (law/classify-thread changed))))))))))

(defn later-proposal [t body]
  (hash-body (assoc (first (:issue-comments t)) :id 8010 :node_id "IC_8010"
                   :html_url "https://example.invalid/issuecomment-8010"
                   :created_at "2026-10-03T14:03:00Z" :updated_at "2026-10-03T14:03:00Z" :body body)))

(deftest genuine-malformed-proposal-invalidates-an-older-pass
  (doseq [s captures]
    (let [t (persist-disposition (evidence (input s))) p (first (:issue-comments t))
          header (str "Actionability proposal v1 for " (:head t) ":")]
      (doseq [body [(str header "\n[") (str header "\n{}") header
                    (str header "\n<malformed>")
                    (str header "\n> " (pr-str (scoped t)))
                    (str header "\n```edn\n" (pr-str (scoped t)) "\n```")
                    (str header "\n~~~edn\n" (pr-str (scoped t)) "\n~~~")
                    (str header "\n<code>" (pr-str (scoped t)) "</code>")
                    (str (:body p) " ") (str (:body p) "\nExtra payload")
                    (str/replace (:body p) "v1 for" "v2 for")
                    (str (:body p) "\n\n[github run](/wrong/repo/actions/runs/1)")]]
        (let [changed (update t :issue-comments conj (later-proposal t body))
              d (actionability/disposition changed)
              persisted (persist-disposition changed)]
          (is (= 8010 (:proposal-id d)) body)
          (is (= :finding (:kind d)) body)
          (is (not (:pass? (gate (law/classify-thread changed)))) body)
          (is (= #{7002} (revoked-ids persisted)) body)
          (is (not (:pass? (gate (law/classify-thread persisted)))) body))))))

(deftest proposal-examples-spoofs-and-other-targets-do-not-invalidate-a-pass
  (doseq [s captures]
    (let [t (persist-disposition (evidence (input s))) p (first (:issue-comments t))
          malformed (later-proposal t (str "Actionability proposal v1 for " (:head t) ":\n["))
          good (later-proposal t (:body p))]
      (doseq [c [(assoc malformed :authorized? false)
                 (assoc-in malformed [:user :type] "Bot")
                 (update malformed :user dissoc :id)
                 (update malformed :user dissoc :node_id)
                 (assoc malformed :source-channel :github-review-comment)
                 (assoc malformed :created_at "2026-10-03T13:59:00Z" :updated_at "2026-10-03T13:59:00Z")
                 (hash-body (update malformed :body str/replace (:head t) (apply str (repeat 40 "b"))))
                 (later-proposal t (str "> " (:body p)))
                 (later-proposal t (str "```edn\n" (:body p) "\n```"))
                 (later-proposal t (str "<details>Generated example\n" (:body p) "\n</details>"))
                 (later-proposal t (str "Example only:\n" (:body p)))
                 (hash-body (change-payload good #(assoc % 1 "R_other")))
                 (hash-body (change-payload good #(assoc % 2 "PR_other")))
                 (hash-body (change-payload good #(assoc % 3 "PRRT_other")))
                 (hash-body (change-payload good #(update % 4 inc)))
                 (hash-body (update (change-payload good #(assoc % 3 "PRRT_other")) :body str " "))]]
        (let [changed (update t :issue-comments conj c)]
          (is (:pass? (gate (law/classify-thread changed))) (:body c))
          (is (= 7001 (:proposal-id (actionability/disposition changed))))
          (is (empty? (:observations (actionability/disposition changed)))))))))

(deftest malformed-proposal-recovery-requires-fresh-proposal-and-assessment-evidence
  (doseq [s captures]
    (let [t (persist-disposition (evidence (input s)))
          bad (later-proposal t (str "Actionability proposal v1 for " (:head t) ":\n["))
          invalidated (persist-disposition (update t :issue-comments conj bad))
          restored (assoc t :actionability-observations (:actionability-observations invalidated))
          p (-> (later-proposal t (:body (first (:issue-comments t))))
                (assoc :id 8011 :node_id "IC_8011" :html_url "https://example.invalid/issuecomment-8011"
                       :created_at "2026-10-03T14:04:00Z" :updated_at "2026-10-03T14:04:00Z"))
          waiting (update invalidated :issue-comments conj p)
          rebound (-> (second (:issue-comments t))
                      (change-payload #(assoc % 7 (:id p) 8 (:body-sha256 p))) hash-body)
          fresh (native-comment 8012 (:user rebound) "2026-10-03T14:05:00Z" (:body rebound))
          edited-p (hash-body (assoc bad :body (:body p) :updated_at "2026-10-03T14:04:00Z"))
          edited-a (native-comment 8013 (:user rebound) "2026-10-03T14:05:00Z"
                                   (:body (hash-body (change-payload rebound #(assoc % 7 (:id edited-p) 8 (:body-sha256 edited-p))))))]
      (is (= #{7002} (revoked-ids invalidated)))
      (is (not (:pass? (gate (law/classify-thread restored)))))
      (is (not (:pass? (gate (law/classify-thread waiting)))))
      (is (not (:pass? (gate (law/classify-thread
                               (assoc invalidated :issue-comments
                                      [(first (:issue-comments t))
                                       edited-p edited-a]))))))
      (let [reuse (update waiting :issue-comments
                          #(conj (filterv (fn [c] (not= 7002 (:id c))) %) (assoc fresh :id 7002)))
            complete (persist-disposition (update waiting :issue-comments conj fresh))]
        (is (not (:pass? (gate (law/classify-thread reuse)))))
        (doseq [read (take 3 (iterate persist-disposition complete))]
          (is (:pass? (gate (law/classify-thread read))))
          (is (= 8012 (get-in (law/classify-thread read) [:actionability :assessment-id])))
          (is (= #{7002} (revoked-ids read)))
          (is (empty? (:observations (actionability/disposition read)))))))))

(deftest malformed-proposal-edits-and-tied-update-times-never-reuse-an-old-pass
  (doseq [s captures]
    (let [t (persist-disposition (evidence (input s)))
          bad (later-proposal t (str "Actionability proposal v1 for " (:head t) ":\n["))]
      (doseq [c [(assoc bad :created_at "2026-10-03T13:59:00Z")
                 (assoc bad :created_at "2026-10-03T14:00:00Z" :updated_at "2026-10-03T14:00:00Z")]]
        (let [changed (update t :issue-comments conj c)]
          (is (not (:pass? (gate (law/classify-thread changed)))))
          (is (= #{7002} (revoked-ids (persist-disposition changed)))))))))


(def native-cjs-assessment
  (js->clj (js/JSON.parse
            (fs/readFileSync (path/join (path/dirname *file*) "fixtures/native-actionability-cjs.json") "utf8"))
           :keywordize-keys true))
(defn native-cjs-input []
  (let [{:keys [native_context proposal assessment writer_permission]} native-cjs-assessment]
    (assert (= "admin" (:permission writer_permission)))
    (assoc (input native_context) :issue-comments
           [(hash-body (assoc proposal :source-channel :github-issue-comment :authorized? true))
            (hash-body (assoc assessment :source-channel :github-issue-comment))])))

(deftest complete-native-cjs-evidence-is-an-informational-disposition-only
  (let [t (native-cjs-input) d (actionability/disposition t)
        a (second (:issue-comments t)) p (first (:issue-comments t))]
    (is (= "b7306bf00fcc7a888728c29e0aecf6c6bda2b7239fcd38922782eedbc0104b88" (:context-digest t)))
    (is (= 5993311862 (:id p)))
    (is (= 5993346659 (:id a)))
    (is (= :informational (:kind d)))
    (is (= :qualified (:status d)))
    (is (= 5993346659 (:assessment-id d)))
    (is (= :github-issue-comment (:channel d)))
    (is (= 1 (count (:observations d))))
    (is (empty? (:approved-heads (law/review-evidence (:head t) [] [a] law/default-reviewer-identities))))
    (is (empty? (law/completed-review-rounds [] [a] law/default-reviewer-identities)))))

(deftest javascript-source-evidence-retains-native-authority-and-freshness
  (let [t (native-cjs-input)]
    (doseq [extension ["js" "cjs" "mjs"]]
      (let [changed (change-issue t 1 #(change-payload % (fn [v] (assoc v 12 (str "Tests in src/assessment." extension " bind the native context.")))))]
        (is (= :qualified (:status (actionability/disposition changed))) extension)
        (doseq [f [#(assoc-in % [:issue-comments 1 :user :id] 41898282)
                   #(assoc-in % [:issue-comments 1 :user :node_id] "BOT_unknown")
                   #(assoc-in % [:issue-comments 0 :authorized?] false)
                   #(assoc-in % [:issue-comments 1 :updated_at] "2026-10-05T11:16:48Z")
                   #(assoc % :head (apply str (repeat 40 "a")))
                   #(assoc % :actionability-observations nil)
                   #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 9 "finding")))))
                   #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 11 "Agree.")))))
                   #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 12 "No evidence.")))))]]
          (is (not= :qualified (:status (actionability/disposition (f changed)))) extension))))))


(deftest canonical-details-boundary-is-public-and-mechanical-only
  (let [predicate #'actionability/details?
        reason "The complete native conversation supplies no request, question or defect."]
    (is (false? (boolean (:private (meta predicate)))))
    (doseq [evidence ["src/model.js" "test/model.cjs" "scripts/model.mjs"
                     "model.clj" "model.cljs" "model.cljc" "model.md" "model.edn"
                     "model.json" "model.yaml" "model.yml" "model.ts" "model.tsx"
                     "model.py" "model.sh" "https://example.invalid/source"]]
      (is (true? (predicate reason evidence)) evidence))
    (doseq [[r e] [[nil "model.cjs"] [123 "model.cjs"] ["Agree." "model.cjs"]
                   [reason nil] [reason 123] [reason ""] [reason "   "]
                   [reason "I agree with this judgment."]]]
      (is (false? (predicate r e))))))


;; Proxx-only MiMo admission source preparation. All protocol records below
;; are synthetic controls, not native assessments, agreements or activation.
(def mimo-test-policy
  (get-in (edn/read-string (fs/readFileSync
                            (path/join (path/dirname *file*) "../flow.edn") "utf8"))
          [:flow/defaults :review/actionability]))
(def mimo-test-user
  {:login "eta-mu-ai[bot]" :id 270021952 :node_id "BOT_kgDOEBg1QA" :type "Bot"})
(def mimo-test-source
  (first (filter #(= 1178288746 (get-in % [:repository :databaseId])) captures)))
(defn mimo-test-input
  ([] (mimo-test-input identity))
  ([f] (-> (evidence (input (f mimo-test-source)))
           (assoc :actionability-policy mimo-test-policy)
           (assoc-in [:issue-comments 1 :user] mimo-test-user))))
(defn mimo-test-decision [t] (:actionability (law/classify-thread t)))
(defn mimo-test-finding [t label]
  (is (= :finding (:kind (mimo-test-decision t))) label)
  (is (law/finding-obligation? t) label))

(deftest mimo-exact-native-tuple-is-separate-repository-policy
  (is (= #{{:login "opencode-agent[bot]" :id 219766164 :node-id "BOT_kgDODRldlA"}}
         (:identities mimo-test-policy)))
  (is (= {["open-hax/proxx" 1178288746]
          #{{:login "eta-mu-ai[bot]" :id 270021952 :node-id "BOT_kgDOEBg1QA"}}}
         (:repository-identities mimo-test-policy)))
  (is (false? (boolean (actionability/assessor-identity?
                        {:user mimo-test-user} mimo-test-policy))))
  (is (= "resolved-author-only-empty-reviews" actionability/scope)))

(deftest mimo-proxx-author-only-informational-supplies-zero-other-credit
  (let [t (mimo-test-input) c (law/classify-thread t) d (:actionability c)
        a (second (:issue-comments t)) head (:head t)]
    (is (= :informational (:kind d)))
    (is (= :qualified (:status d)))
    (is (= 7002 (:assessment-id d)))
    (is (= :github-issue-comment (:channel d)))
    (is (= 270021952 (get-in d [:observations 0 :assessment-user-id])))
    (is (= "BOT_kgDOEBg1QA" (get-in d [:observations 0 :assessment-user-node])))
    (is (false? (:settled? c)))
    (is (nil? (:resolution c)))
    (is (false? (:rejection-approved? c)))
    (is (empty? (:approved-heads (law/review-evidence head [] [a] law/default-reviewer-identities))))
    (is (empty? (:reviewed-heads (law/review-evidence head [] [a] law/default-reviewer-identities))))
    (is (empty? (law/completed-review-rounds [] [a] law/default-reviewer-identities)))
    (let [accepted (persist-disposition t)]
      (is (= :informational (:kind (mimo-test-decision accepted))))
      (is (empty? (:observations (mimo-test-decision accepted)))))))

(deftest mimo-foreign-repositories-ids-and-aliases-refuse-even-fresh-bindings
  ;; Rebuild manifest, proposal and assessment for each altered repository;
  ;; these refusals therefore exercise identity scope, not a stale digest.
  (doseq [[label f]
          [["foreign repository" #(assoc-in % [:repository :nameWithOwner] "other/proxx")]
           ["wrong native repository ID" #(assoc-in % [:repository :databaseId] 1178288747)]
           ["matching ID with foreign name" #(assoc-in % [:repository :nameWithOwner] "open-hax/uxx")]
           ["case alias" #(assoc-in % [:repository :nameWithOwner] "Open-Hax/proxx")]
           ["whitespace alias" #(assoc-in % [:repository :nameWithOwner] "open-hax/proxx ")]
           ["missing repository name" #(update % :repository dissoc :nameWithOwner)]
           ["missing repository ID" #(update % :repository dissoc :databaseId)]
           ["string repository ID" #(assoc-in % [:repository :databaseId] "1178288746")]]]
    (mimo-test-finding (mimo-test-input f) label))
  (let [uxx (second captures)]
    (mimo-test-finding (-> (evidence (input uxx))
                          (assoc :actionability-policy mimo-test-policy)
                          (assoc-in [:issue-comments 1 :user] mimo-test-user)) "actual captured foreign repository"))
  (doseq [s captures]
    (is (= :informational (:kind (mimo-test-decision
                                  (assoc (evidence (input s)) :actionability-policy mimo-test-policy))))
        "original global OpenCode admission retained")))

(deftest mimo-wrong-actor-and-policy-never-admit
  (let [t (mimo-test-input)]
    (doseq [[label f]
            [["wrong login" #(assoc-in % [:issue-comments 1 :user :login] "opencode-agent[bot]")]
             ["suffixless alias" #(assoc-in % [:issue-comments 1 :user :login] "eta-mu-ai")]
             ["case alias" #(assoc-in % [:issue-comments 1 :user :login] "Eta-Mu-Ai[bot]")]
             ["wrong numeric Bot ID" #(assoc-in % [:issue-comments 1 :user :id] 219766164)]
             ["wrong node ID" #(assoc-in % [:issue-comments 1 :user :node_id] "BOT_kgDODRldlA")]
             ["human actor" #(assoc-in % [:issue-comments 1 :user :type] "User")]
             ["generic actions actor" #(assoc-in % [:issue-comments 1 :user :login] "github-actions[bot]")]
             ["missing actor ID" #(update-in % [:issue-comments 1 :user] dissoc :id)]
             ["missing actor node" #(update-in % [:issue-comments 1 :user] dissoc :node_id)]
             ["missing policy" #(dissoc % :actionability-policy)]
             ["wrong policy version" #(assoc-in % [:actionability-policy :version] 2)]
             ["wrong policy status" #(assoc-in % [:actionability-policy :status] :accepted)]
             ["ordinary MiMo roster alone" #(assoc % :actionability-policy policy
                                                   :identities {"mimo" #{"eta-mu-ai[bot]"}})]
             ["wrong policy repository binding" #(assoc-in % [:actionability-policy :repository-identities]
                                                           {["open-hax/proxx" 1178288747]
                                                            #{{:login "eta-mu-ai[bot]" :id 270021952
                                                               :node-id "BOT_kgDOEBg1QA"}}})]]]
      (mimo-test-finding (f t) label))))

(deftest mimo-native-source-proposal-body-and-chronology-guards-remain
  (let [t (mimo-test-input)]
    (doseq [[label f]
            [["non-native review-body channel" #(assoc-in % [:issue-comments 1 :source-channel] :github-review)]
             ["missing source channel" #(update-in % [:issue-comments 1] dissoc :source-channel)]
             ["missing native source ID" #(update-in % [:issue-comments 1] dissoc :id)]
             ["missing source node" #(update-in % [:issue-comments 1] dissoc :node_id)]
             ["missing source URL" #(update-in % [:issue-comments 1] dissoc :html_url)]
             ["invalid source body hash" #(assoc-in % [:issue-comments 1 :body-sha256] "bad")]
             ["unauthorized proposal" #(assoc-in % [:issue-comments 0 :authorized?] false)]
             ["edited proposal" #(assoc-in % [:issue-comments 0 :updated_at] "2026-10-03T14:00:10Z")]
             ["edited/restored assessment" #(assoc-in % [:issue-comments 1 :updated_at] "2026-10-03T14:01:10Z")]
             ["assessment precedes proposal" #(update-in % [:issue-comments 1] assoc
                                                         :created_at "2026-10-03T13:59:00Z"
                                                         :updated_at "2026-10-03T13:59:00Z")]
             ["proposal ID mismatch" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 7 9001)))))]
             ["proposal hash mismatch" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 8 (apply str (repeat 64 "b")))))))]
             ["wrong head" #(assoc % :head (apply str (repeat 40 "a")))]
             ["wrong root" #(update % :root-comment-id inc)]
             ["wrong context digest" #(assoc % :context-digest (apply str (repeat 64 "b")))]
             ["wrong scope" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 6 "all-findings")))))]
             ["finding result" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 9 "finding")))))]
             ["uncertain result" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 9 "uncertain")))))]
             ["generic assent" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 11 "Agree.")))))]
             ["missing evidence" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (assoc v 12 "none")))))]
             ["quoted assessment" #(change-issue % 1 (fn [c] (update c :body (fn [b] (str "> " b)))))]
             ["duplicate payload" #(change-issue % 1 (fn [c] (change-payload c (fn [v] (conj v "informational")))))]
             ["duplicate native record" #(update % :issue-comments conj (second (:issue-comments %)))]]]
      (mimo-test-finding (f t) label))))

(deftest mimo-author-only-context-and-edits-cannot-be-expanded
  (let [t (mimo-test-input)]
    (doseq [[label f]
            [["unresolved" #(assoc-in % [:thread :isResolved] false)]
             ["incomplete comments" #(assoc-in % [:thread :comments :pageInfo :hasNextPage] true)]
             ["comment count mismatch" #(update-in % [:thread :comments :totalCount] inc)]
             ["changed native body" #(assoc-in % [:thread :comments :nodes 0 :body] "Please answer this defect report.")]
             ["changed diff hunk" #(assoc-in % [:thread :comments :nodes 0 :diffHunk] "@@ changed context @@")]
             ["native comment edit" #(assoc-in % [:thread :comments :nodes 0 :updatedAt] "2026-10-03T14:03:00Z")]
             ["enclosing review edit" #(assoc-in % [:thread :comments :nodes 0 :pullRequestReview :updatedAt]
                                                "2026-10-03T14:03:00Z")]
             ["Kimi-rooted thread" #(assoc-in % [:thread :comments :nodes 0 :author]
                                           {:id "BOT_kgDODRldlA" :databaseId 219766164
                                            :login "opencode-agent[bot]" :__typename "Bot"})]
             ["nonempty review body" #(assoc-in % [:thread :comments :nodes 0 :pullRequestReview :body] "P2: Fix this.")]
             ["wrong enclosing review state" #(assoc-in % [:thread :comments :nodes 0 :pullRequestReview :state] "APPROVED")]
             ["duplicate context comments" #(update-in % [:thread :comments :nodes] conj
                                                      (get-in % [:thread :comments :nodes 0]))]]]
      (mimo-test-finding (assoc (refresh-context t f) :actionability-policy mimo-test-policy) label))
    ;; Even fresh correctly rebound evidence cannot relax the author-only scope.
    (doseq [[label f]
            [["fresh Kimi root" #(assoc-in % [:thread :comments :nodes 0 :author]
                                          {:id "BOT_kgDODRldlA" :databaseId 219766164
                                           :login "opencode-agent[bot]" :__typename "Bot"})]
             ["fresh nonempty enclosing review" #(assoc-in % [:thread :comments :nodes 0 :pullRequestReview :body]
                                                          "Please fix this defect.")]]]
      (mimo-test-finding (mimo-test-input f) label))))

(deftest mimo-latest-malformed-or-edited-proposal-revokes-without-fallback
  (let [t (persist-disposition (mimo-test-input)) p (first (:issue-comments t))
        bad (later-proposal t (str "Actionability proposal v1 for " (:head t) ":\n["))]
    (doseq [later [bad (assoc bad :updated_at "2026-10-03T14:04:00Z")
                   (later-proposal t (str (:body p) "\nExtra payload"))
                   (later-proposal t (str/replace (:body p) "v1 for" "v2 for"))]]
      (let [changed (update t :issue-comments conj later)
            recorded (persist-disposition changed)]
        (mimo-test-finding changed "latest invalid proposal")
        (is (= #{7002} (revoked-ids recorded)))
        (mimo-test-finding (assoc t :actionability-observations (:actionability-observations recorded))
                           "restoring prior proposal cannot restore observed revoked source")))))

(deftest mimo-withdrawals-are-same-bot-exact-source-and-retained
  (let [t (persist-disposition (mimo-test-input)) bot mimo-test-user
        foreign {:login "opencode-agent[bot]" :id 219766164 :node_id "BOT_kgDODRldlA" :type "Bot"}]
    (doseq [c [(native-comment 7003 bot "2026-10-03T14:02:00Z" "I withdraw my agreement in issuecomment7002.")
               (native-comment 7003 bot "2026-10-03T14:02:00Z"
                               "I withdraw my agreement in https://example.invalid/issuecomment-7002.")
               (withdrawal t 7002)]]
      (let [changed (update t :issue-comments conj c) recorded (persist-disposition changed)]
        (mimo-test-finding changed "same MiMo source withdrawal")
        (is (= #{7002} (revoked-ids recorded)))
        (mimo-test-finding (assoc t :actionability-observations (:actionability-observations recorded))
                           "withdrawn source cannot be restored")))
    (doseq [c [(assoc (withdrawal t 7002) :user foreign)
               (native-comment 7003 foreign "2026-10-03T14:02:00Z" "I withdraw my agreement in issuecomment7002.")
               (native-comment 7003 (assoc bot :id 219766164) "2026-10-03T14:02:00Z"
                               "I withdraw my agreement in issuecomment7002.")
               (native-comment 7003 bot "2026-10-03T14:02:00Z" "> I withdraw my agreement in issuecomment7002.")
               (native-comment 7003 bot "2026-10-03T14:02:00Z" "I withdraw my agreement in issuecomment9999.")
               (withdrawal t 9999)]]
      (is (= :informational (:kind (mimo-test-decision (update t :issue-comments conj c))))
          "foreign/forged/quoted/wrong-source withdrawal cannot revoke MiMo"))))

(deftest mimo-conflicts-edits-deletion-and-policy-loss-retain-revocation
  (let [t (persist-disposition (mimo-test-input)) a (second (:issue-comments t))]
    (doseq [[label changed]
            [["edited source" (assoc-in t [:issue-comments 1 :updated_at] "2026-10-03T14:02:00Z")]
             ["deleted source" (update t :issue-comments pop)]
             ["lost scoped admission" (assoc t :actionability-policy policy)]
             ["adverse new assessment"
              (update t :issue-comments conj
                      (-> a (assoc :id 7004 :node_id "IC_7004")
                          (change-payload #(assoc % 9 "finding")) hash-body))]
             ["malformed admitted actor assessment"
              (update t :issue-comments conj
                      (native-comment 7004 mimo-test-user "2026-10-03T14:02:00Z"
                                      (str "Actionability assessment v1 for " (:head t) ":\n[")))]]]
      (let [recorded (persist-disposition changed)]
        (mimo-test-finding changed label)
        (is (= #{7002} (revoked-ids recorded)) label)
        (mimo-test-finding (assoc t :actionability-observations (:actionability-observations recorded))
                           "restored source cannot erase history")))))

(deftest mimo-unavailable-history-unresolved-and-required-failures-stay-blocking
  (let [t (mimo-test-input) c (law/classify-thread t)
        baseline {:threads [c] :head (:head t) :snapshot-head (:head t)
                  :approved-heads {"mimo" #{(:head t)}} :rounds 5
                  :review-participants #{"mimo"} :checks [{:name "laws" :state "SUCCESS"}]}]
    (mimo-test-finding (assoc t :actionability-observations nil) "unavailable history")
    (doseq [bad [(assoc baseline :approved-heads {}) (assoc baseline :rounds 0)
                 (assoc baseline :review-bodies-unanswered 1)
                 (assoc baseline :checks [{:name "laws" :required? true :state "FAILURE"}])
                 (assoc baseline :checks [{:name "laws" :required? true :state "CANCELLED"}])
                 (assoc baseline :checks [{:name "laws" :required? true :state "PENDING"}])
                 (assoc baseline :threads [(assoc c :resolved? false)])]]
      (is (not (:pass? (law/merge-gate bad))) "actionability cannot supply another gate's credit"))))
