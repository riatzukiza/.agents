;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.native-context
  (:require ["crypto" :as crypto]
            [clojure.string :as str]
            [pr-flow.actionability :as actionability]))

;; Native reads are supplied by the caller. Importing this namespace performs
;; no reads, CLI dispatch, credential lookup, classification or receipt append.

(defn- split-repo [repo]
  (let [[owner name] (str/split repo #"/")]
    (when-not (and owner name) (throw (ex-info "REPO must be owner/name" {:repo repo})))
    [owner name]))

(def ^:private threads-query
  "query($owner:String!,$name:String!,$n:Int!,$after:String){repository(owner:$owner,name:$name){id databaseId nameWithOwner pullRequest(number:$n){
     id number state isDraft headRefOid author{login __typename ... on User{id databaseId}}
     reviewThreads(first:100,after:$after){pageInfo{hasNextPage endCursor}
       nodes{id isResolved isOutdated path line
         comments(first:100){totalCount pageInfo{hasNextPage} nodes{id databaseId author{login __typename ... on User{id databaseId}}
           body diffHunk url createdAt updatedAt commit{oid} originalCommit{oid}
           pullRequestReview{id databaseId state body updatedAt commit{oid} author{login __typename ... on User{id databaseId}}}}}}}}}}")

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

(defn collect-context
  "Collect native conversation inputs through synchronous injected readers.
   The optional issue records must come from the same complete native snapshot.
   History remains separate: nil means unavailable and [] means healthy empty.
   This result carries no disposition, settlement or native mutation authority."
  ([deps repo n]
   (require-readers deps)
   (collect-context deps repo n (fetch-issue-comments (:read-json deps) repo n)))
  ([{:keys [read-json authorized-author? read-history] :as deps} repo n raw-issue-comments]
   (require-readers deps)
   (let [[owner name] (split-repo repo)
         authorized? (memoize (partial authorized-author? repo))
         issue-comments (mapv #(cond-> (assoc (hash-body %) :source-channel :github-issue-comment)
                                (= "User" (get-in % [:user :type]))
                                (assoc :authorized? (boolean (authorized? (get-in % [:user :login])))))
                              raw-issue-comments)]
     (loop [after nil acc [] draft? nil]
       (let [pr (threads-page read-json owner name n after)
             conn (:reviewThreads pr)
             acc (into acc (:nodes conn))]
         (if (get-in conn [:pageInfo :hasNextPage])
           (recur (get-in conn [:pageInfo :endCursor]) acc (:isDraft pr))
           (let [repository (:native-repository pr)
                 history (read-history (:id repository))]
             {:repository repository :pr (dissoc pr :reviewThreads :native-repository)
              :raw-threads acc :raw-issue-comments raw-issue-comments
              :issue-comments issue-comments :history history
              :draft? (if (nil? draft?) (:isDraft pr) draft?)
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
                                                          :authorized? (boolean (authorized? (get-in c [:author :login])))
                                                          :url (:url c) :created-at (:createdAt c) :updated-at (:updatedAt c)})
                                                       (get-in t [:comments :nodes]))})) acc)})))))))
