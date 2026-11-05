(ns test-walkthrough
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            ["fs" :as fs] ["path" :as path] ["crypto" :as crypto]
            [nbb.core :refer [*file*]]
            [pr-flow.actionability :as actionability]
            [pr-flow.law :as law]))

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

(def marker "<!-- pr-flow:walkthrough:v1; informational/no-finding/no-request/no-question -->\n")
(defn snapshot []
  (-> (first captures)
      (assoc-in [:thread :isResolved] false)
      (assoc-in [:thread :isOutdated] false)
      (update-in [:thread :comments :nodes]
                 (fn [nodes] [(update (first nodes) :body #(str marker %))]))
      (assoc-in [:thread :comments :totalCount] 1)
      (assoc-in [:thread :comments :pageInfo :hasNextPage] false)))
(defn target [t]
  [(get-in t [:native-context :repository :id]) (get-in t [:native-context :pr :id])
   (:id t) (:root-comment-id t)
   (get-in t [:native-context :thread :comments :nodes 0 :body-sha256])])
(defn attestation [t]
  (let [a (get-in t [:native-context :pr :author])
        user {:id (:databaseId a) :node_id (:id a) :login (:login a) :type "User"}
        body (str "Author walkthrough attestation v1 for " (:head t) ":\n"
                  (pr-str ["author-walkthrough/v1" [(target t)]
                           "informational/no-finding/no-request/no-question"]))]
    (native-comment 999999 user "2026-10-04T00:00:00Z" body)))
(defn qualified [s]
  (let [t (input s)] (assoc t :issue-comments [(attestation t)])))

(defn adverse-context [t]
  (let [a (get-in t [:native-context :pr :author])
        writer {:id (:databaseId a) :node_id (:id a) :login (:login a) :type "User"}
        bot {:id 219766164 :node_id "BOT_kgDODRldlA" :login "opencode-agent[bot]" :type "Bot"}
        binding (actionability/context-binding t)
        p (assoc (native-comment 80001 writer "2026-10-04T00:01:00Z"
                    (str "Actionability proposal v1 for " (:head t) ":\n" (pr-str binding)))
                 :authorized? true)
        v (into binding [(:id p) (:body-sha256 p) "finding"
                         "complete-context/no-defect/no-request/no-question"
                         "The reviewer found an actual problem in this context."
                         "skills/pr-flow/SKILL.md"])
        assessment (native-comment 80002 bot "2026-10-04T00:02:00Z"
                     (str "Actionability assessment v1 for " (:head t) ":\n" (pr-str v)))]
    (update t :issue-comments into [p assessment])))

(deftest explicit-native-attestation-and-conservative-controls
  (let [t (qualified (snapshot))
        untyped (update-in (snapshot) [:thread :comments :nodes 0 :body] #(str/replace % marker ""))
        second-comment (-> (snapshot)
                           (assoc-in [:thread :comments :totalCount] 2)
                           (update-in [:thread :comments :nodes]
                                      conj (get-in (first captures) [:thread :comments :nodes 1])))
        changed-root (-> (snapshot)
                         (update-in [:thread :comments :nodes 0 :body] str " Later change."))
        actual-request (-> (snapshot)
                           (assoc-in [:thread :comments :totalCount] 2)
                           (update-in [:thread :comments :nodes]
                                      conj (-> (get-in (first captures) [:thread :comments :nodes 1])
                                               (assoc :body "Please fix the missing validation."))))]
    (is (actionability/author-walkthrough? t) "exact native author attestation")
    (is (false? (law/finding-obligation? t)))
    (is (not (actionability/author-walkthrough? (qualified untyped))) "untyped root")
    (is (not (actionability/author-walkthrough? (qualified second-comment))) "any reply denies")
    (is (not (actionability/author-walkthrough? (qualified actual-request))) "actual request stays a finding")
    (is (law/finding-obligation? (qualified actual-request)))
    (is (not (actionability/author-walkthrough? (assoc t :issue-comments []))) "missing attestation")
    (is (law/finding-obligation? (assoc t :issue-comments [] :author-walkthrough? true))
        "caller-supplied classification cannot manufacture admission")
    (is (not (actionability/author-walkthrough? (assoc t :head (apply str (repeat 40 "f"))))) "stale head")
    (is (not (actionability/author-walkthrough? (assoc-in t [:issue-comments 0 :user :id] 123))) "wrong user")
    (is (not (actionability/author-walkthrough? (assoc-in t [:issue-comments 0 :updated_at] "2026-10-05T00:00:00Z"))) "edited attestation")
    (is (not (actionability/author-walkthrough?
              (assoc (input changed-root) :issue-comments (:issue-comments t))))
        "a later content change revokes the original attestation")
    (is (not (actionability/author-walkthrough?
              (assoc-in t [:issue-comments 0 :created_at] "2026-10-01T00:00:00Z")))
        "attestation must follow the native comment and review")
    (is (not (actionability/author-walkthrough? (update-in t [:native-context :thread :comments :nodes 0 :body] str " changed"))) "changed root")
    (is (not (actionability/author-walkthrough? (assoc-in t [:native-context :thread :comments :pageInfo :hasNextPage] true))) "incomplete context")
    (is (not (actionability/author-walkthrough? (assoc-in t [:native-context :thread :isOutdated] true))) "outdated")
    (is (actionability/author-walkthrough?
         (qualified (-> (snapshot)
                        (assoc-in [:thread :isOutdated] true)
                        (assoc-in [:thread :line] nil)
                        (assoc-in [:thread :comments :nodes 0 :commit :oid]
                                  "1111111111111111111111111111111111111111"))))
        "an outdated location needs a fresh current-head attestation")
    (let [historic (-> (snapshot)
                       (assoc-in [:thread :isOutdated] true)
                       (assoc-in [:thread :line] nil)
                       (assoc-in [:thread :comments :nodes 0 :commit :oid]
                                 "1111111111111111111111111111111111111111"))
          new-head (assoc-in historic [:pr :headRefOid] (apply str (repeat 40 "f")))
          old-attestation (:issue-comments (qualified historic))]
      (is (not (actionability/author-walkthrough?
                (qualified (assoc-in historic [:thread :comments :nodes 0 :commit :oid] "123"))))
          "a short historical commit is invalid")
      (is (not (actionability/author-walkthrough?
                (qualified (-> historic (assoc-in [:thread :isOutdated] false)
                               (assoc-in [:thread :line] 99)))))
          "a non-outdated location must bind the current commit")
      (is (not (actionability/author-walkthrough?
                (assoc (input new-head) :issue-comments old-attestation)))
          "a historical attestation cannot carry across a new head")
      (is (actionability/author-walkthrough? (qualified new-head))
          "fresh author attestation rebinds the new head"))
    (is (actionability/author-walkthrough?
         (assoc (input (assoc-in (snapshot) [:thread :isResolved] true))
                :issue-comments (:issue-comments t)))
        "GitHub resolution preserves an unchanged attestation")
    (is (not (actionability/author-walkthrough? (update-in t [:issue-comments 0 :body] #(str "> " %)))) "quoted attestation")
    (is (not (actionability/author-walkthrough? (assoc-in t [:issue-comments 0 :user :type] "Bot"))) "bot cannot attest")
    (is (not (law/author-walkthrough? (assoc t :actionability-observations nil)))
        "unavailable disposition history fails closed")
    (let [gate #(law/merge-gate {:threads % :checks [] :head (:head t) :rounds 5})]
      (is (not-any? #(str/includes? % "unresolved thread") (:reasons (gate [t]))))
      (is (some #(str/includes? % "unresolved thread") (:reasons (gate [t (qualified actual-request)])))))))

(deftest independent-adverse-evidence-overrides-author-attestation
  (let [s (assoc-in (snapshot) [:thread :comments :nodes 0 :pullRequestReview :body] "Author review")
        t (adverse-context (qualified s))
        resolved (assoc (input (assoc-in s [:thread :isResolved] true))
                        :issue-comments (:issue-comments t))
        positive (update-in t [:issue-comments 2]
                            #(hash-body (update % :body str/replace "\"finding\"" "\"informational\"")))
        revoked {:purpose :thread-actionability :status :revoked
                 :repo-id (get-in t [:native-context :repository :id])
                 :pr-id (get-in t [:native-context :pr :id]) :thread-id (:id t)
                 :assessment-id 80002}]
    (is (actionability/author-walkthrough? t) "the raw attestation alone is valid")
    (is (= :revoked (get-in (law/classify-thread t) [:actionability :status])))
    (is (not (law/author-walkthrough? t)))
    (is (law/finding-obligation? t))
    (is (some #(str/includes? % "unresolved thread")
              (:reasons (law/merge-gate {:threads [t] :checks [] :head (:head t) :rounds 5}))))
    (is (not (law/author-walkthrough? resolved))
        "resolution does not erase the earlier adverse scoped evidence")
    (is (law/finding-obligation? resolved))
    (is (= :qualified (get-in (law/classify-thread positive) [:actionability :status])))
    (is (not (law/author-walkthrough? positive))
        "even positive independent protocol overlap follows its own path")
    (is (not (law/author-walkthrough?
              (assoc (qualified s) :actionability-observations [revoked])))
        "retained revocation cannot be overridden by the author")
    (is (not (law/author-walkthrough?
              (assoc (qualified s) :actionability-observations [(dissoc revoked :purpose)])))
        "legacy retained revocation without a purpose also fails closed")
    (let [a (get-in t [:native-context :pr :author])
          writer {:id (:databaseId a) :node_id (:id a) :login (:login a) :type "User"}
          malformed (assoc (native-comment 80003 writer "2026-10-04T00:03:00Z"
                             (str "Actionability proposal v1 for " (:head t) ":\n[broken EDN"))
                           :authorized? true)
          partial (update (qualified s) :issue-comments conj malformed)]
      (is (= :ineligible (get-in (law/classify-thread partial) [:actionability :status])))
      (is (not (law/author-walkthrough? partial))
          "an unreadable live scoped proposal fails closed"))))
