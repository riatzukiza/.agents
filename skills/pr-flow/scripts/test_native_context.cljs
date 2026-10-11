;; SPDX-License-Identifier: GPL-3.0-or-later
(ns test-native-context
  (:require [cljs.test :refer [deftest is testing]]
            ["fs" :as fs] ["path" :as path] ["crypto" :as crypto]
            [nbb.core :refer [*file*]]
            [clojure.string :as str]
            [pr-flow.actionability :as actionability]
            [pr-flow.permissions :as permissions]
            [pr-flow.native-context :as native-context]))

(def captures
  (:snapshots (js->clj (js/JSON.parse
                        (fs/readFileSync (path/join (path/dirname *file*)
                                                   "fixtures/native-author-walkthroughs.json") "utf8"))
                      :keywordize-keys true)))
(def sample (first captures))
(def repo (get-in sample [:repository :nameWithOwner]))
(def number (str (get-in sample [:pr :number])))
(defn byte-hash [s]
  (.digest (.update (crypto/createHash "sha256") (.from js/Buffer s "utf8")) "hex"))
(defn page
  ([nodes] (page nodes false nil))
  ([nodes more? cursor]
   {:data {:repository (assoc (:repository sample) :pullRequest
                             (assoc (:pr sample) :reviewThreads
                                    {:nodes nodes :pageInfo {:hasNextPage more? :endCursor cursor}}))}}))
(defn readers [pages issue-pages history]
  (let [calls (atom []) permissions (atom []) history-reads (atom []) remaining (atom pages)]
    {:calls calls :permissions permissions :history-reads history-reads
     :deps {:read-json (fn [& args]
                         (swap! calls conj (vec args))
                         (if (= ["api" "graphql"] (vec (take 2 args)))
                           (let [response (first @remaining)]
                             (when-not (seq @remaining)
                               (throw (ex-info "Unexpected extra native page read" {})))
                             (swap! remaining subvec 1)
                             (if (instance? js/Error response) (throw response) response))
                           (do
                             (when-not (= ["api" (str "repos/" repo "/issues/" number "/comments")
                                           "--paginate" "--slurp"] (vec args))
                               (throw (ex-info "Unexpected native request" {:args args})))
                             issue-pages)))
            :authorized-author? (fn [r login]
                                  (swap! permissions conj [r login])
                                  (= login (get-in sample [:pr :author :login])))
            :read-history (fn [id] (swap! history-reads conj id) history)}}))

(deftest collector-preserves-complete-outer-pagination
  (let [a (:thread sample) b (assoc a :id "PRRT_synthetic_second")
        r (readers [(page [a] true "cursor-one") (page [b])] [[]] [])
        result (native-context/collect-context (:deps r) repo number)
        query-calls (filter #(= ["api" "graphql"] (vec (take 2 %))) @(:calls r))]
    (is (= [a b] (:raw-threads result)))
    (is (= [(:id a) (:id b)] (mapv :id (:threads result))))
    (is (= (:repository sample) (:repository result)))
    (is (= (:pr sample) (:pr result)))
    (is (= ["api" (str "repos/" repo "/issues/" number "/comments") "--paginate" "--slurp"]
           (first @(:calls r))))
    (is (= 2 (count query-calls)))
    (is (= ["-f" "after=cursor-one"] (vec (take-last 2 (second query-calls)))))
    (is (not-any? #(str/starts-with? % "after=") (first query-calls)))
    (doseq [call query-calls]
      (is (some #(= ["-f" "owner=open-hax"] %) (partition 2 1 call)))
      (is (some #(= ["-f" "name=proxx"] %) (partition 2 1 call)))
      (is (some #(= ["-F" (str "n=" number)] %) (partition 2 1 call)))
      (is (str/includes? (nth call 3) "databaseId"))
      (is (str/includes? (nth call 3) "pullRequestReview")))
    (is (= [(get-in sample [:repository :id])] @(:history-reads r)))
    (is (= [[repo (get-in sample [:pr :author :login])]] @(:permissions r)))
    (is (false? (:incomplete? result)))
    (is (false? (:draft? result))))
  (let [r (readers [(page [])] [[]] []) result (native-context/collect-context (:deps r) repo number)]
    (is (= [] (:threads result)))
    (is (= [] (:raw-threads result)))
    (is (= [] (:history result)))
    (is (false? (:incomplete? result)))))

(deftest collector-refuses-invalid-or-failed-required-pages
  (let [good (page [(:thread sample)])]
    (doseq [bad [nil {}
                 (assoc good :errors [{:message "partial native response"}])
                 (assoc-in good [:data :repository] nil)
                 (assoc-in good [:data :repository :pullRequest] nil)
                 (assoc-in good [:data :repository :pullRequest :isDraft] nil)
                 (assoc-in good [:data :repository :pullRequest :reviewThreads] nil)
                 (assoc-in good [:data :repository :pullRequest :reviewThreads :nodes] nil)
                 (assoc-in good [:data :repository :pullRequest :reviewThreads :pageInfo] nil)
                 (assoc-in good [:data :repository :pullRequest :reviewThreads :pageInfo :hasNextPage] nil)
                 (page [] true nil)
                 (page [] true " ")
                 (page [(assoc (:thread sample) :comments nil)])
                 (page [(assoc-in (:thread sample) [:comments :nodes] nil)])
                 (page [(assoc-in (:thread sample) [:comments :pageInfo :hasNextPage] nil)])]]
      (testing (pr-str bad)
        (let [r (readers [bad] [[]] [])]
          (is (thrown-with-msg? js/Error #"Invalid GraphQL review-thread page"
                               (native-context/collect-context (:deps r) repo number)))
          (is (empty? @(:history-reads r))))))
    (doseq [bad [nil (assoc good :errors [{:message "later partial response"}])
                 (ex-info "Mock native process exited nonzero" {})]]
      (let [r (readers [(page [] true "next") bad] [[]] [])]
        (is (thrown? js/Error (native-context/collect-context (:deps r) repo number)))
        (is (= 3 (count @(:calls r))))
        (is (empty? @(:history-reads r)))))
    (let [r (readers [(page [] true "same") (page [] true "same")] [[]] [])]
      (is (thrown-with-msg? js/Error #"Invalid GraphQL review-thread page"
                           (native-context/collect-context (:deps r) repo number)))
      (is (= 3 (count @(:calls r)))))))

(deftest collector-keeps-nested-comment-incompleteness
  (let [t (assoc-in (:thread sample) [:comments :pageInfo :hasNextPage] true)
        r (readers [(page [t])] [[]] []) result (native-context/collect-context (:deps r) repo number)]
    (is (true? (:incomplete? result)))
    (is (= [t] (:raw-threads result)))
    (is (true? (get-in result [:threads 0 :native-context :thread :comments :pageInfo :hasNextPage])))
    (is (= 2 (count @(:calls r))))))

(deftest collector-preserves-complete-issue-records-and-shared-snapshot
  (let [writer {:id 701 :node_id "IC_synthetic_writer" :user {:type "User" :login "riatzukiza"}
                :body "Synthetic proposal: café 🧭\n" :created_at "2026-10-03T14:00:00Z"
                :updated_at "2026-10-03T14:00:00Z" :html_url "https://example.invalid/701"}
        bot {:id 702 :node_id "IC_synthetic_bot" :user {:type "Bot" :login "model[bot]"}
             :body "Synthetic withdrawal history retained verbatim" :html_url "https://example.invalid/702"}
        raw [writer bot]
        r (readers [(page [(:thread sample)])] [[writer] [bot]] [])
        result (native-context/collect-context (:deps r) repo number)
        supplied (readers [(page [(:thread sample)])] nil [])
        same (native-context/collect-context (:deps supplied) repo number raw)]
    (is (= raw (:raw-issue-comments result)))
    (is (= result same))
    (is (= (assoc writer :body-sha256 (byte-hash (:body writer))
                        :source-channel :github-issue-comment :authorized? true)
           (first (:issue-comments result))))
    (is (= (assoc bot :body-sha256 (byte-hash (:body bot)) :source-channel :github-issue-comment)
           (second (:issue-comments result))))
    (is (= (:issue-comments result) (get-in result [:threads 0 :issue-comments])))
    (is (= [[repo "riatzukiza"]] @(:permissions r)))
    (is (= 1 (count @(:calls supplied))))
    (is (= ["api" "graphql"] (vec (take 2 (first @(:calls supplied)))))))
  (doseq [bad [nil [] [nil] [{}] [[nil]] [[{}]] [[{:body nil}]] [[{:body 7}]]]]
    (let [r (readers [(page [])] bad [])]
      (is (thrown-with-msg? js/Error #"Invalid PR issue-comment page"
                           (native-context/collect-context (:deps r) repo number)))
      (is (= 1 (count @(:calls r))))
      (is (empty? @(:history-reads r)))))
  (let [read-failure (fn [& _] (throw (ex-info "Mock issue page read failed" {})))]
    (is (thrown-with-msg? js/Error #"Mock issue page read failed"
                         (native-context/fetch-issue-comments read-failure repo number)))))

(deftest collector-preserves-native-utf8-and-context-custody
  (doseq [s captures]
    (let [hydrated (native-context/hydrate-actionability-context (:repository s) (:pr s) (:thread s))
          context (:native-context hydrated)]
      (is (= (:repository s) (:repository context)))
      (is (= (:pr s) (:pr context)))
      (is (= (:thread s)
             (update-in (:thread context) [:comments :nodes]
                        #(mapv (fn [c] (-> (dissoc c :body-sha256 :diff-sha256)
                                          (update :pullRequestReview dissoc :body-sha256))) %))))
      (doseq [c (get-in context [:thread :comments :nodes])]
        (is (= (byte-hash (:body c)) (:body-sha256 c)))
        (is (= (byte-hash (:diffHunk c)) (:diff-sha256 c)))
        (is (= (byte-hash (get-in c [:pullRequestReview :body]))
               (get-in c [:pullRequestReview :body-sha256]))))
      (is (= (actionability/context-manifest context) (:context-manifest hydrated)))
      (is (= (byte-hash (pr-str (:context-manifest hydrated))) (:context-digest hydrated)))))
  (let [t (-> (:thread sample)
              (assoc-in [:comments :nodes 0 :body] "Synthetic café 🧭\nsecond line\r\n")
              (assoc-in [:comments :nodes 0 :diffHunk] "@@ synthetic @@\n+ λ 日本語 🧪\n")
              (assoc-in [:comments :nodes 0 :pullRequestReview :body] "Synthetic résumé 🧵\n"))
        h #(native-context/hydrate-actionability-context (:repository sample) (:pr sample) %)
        a (h t)]
    (is (= a (h t)))
    (doseq [c (get-in a [:native-context :thread :comments :nodes])]
      (is (= (byte-hash (:body c)) (:body-sha256 c)))
      (is (= (byte-hash (:diffHunk c)) (:diff-sha256 c)))
      (is (= (byte-hash (get-in c [:pullRequestReview :body])) (get-in c [:pullRequestReview :body-sha256]))))
    (doseq [changed [(update-in t [:comments :nodes 0 :body] str "edit")
                     (update-in t [:comments :nodes 0 :diffHunk] str "edit")
                     (update-in t [:comments :nodes 0 :pullRequestReview :body] str "edit")
                     (assoc-in t [:comments :nodes 0 :updatedAt] "2026-10-04T00:00:00Z")
                     (assoc-in t [:comments :nodes 0 :pullRequestReview :updatedAt] "2026-10-04T00:00:00Z")]]
      (is (not= (:context-digest a) (:context-digest (h changed)))))))

(deftest collector-retains-history-and-injected-permission-boundaries
  (doseq [history [nil [] [{:status :revoked :assessment-id 7002 :native-source-id 701}]]]
    (let [r (readers [(page [(:thread sample)])] [[]] history)
          result (native-context/collect-context (:deps r) repo number)]
      (is (= history (:history result)))
      (is (= [(get-in sample [:repository :id])] @(:history-reads r)))
      (doseq [t (:threads result)]
        (is (not-any? #(contains? t %) [:actionability :actionability-observations :actionability-policy
                                      :identities :settled? :severity :resolution])))))
  (let [r (readers [(page [(:thread sample)])] [[]] [])
        denied (assoc (:deps r) :authorized-author? (fn [_ _] false))
        result (native-context/collect-context denied repo number)]
    (is (every? false? (map :authorized? (get-in result [:threads 0 :comments])))))
  (doseq [reader [:authorized-author? :read-history]]
    (let [r (readers [(page [(:thread sample)])] [[]] [])
          failed (assoc (:deps r) reader (fn [& _] (throw (ex-info "Injected native read failed" {}))))]
      (is (thrown-with-msg? js/Error #"Injected native read failed"
                           (native-context/collect-context failed repo number)))))
  (doseq [reader [:read-json :authorized-author? :read-history]]
    (let [r (readers [(page [])] [[]] [])]
      (is (thrown-with-msg? js/Error #"requires injected"
                           (native-context/collect-context (dissoc (:deps r) reader) repo number)))
      (is (empty? @(:calls r)))
      (is (empty? @(:permissions r)))
      (is (empty? @(:history-reads r))))))

(deftest repository-writer-roles-preserve-the-canonical-predicate
  (doseq [role ["admin" "maintain" "write"]]
    (is (= role (permissions/writer-role? role))))
  (doseq [role [nil false true "read" "triage" "ADMIN" "Write" "write "
                "owner" "push" :write {:permission "admin"} ["write"]]]
    (is (nil? (permissions/writer-role? role)))))
