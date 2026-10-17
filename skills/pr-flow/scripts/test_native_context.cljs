;; SPDX-License-Identifier: GPL-3.0-or-later
(ns test-native-context
  (:require [cljs.test :refer [deftest is testing]]
            ["fs" :as fs] ["path" :as path] ["crypto" :as crypto]
            [nbb.core :refer [*file*]]
            [clojure.string :as str]
            [pr-flow.actionability :as actionability]
            [pr-flow.permissions :as permissions]
            [pr-flow.native-context :as native-context]
            [pr-flow.native-context-law :as native-context-law]))

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

(def item-id "d9618d1e6c3cf0ee5e35cc46")
(def native-review
  {:id 101 :node_id "PRR_inert_101" :state "COMMENTED" :commit_id (get-in sample [:pr :headRefOid])
   :body (str "Retain this source boundary.\n<!-- cr-comment:v1:" item-id " -->")
   :submitted_at "2026-10-03T11:30:00Z"
   :user {:login "coderabbitai[bot]" :id 136622811 :node_id "BOT_inert_cr" :type "Bot"}})
(defn native-review-node [review]
  {:id (:node_id review) :databaseId (:id review) :state (:state review) :body (:body review)
   :submittedAt (:submitted_at review) :updatedAt "2026-10-03T11:31:00Z"
   :commit {:oid (:commit_id review)}
   :author {:login (let [login (get-in review [:user :login])]
                     (if (= "Bot" (get-in review [:user :type]))
                       (str/replace login #"\[bot\]$" "") login))
            :databaseId (get-in review [:user :id])
            :id (get-in review [:user :node_id]) :__typename (get-in review [:user :type])}})
(defn body-readers
  ([review-pages nodes] (body-readers review-pages nodes (page [(:thread sample)])))
  ([review-pages nodes final-page]
   (body-readers review-pages nodes final-page review-pages nodes))
  ([review-pages nodes final-page final-review-pages final-nodes]
  (let [r (readers [(page [(:thread sample)]) final-page] [[]] [])
        read (get-in r [:deps :read-json]) remaining (atom [])
        review-reads (atom [[review-pages nodes] [final-review-pages final-nodes]])]
    (assoc r :deps
           (assoc (:deps r) :body-items? true
                  :read-json
                  (fn [& args]
                    (cond
                      (= ["api" (str "repos/" repo "/pulls/" number "/reviews") "--paginate" "--slurp"] (vec args))
                      (do (swap! (:calls r) conj (vec args))
                          (when-not (seq @review-reads)
                            (throw (ex-info "Unexpected extra review collection" {})))
                          (let [[pages nodes] (first @review-reads)]
                            (swap! review-reads subvec 1)
                            (reset! remaining (vec nodes))
                            pages))
                      (some #(and (string? %) (str/starts-with? % "id=PRR_")) args)
                      (do (swap! (:calls r) conj (vec args))
                          (let [node (first @remaining)]
                            (swap! remaining subvec 1)
                            {:data {:node node}}))
                      :else (apply read args))))))))

(deftest strict-native-review-hydration-keeps-actor-specific-login-spellings
  ;; Suffix-free GraphQL Bot login is native-observed; review body/head/times
  ;; remain inert fixtures and supply no native eligibility evidence.
  (let [node (native-review-node native-review)
        hydrate (fn [review n]
                  (native-context/hydrate-review-update (fn [& _] {:data {:node n}}) review true))
        bot (hydrate native-review node)
        user-review (assoc native-review :user {:login "writer" :id 7 :node_id "U_inert_writer" :type "User"})
        user-node (native-review-node user-review)]
    (is (= "coderabbitai" (get-in node [:author :login])))
    (is (nil? (:positive-review-update-error bot)))
    (is (= "2026-10-03T11:31:00Z" (:updated_at bot)))
    (is (= node (:native-review bot)))
    (is (nil? (:positive-review-update-error (hydrate user-review user-node))))
    (doseq [bad [(assoc-in node [:author :login] "other")
                 (update node :author dissoc :login)
                 (assoc-in node [:author :login] "")
                 (assoc-in node [:author :databaseId] 1)
                 (assoc-in node [:author :id] "BOT_other")
                 (assoc-in node [:author :__typename] "User")]]
      (is (= :native-source-mismatch (:positive-review-update-error (hydrate native-review bad)))))
    (is (= :native-source-mismatch
           (:positive-review-update-error (hydrate user-review (assoc-in user-node [:author :login] "writer[bot]")))))
    (is (= :native-source-mismatch
           (:positive-review-update-error
             (hydrate (assoc-in user-review [:user :login] nil) (assoc-in user-node [:author :login] nil)))))))

(deftest opted-in-body-collection-preserves-all-reviews-and-native-edit-custody
  (let [other (assoc native-review :id 102 :node_id "PRR_inert_102" :body "No selected item here.")
        r (body-readers [[native-review] [other]] (mapv native-review-node [native-review other]))
        result (native-context/collect-context (:deps r) repo number)]
    (is (true? (:reviews-complete? result)))
    (is (= [native-review other] (:raw-reviews result)))
    (is (= [101 102] (mapv :id (:reviews result))))
    (is (= "2026-10-03T11:31:00Z" (get-in result [:reviews 0 :updated_at])))
    (is (= (native-review-node native-review) (get-in result [:reviews 0 :native-review])))
    (is (= [(:thread sample)] (:raw-threads result)))
    (is (= [] (:history result)))
    (is (= 9 (count @(:calls r))))))

(deftest opted-in-body-collection-refuses-incomplete-duplicate-and-edited-reviews
  (doseq [pages [nil [] [{}] [[nil]] [[native-review native-review]]
                [[native-review (assoc native-review :id 102)]]]]
    (let [r (body-readers pages [(native-review-node native-review)])]
      (is (thrown? js/Error (native-context/collect-context (:deps r) repo number)))))
  (doseq [node [nil
                (assoc (native-review-node native-review) :body "Edit raced collection")
                (assoc (native-review-node native-review) :databaseId 102)
                (assoc-in (native-review-node native-review) [:author :databaseId] 1)
                (assoc-in (native-review-node native-review) [:commit :oid] "unknown")
                (dissoc (native-review-node native-review) :updatedAt)]]
    (let [r (body-readers [[native-review]] [node])]
      (is (thrown? js/Error (native-context/collect-context (:deps r) repo number)))))
  (doseq [p [(assoc-in (page [(:thread sample)]) [:data :repository :pullRequest :headRefOid] (apply str (repeat 40 "b")))
             (assoc-in (page [(:thread sample)]) [:data :repository :pullRequest :state] "CLOSED")]]
    (let [r (body-readers [[native-review]] [(native-review-node native-review)] p)]
      (is (thrown? js/Error (native-context/collect-context (:deps r) repo number))))))

(deftest opted-in-body-collection-refuses-reviews-changed-after-first-hydration
  (let [edited (assoc native-review :body "A later authored edit.")
        other (assoc native-review :id 102 :node_id "PRR_inert_102" :body "Another review.")
        original-node (native-review-node native-review)]
    (doseq [[pages nodes] [[[edited] [(native-review-node edited)]]
                           [[native-review] [(assoc original-node :updatedAt "2026-10-03T11:32:00Z")]]
                           [[native-review other] [original-node (native-review-node other)]]
                           [[] []]]]
      (let [r (body-readers [[native-review]] [original-node] (page [(:thread sample)]) [pages] nodes)
            result (try (native-context/collect-context (:deps r) repo number)
                        (catch :default e {:refusal (ex-message e)}))]
        (is (= "Native snapshot changed during review-body collection" (:refusal result)))))))

(deftest fresh-body-selection-is-one-native-review-item-without-a-fake-thread
  (let [r (body-readers [[native-review]] [(native-review-node native-review)])
        snapshot (native-context/collect-context (:deps r) repo number)
        target {:kind :review-body-item :repo repo :repository-database-id (get-in sample [:repository :databaseId])
                :pr-number (get-in sample [:pr :number]) :head (:head snapshot) :review-id 101 :item-id item-id}
        select #(native-context-law/select-fresh-context %1 %2)
        selected (:selected (select snapshot target))]
    (is (= :review-body-item (:kind selected)))
    (is (= [101 item-id] [(:review-id selected) (:item-id selected)]))
    (is (= (first (:reviews snapshot)) (:review selected)))
    (is (= {"coderabbit" #{"coderabbitai[bot]"} "opencode" #{"opencode-agent[bot]"}}
           (get-in (select snapshot (assoc target :identities {"coderabbit" #{"coderabbitai[bot]"} "opencode" #{"opencode-agent[bot]"}}))
                   [:selected :identities])))
    (is (not-any? #(contains? selected %) [:id :thread-id :root-comment-id :settled?]))
    (doseq [t [(assoc target :thread-id "PRRT_inert") (assoc target :items [item-id])
               (assoc target :review-id 999) (assoc target :item-id "unknown")]]
      (is (:refusal (select snapshot t))))
    (doseq [s [(dissoc snapshot :reviews-complete?) (assoc snapshot :reviews-complete? false)
               (update snapshot :reviews conj (first (:reviews snapshot)))
               (assoc snapshot :history nil) (assoc snapshot :incomplete? true)]]
      (is (:refusal (select s target))))))

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

(deftest collector-refuses-mixed-native-page-snapshots-before-hydration
  (let [first-page (page [(:thread sample)] true "first-cursor")
        second-page (page [] true "second-cursor")]
    (doseq [[field at replacement]
            [["repository" [:data :repository :id] "R_other_repository"]
             ["PR" [:data :repository :pullRequest :id] "PR_other_pull_request"]
             ["head" [:data :repository :pullRequest :headRefOid] (apply str (repeat 40 "f"))]
             ["draft" [:data :repository :pullRequest :isDraft] true]]
            changed [replacement nil]]
      (testing (str field " changed to " (pr-str changed))
        (let [r (readers [first-page (assoc-in second-page at changed) (page [])] [[]] [])]
          (is (thrown? js/Error (native-context/collect-context (:deps r) repo number)))
          (is (= 3 (count @(:calls r))) "Refuse the differing page before reading a later restored snapshot")
          (is (empty? @(:history-reads r)) "Never select or read history from mixed native identities")
          (is (empty? @(:permissions r)) "Never hydrate mixed threads"))))
    (let [r (readers [first-page second-page (page [])] [[]] [])
          result (native-context/collect-context (:deps r) repo number)]
      (is (= [(:thread sample)] (:raw-threads result)))
      (is (= (:repository sample) (:repository result)))
      (is (= (:pr sample) (:pr result)))
      (is (false? (:draft? result)))
      (is (= [(get-in sample [:repository :id])] @(:history-reads r))))))

(deftest native-page-snapshot-is-the-exact-four-field-binding
  (let [repository (:repository sample) pr (:pr sample)]
    (is (= {:repository-id (:id repository) :pr-id (:id pr)
            :head (:headRefOid pr) :draft? (:isDraft pr)}
           (native-context-law/page-snapshot repository pr)))
    (doseq [at [[:id] [:headRefOid] [:isDraft]]]
      (is (not= (native-context-law/page-snapshot repository pr)
                (native-context-law/page-snapshot repository (assoc-in pr at nil)))))
    (is (not= (native-context-law/page-snapshot repository pr)
              (native-context-law/page-snapshot (assoc repository :id nil) pr)))))

(deftest fresh-native-context-selection-is-structural-and-preserves-the-original-thread
  (let [r (readers [(page [(:thread sample)])] [[]] [])
        snapshot (native-context/collect-context (:deps r) repo number)
        thread (first (:threads snapshot))
        target {:repo repo :repository-database-id (get-in sample [:repository :databaseId])
                :pr-number (get-in sample [:pr :number]) :head (:headRefOid (:pr sample))
                :thread-id (:id thread)}
        select #(native-context-law/select-fresh-context % target)]
    (is (= {:selected thread} (select snapshot)))
    (is (= {:selected thread} (select (assoc snapshot :threads [thread {:id "PRRT_unrelated"}]))))
    (is (= {:selected thread} (select (assoc snapshot :history [{:status :revoked}]))))
    (doseq [[at changed reason]
            [[[:repository :nameWithOwner] "other/repository" :target-mismatch]
             [[:repository :databaseId] 1 :target-mismatch]
             [[:pr :number] 1 :target-mismatch]
             [[:head] (apply str (repeat 40 "f")) :target-mismatch]
             [[:pr :state] "CLOSED" :pr-not-open]
             [[:draft?] true :draft-context]
             [[:draft?] nil :draft-context]
             [[:incomplete?] true :incomplete-context]
             [[:incomplete?] nil :incomplete-context]
             [[:history] nil :history-unavailable]
             [[:history] '() :history-unavailable]
             [[:threads] [] :thread-selection]
             [[:threads] [thread thread] :thread-selection]
             [[:threads] nil :thread-selection]]]
      (is (= {:refusal reason} (select (assoc-in snapshot at changed))) (pr-str at)))
    (doseq [key [:repo :repository-database-id :pr-number :head :thread-id]]
      (is (= {:refusal :target-unavailable}
             (native-context-law/select-fresh-context snapshot (dissoc target key))))
      (is (= {:refusal :target-unavailable}
             (native-context-law/select-fresh-context snapshot (assoc target key nil)))))
    (is (= {:refusal :context-unavailable} (select nil)))
    (is (= {:refusal :target-unavailable} (native-context-law/select-fresh-context snapshot nil)))
    (is (= {:refusal :target-mismatch}
           (select (assoc snapshot :head "unbound" :selected thread :qualified? true
                                   :permission true :disposition :informational))))
    (is (= #{:selected} (set (keys (select snapshot)))))
    (is (not-any? #(contains? (select snapshot) %)
                  [:permission :qualified? :actionability :disposition :approval :publication]))))
