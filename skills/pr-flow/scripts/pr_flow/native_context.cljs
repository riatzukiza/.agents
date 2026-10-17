;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.native-context
  (:require ["crypto" :as crypto]
            [clojure.string :as str]
            [pr-flow.actionability :as actionability]
            [pr-flow.positive-thread :as positive-thread]
            [pr-flow.law :as law]
            [pr-flow.native-context-law :as native-context-law]))

;; Native reads are supplied by the caller. Importing this namespace performs
;; no reads, CLI dispatch, credential lookup, classification or receipt append.

(defn- split-repo [repo]
  (let [[owner name] (str/split repo #"/")]
    (when-not (and owner name) (throw (ex-info "REPO must be owner/name" {:repo repo})))
    [owner name]))

(def ^:private threads-query
  "query($owner:String!,$name:String!,$n:Int!,$after:String){repository(owner:$owner,name:$name){id databaseId nameWithOwner pullRequest(number:$n){
     id number state isDraft headRefOid author{login __typename ... on User{id databaseId} ... on Bot{id databaseId}}
     reviewThreads(first:100,after:$after){pageInfo{hasNextPage endCursor}
       nodes{id isResolved isOutdated path line
         comments(first:100){totalCount pageInfo{hasNextPage} nodes{id databaseId author{login __typename ... on User{id databaseId} ... on Bot{id databaseId}}
           body diffHunk url createdAt updatedAt commit{oid} originalCommit{oid}
           pullRequestReview{id databaseId state body updatedAt commit{oid} author{login __typename ... on User{id databaseId} ... on Bot{id databaseId}}}}}}}}}}")

(defn- sha256 [s]
  (when (string? s) (.digest (.update (crypto/createHash "sha256") s "utf8") "hex")))
(defn- hash-body [c] (assoc c :body-sha256 (sha256 (:body c))))
(defn hydrate-actionability-context [repository pr thread]
  (let [context {:repository repository :pr (dissoc pr :reviewThreads :native-repository)
                 :thread (update-in thread [:comments :nodes]
                                    #(mapv (fn [c] (-> (hash-body c) (assoc :diff-sha256 (sha256 (:diffHunk c)))
                                                       (update :pullRequestReview hash-body))) %))}
        manifest (actionability/context-manifest context)]
    {:native-context context :context-manifest manifest :context-digest (sha256 (pr-str manifest))}))

(defn fetch-issue-comments [read-json repo n]
  (let [pages (read-json "api" (str "repos/" repo "/issues/" n "/comments") "--paginate" "--slurp")]
    (when-not (and (vector? pages) (seq pages) (every? vector? pages)
                   (every? #(and (map? %) (string? (:body %))) (mapcat identity pages)))
      (throw (ex-info "Invalid PR issue-comment page; cannot establish complete native evidence" {:repo repo :pr n})))
    (vec (mapcat identity pages))))

(defn hydrate-review-update
  "Read the latest native review body/edit without executing source or writes.
   Strict mode also binds complete User/Bot numeric/login identity for corpus
   input. The existing positive CLI retains its original Bot-only comparison."
  ([read-json review] (hydrate-review-update read-json review false))
  ([read-json review strict?]
   (let [id (:node_id review)
         response (when (and (string? id) (not (str/blank? id)))
                    (read-json "api" "graphql" "-f"
                               (if strict?
                                 "query=query($id:ID!){node(id:$id){... on PullRequestReview{id databaseId state body updatedAt submittedAt commit{oid} author{login __typename ... on User{id databaseId} ... on Bot{id databaseId}}}}}"
                                 "query=query($id:ID!){node(id:$id){... on PullRequestReview{id databaseId state body updatedAt submittedAt commit{oid} author{__typename ... on Bot{id}}}}}")
                               "-f" (str "id=" id)))
         native (get-in response [:data :node])
         matches? (and (not (seq (:errors response)))
                       (law/review-update-matches? review native (if strict? (get-in review [:user :type]) "Bot"))
                       (or (not strict?)
                           (and (integer? (get-in review [:user :id])) (pos? (get-in review [:user :id]))
                                (= (get-in review [:user :id]) (get-in native [:author :databaseId]))
                                (let [login (law/graphql-rest-login (:author native))]
                                  (and login (= (get-in review [:user :login]) login))))))]
     (cond-> (assoc review :updated_at (when matches? (:updatedAt native)))
       strict? (assoc :native-review native)
       (not matches?) (assoc :positive-review-update-error :native-source-mismatch)))))

(defn- collect-reviews [read-json repo n]
  (let [pages (read-json "api" (str "repos/" repo "/pulls/" n "/reviews") "--paginate" "--slurp")
        raw (when (and (vector? pages) (seq pages) (every? vector? pages)) (vec (mapcat identity pages)))
        ids (map :id raw) nodes (map :node_id raw)]
    (when-not (and raw (every? #(and (map? %) (integer? (:id %)) (pos? (:id %))
                                    (string? (:node_id %)) (not (str/blank? (:node_id %)))
                                    (string? (:body %))) raw)
                   (= (count raw) (count (set ids)) (count (set nodes))))
      (throw (ex-info "Incomplete or duplicate native review pages" {})))
    (let [reviews (mapv #(hydrate-review-update read-json % true) raw)]
      (when (some :positive-review-update-error reviews)
        (throw (ex-info "Native review body or edit changed during collection" {})))
      {:raw-reviews raw :reviews reviews :reviews-complete? true})))

(defn- thread-connection? [conn cursor-required?]
  (let [page (:pageInfo conn)]
    (and (map? conn) (vector? (:nodes conn)) (map? page)
         (boolean? (:hasNextPage page))
         (or (not cursor-required?) (not (:hasNextPage page))
             (and (string? (:endCursor page)) (not (str/blank? (:endCursor page))))))))

(defn- threads-page [read-json owner name n after]
  (let [args (cond-> ["api" "graphql" "-f" (str "query=" threads-query)
                      "-f" (str "owner=" owner) "-f" (str "name=" name) "-F" (str "n=" n)]
               after (into ["-f" (str "after=" after)]))
        response (apply read-json args)
        pr (get-in response [:data :repository :pullRequest])
        conn (:reviewThreads pr)]
    ;; gh normally exits nonzero on GraphQL errors, including HTTP 200. Also
    ;; validate decoded successes: null/partial data is never zero threads.
    (when-not (and (not (seq (:errors response)))
                   (map? pr) (boolean? (:isDraft pr))
                   (thread-connection? conn true)
                   (not (and (:hasNextPage (:pageInfo conn)) (= after (:endCursor (:pageInfo conn)))))
                   (every? #(thread-connection? (:comments %) false) (:nodes conn)))
      (throw (ex-info "Invalid GraphQL review-thread page; cannot establish complete thread evidence"
                      {:owner owner :repo name :pr n :after after})))
    (assoc pr :native-repository (dissoc (get-in response [:data :repository]) :pullRequest))))

(defn- require-readers [{:keys [read-json authorized-author? read-history]}]
  (when-not (every? fn? [read-json authorized-author? read-history])
    (throw (ex-info "Native context requires injected JSON, permission and history readers" {}))))

(defn- collect-context-base
  "Collect native conversation inputs through synchronous injected readers.
   The optional issue records must come from the same complete native snapshot.
   History remains separate: nil means unavailable and [] means healthy empty.
   This result carries no disposition, settlement or native mutation authority."
  ([deps repo n]
   (require-readers deps)
   (collect-context-base deps repo n (fetch-issue-comments (:read-json deps) repo n)))
  ([{:keys [read-json authorized-author? read-history] :as deps} repo n raw-issue-comments]
   (require-readers deps)
   (let [[owner name] (split-repo repo)
         authorized? (memoize (partial authorized-author? repo))
         issue-comments (mapv #(cond-> (assoc (hash-body %) :source-channel :github-issue-comment)
                                (= "User" (get-in % [:user :type]))
                                (assoc :authorized? (boolean (authorized? (get-in % [:user :login])))))
                              raw-issue-comments)]
     (loop [after nil acc [] first-snapshot nil]
       (let [pr (threads-page read-json owner name n after)
             repository (:native-repository pr)
             snapshot (native-context-law/page-snapshot repository pr)
             _ (when (and first-snapshot (not= first-snapshot snapshot))
                 (throw (ex-info "PR snapshot changed during review-thread pagination"
                                 {:repo repo :pr n :after after})))
             conn (:reviewThreads pr)
             acc (into acc (:nodes conn))]
         (if (get-in conn [:pageInfo :hasNextPage])
           (recur (get-in conn [:pageInfo :endCursor]) acc (or first-snapshot snapshot))
           (let [history (read-history (:id repository))]
             {:repository repository :pr (dissoc pr :reviewThreads :native-repository)
              :raw-threads acc :raw-issue-comments raw-issue-comments
              :issue-comments issue-comments :history history
              :draft? (:isDraft pr)
              :head (:headRefOid pr) :pr-author (get-in pr [:author :login])
              :incomplete? (boolean (some #(get-in % [:comments :pageInfo :hasNextPage]) acc))
              :threads (mapv (fn [t]
                               (merge (hydrate-actionability-context repository pr t)
                                      {:id (:id t) :resolved? (:isResolved t) :outdated? (:isOutdated t)
                                       :path (:path t) :line (:line t)
                                       :head (:headRefOid pr) :pr-author (get-in pr [:author :login])
                                       :root-comment-id (get-in t [:comments :nodes 0 :databaseId])
                                       :issue-comments issue-comments
                                       :comments (mapv (fn [c]
                                                         {:id (:databaseId c) :author (get-in c [:author :login]) :body (:body c)
                                                          :user {:login (get-in c [:author :login]) :type (get-in c [:author :__typename])}
                                                          :authorized? (boolean (and (not= "Bot" (get-in c [:author :__typename]))
                                                                                     (authorized? (get-in c [:author :login]))))
                                                          :url (:url c) :created-at (:createdAt c) :updated-at (:updatedAt c)})
                                                       (get-in t [:comments :nodes]))})) acc)})))))))

(defn- preservation-source [read-json repository-binding head file]
  (let [repo (:nameWithOwner repository-binding)]
    (when-not (and (positive-thread/source-path? file) (re-matches #"[0-9a-f]{40}" (or head "")))
      (throw (ex-info "Invalid preservation source coordinate" {})))
    (let [[owner name] (split-repo repo)
          response (read-json "api" "graphql" "-f"
                     "query=query($owner:String!,$name:String!,$expression:String!){repository(owner:$owner,name:$name){id databaseId nameWithOwner object(expression:$expression){... on Blob{oid byteSize isTruncated text}}}}"
                     "-f" (str "owner=" owner) "-f" (str "name=" name) "-f" (str "expression=" head ":" file))
          repository (get-in response [:data :repository]) b (:object repository) text (:text b)
          size (when (string? text) (js/Buffer.byteLength text "utf8"))
          oid (when size (.digest (-> (crypto/createHash "sha1")
                                      (.update (str "blob " size "\u0000") "utf8") (.update text "utf8")) "hex"))]
      (when-not (and (not (seq (:errors response)))
                     (= (select-keys repository-binding [:id :databaseId :nameWithOwner])
                        (select-keys repository [:id :databaseId :nameWithOwner]))
                     (false? (:isTruncated b)) (string? text) (= size (:byteSize b)) (= oid (:oid b)))
        (throw (ex-info "Complete immutable preservation source unavailable" {:path file :head head})))
      {:path file :head head :blob-oid oid :body-sha256 (sha256 text) :body text
       :repository (select-keys repository [:id :databaseId :nameWithOwner])})))

(defn hydrate-preservation-context
  "Acquire one complete source/corpus carrier for an explicit sorted cohort.
   Candidate member cuts manufacture no eligibility: pure law validates EVERY
   actual member append before any normalization can qualify. No code executes."
  ([deps snapshot t paths] (hydrate-preservation-context deps snapshot t paths nil))
  ([{:keys [read-json preservation-policy]} snapshot t paths requested-targets]
   (let [paths (or paths (positive-thread/proposal-source-paths t))
         targets (or requested-targets (positive-thread/proposal-cohort-targets t) [(positive-thread/member-target t)])
         members (mapv (fn [pair]
                         (let [matches (filterv #(= pair (positive-thread/member-target %)) (:threads snapshot))]
                           (when (= 1 (count matches)) (first matches)))) targets)
         cuts (set (keep (fn [member]
                          (let [nodes (get-in member [:native-context :thread :comments :nodes])]
                            (when (and (> (count nodes) 1) (positive-thread/settlement-attempt? (:body (last nodes))))
                              (positive-thread/member-target member)))) members))
         selected (fn [raw]
                    (if (contains? cuts [(:id raw) (get-in raw [:comments :nodes 0 :databaseId])])
                      (-> raw (assoc :isResolved false) (update-in [:comments :nodes] pop) (update-in [:comments :totalCount] dec)) raw))
         raw (mapv selected (:raw-threads snapshot))
         contexts (mapv #(hydrate-actionability-context (:repository snapshot) (:pr snapshot) %) raw)
         manifests (mapv :context-manifest contexts)
         corpus [2 [(get-in snapshot [:repository :id]) (get-in snapshot [:repository :databaseId]) (get-in snapshot [:repository :nameWithOwner])]
                 [(get-in snapshot [:pr :id]) (get-in snapshot [:pr :number]) (get-in snapshot [:pr :state])
                  (get-in snapshot [:pr :isDraft]) (get-in snapshot [:pr :headRefOid]) (actionability/author-row (get-in snapshot [:pr :author]))]
                 targets manifests]
         proposal (positive-thread/latest-proposal t) prefix-count (nth (:payload proposal) 5 nil)
         issues (:issue-comments snapshot)
         counts (set (filter #(and (integer? %) (<= 0 % (count issues))) [0 prefix-count (count issues)]))
         prefixes (into {} (map (fn [n] [n (sha256 (pr-str (mapv positive-thread/issue-row (subvec issues 0 n))))]) counts))
         inputs (mapv #(preservation-source read-json (:repository snapshot) (:head snapshot) %) paths)
         complete? (and (false? (:incomplete? snapshot)) (false? (:draft? snapshot))
                        (positive-thread/cohort-targets? targets) (every? map? members)
                        (= targets (mapv positive-thread/member-target members))
                        (= (count raw) (count (set (map :id raw))))
                        (= (count (mapcat #(get-in % [:comments :nodes]) raw))
                           (count (set (map :id (mapcat #(get-in % [:comments :nodes]) raw))))
                           (count (set (map :databaseId (mapcat #(get-in % [:comments :nodes]) raw)))))
                        (every? #(and (false? (get-in % [:comments :pageInfo :hasNextPage]))
                                      (= (count (get-in % [:comments :nodes])) (get-in % [:comments :totalCount]))) raw)
                        (every? #(= (:repository %) (:repository snapshot)) inputs))]
     (assoc t :preservation-policy preservation-policy :preservation-source-inputs (mapv #(dissoc % :repository) inputs)
            :preservation-custody {:version 2 :complete? (boolean complete?) :cohort-targets targets
                                   :cohort-members members :writer-cut-targets (vec (sort-by first cuts))
                                   :native-corpus (:raw-threads snapshot) :corpus-manifest corpus
                                   :conversation-digest (sha256 (pr-str corpus)) :issue-prefix-digests prefixes
                                   :source-updated-at (vec (mapcat (fn [c] [(:updatedAt c) (get-in c [:pullRequestReview :updatedAt])])
                                                                  (mapcat #(get-in % [:native-context :thread :comments :nodes]) contexts)))}))))

(defn collect-context
  "Existing native collection plus optional separately scoped preservation
   custody. Adding Bot IDs alone grants no eligibility. Unsupported repositories
   retain byte-for-byte normalized collection and perform no source reads."
  ([deps repo n]
   (require-readers deps)
   (collect-context deps repo n (fetch-issue-comments (:read-json deps) repo n)))
  ([deps repo n issue-comments]
   (let [snapshot (collect-context-base deps repo n issue-comments)
         snapshot (if (true? (:body-items? deps))
                    (let [reviews (collect-reviews (:read-json deps) repo n)
                          [owner name] (split-repo repo)
                          final (threads-page (:read-json deps) owner name n nil)
                          final-reviews (collect-reviews (:read-json deps) repo n)]
                      (when-not (and (= (:repository snapshot) (:native-repository final))
                                     (= (select-keys (:pr snapshot) [:id :number :state :isDraft :headRefOid])
                                        (select-keys final [:id :number :state :isDraft :headRefOid]))
                                     (= (:reviews reviews) (:reviews final-reviews)))
                        (throw (ex-info "Native snapshot changed during review-body collection" {})))
                      (merge snapshot reviews)) snapshot)
         profile (:preservation-policy deps) r (:repository snapshot)]
     (if (and (= 2 (:version profile)) (= :provisional (:status profile))
              (= [(:nameWithOwner r) (:databaseId r) (:id r)] (:repository profile)))
       (update snapshot :threads
               #(mapv (fn [t]
                        (try (hydrate-preservation-context deps snapshot t nil)
                             (catch :default e
                               ;; Incomplete member custody never grants admission. Retain
                               ;; only controlled causes and validated source coordinates.
                               (let [data (ex-data e)
                                     cause (case (ex-message e)
                                             "Invalid preservation source coordinate" :invalid-source-coordinate
                                             "Complete immutable preservation source unavailable" :immutable-source-unavailable
                                             :preservation-hydration-unavailable)
                                     coordinate (cond-> {}
                                                  (positive-thread/source-path? (:path data)) (assoc :path (:path data))
                                                  (and (string? (:head data)) (re-matches #"[0-9a-f]{40}" (:head data))) (assoc :head (:head data)))]
                                 (assoc t :preservation-policy profile :preservation-source-inputs []
                                          :preservation-custody {:version 2 :complete? false :error cause
                                                                 :source-error coordinate}))))) %))
       snapshot))))
