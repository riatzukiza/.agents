;; SPDX-License-Identifier: GPL-3.0-or-later
(ns test-positive-thread
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str] [clojure.edn :as edn]
            ["fs" :as fs] ["path" :as path] ["crypto" :as crypto]
            [nbb.core :refer [*file*]]
            [pr-flow.native-context :as native-context]
            [pr-flow.actionability :as actionability]
            [test-informational :as informational]
            [pr-flow.positive-thread :as positive-thread]
            [pr-flow.law :as law]))

;; Only root prose/diff/coordinates and source bytes are native historical seeds.
;; Every publication, PR/User/review tuple, chronology and writer outcome below
;; is a synthetic fixture. No fixture qualifies an operational native judgment.
(def here (path/dirname *file*))
(def seeds (:seeds (js->clj (js/JSON.parse (fs/readFileSync (path/join here "fixtures/native-positive-bot-preservation-seeds.json") "utf8")) :keywordize-keys true)))
(def profile (get-in (edn/read-string (fs/readFileSync (path/join here ".." "flow.edn") "utf8")) [:flow/defaults :review/positive-bot-thread]))
(def user {:login "fixture-operator" :id "U_fixture_operator" :databaseId 1 :__typename "User"})
(def root-bot {:login "github-actions" :id "MDM6Qm90NDE4OTgyODI=" :databaseId 41898282 :__typename "Bot"})
(def assessor {:login "eta-mu-ai[bot]" :id 270021952 :node_id "BOT_kgDOEBg1QA" :type "Bot"})
(def actor {:login "fixture-operator" :id 1 :node_id "U_fixture_operator" :type "User"})
(def reason "The complete bound context independently preserves the verified existing behavior without an unaddressed corrective request.")
(def evidence "https://github.com/open-hax/knoxx/blob/d3ccf687a8756377fd6360119e2cbe3d6be42326/.github/workflows/deploy-production.yml")
(def repository {:id "R_kgDOR4ZK1A" :databaseId 1199983316 :nameWithOwner "open-hax/knoxx"})
(defn hash-text [s] (.digest (.update (crypto/createHash "sha256") s "utf8") "hex"))
(defn root-thread [s]
  (let [c (:root s) head (:commit_id c)
        id (if (= 4252401734 (:id c)) "PRRT_kwDOR4ZK1M6rn7BT" "PRRT_kwDOR4ZK1M6rn7Bc")]
    {:id id :isResolved false :isOutdated false :path (:path c) :line (:line c)
     :comments {:totalCount 1 :pageInfo {:hasNextPage false}
                :nodes [{:id (:node_id c) :databaseId (:id c) :url (:html_url c) :author root-bot
                         :body (:body c) :diffHunk (:diff_hunk c) :createdAt (:created_at c) :updatedAt (:updated_at c)
                         :commit {:oid head} :originalCommit {:oid (:original_commit_id c)}
                         :pullRequestReview {:id "PRR_fixture_root_review" :databaseId (:pull_request_review_id c)
                                             :state "COMMENTED" :body "Synthetic enclosing review retaining the full root preservation scope."
                                             :author root-bot :commit {:oid (:original_commit_id c)} :updatedAt (:updated_at c)}}]}}))
(defn pr-data [s] {:id "PR_fixture388" :number 388 :state "OPEN" :isDraft false :headRefOid (get-in s [:root :commit_id]) :author user})
(defn page [s threads] {:data {:repository (assoc repository :pullRequest (assoc (pr-data s) :reviewThreads {:nodes threads :pageInfo {:hasNextPage false}}))}})
(defn blob-response [s]
  (let [src (:source s)] {:data {:repository (assoc repository :object {:oid (:blob-oid src) :byteSize (js/Buffer.byteLength (:body src) "utf8") :text (:body src) :isTruncated false})}}))
(defn collected
  ([s issues raw history] (collected s issues raw history [raw]))
  ([s issues _raw history all-threads]
   (let [deps {:preservation-policy profile :authorized-author? (fn [& _] true) :read-history (fn [_] history)
               :read-json (fn [& args] (if (str/includes? (str (nth args 3 nil)) "object(expression:")
                                       (blob-response s) (page s all-threads)))}
         snapshot (native-context/collect-context deps "open-hax/knoxx" "388" issues)]
     {:deps deps :snapshot snapshot :thread (assoc (first (:threads snapshot)) :actionability-observations history)})))
(defn issue [id user body time]
  {:id id :node_id (str "IC_fixture_" id) :user user :body body
   :html_url (str "https://github.com/open-hax/knoxx/pull/388#issuecomment-" id) :created_at time :updated_at time})
(defn fixture [s]
  (let [raw (root-thread s) first-input (collected s [] raw [])
        input (native-context/hydrate-preservation-context (:deps first-input) (:snapshot first-input) (:thread first-input) [(get-in s [:source :path])])
        proposal-body (positive-thread/proposal-body input reason evidence)
        proposal (issue 9001 actor proposal-body "2026-10-12T23:00:00Z")
        proposed (:thread (collected s [proposal] raw []))
        p (positive-thread/latest-proposal proposed)
        assessment-body (str "Preservation assessment v2 for " (:head proposed) ":\n"
                             (pr-str (into (subvec (:payload p) 0 8) [9001 (get-in p [:source :body-sha256])
                                                               (mapv #(into % ["preservation" positive-thread/judgment reason evidence])
                                                                     (get-in proposed [:preservation-custody :cohort-targets]))])))
        assessment (issue 9002 assessor assessment-body "2026-10-12T23:10:00Z")
        body (str "Handled: preservation v2 for " (:head proposed) ":\nFinding: " (:id proposed) " comment" (:root-comment-id proposed)
                  "\nAssessment: " (:html_url assessment) "\nReason: " reason "\nEvidence: " evidence)
        reply-id (if (= 4252401734 (get-in s [:root :id])) 9003 9103)
        reply {:id (str "PRRC_fixture_writer_" reply-id) :databaseId reply-id :url (str "https://github.com/open-hax/knoxx/pull/388#discussion_r" reply-id)
               :author user :body body :diffHunk (get-in s [:root :diff_hunk]) :createdAt "2026-10-12T23:20:00Z" :updatedAt "2026-10-12T23:20:00Z"
               :commit {:oid (:head proposed)} :originalCommit {:oid (get-in s [:root :original_commit_id])}
               :pullRequestReview {:id (str "PRR_fixture_writer_" reply-id) :databaseId (inc reply-id) :state "COMMENTED" :body "" :author user
                                   :commit {:oid (:head proposed)} :updatedAt "2026-10-12T23:20:00Z"}}
        written (-> raw (update-in [:comments :nodes] conj reply) (update-in [:comments :totalCount] inc))]
    {:seed s :raw raw :proposed proposed :proposal proposal :assessment assessment :body body :reply reply :written written
     :pending (:thread (collected s [proposal assessment] raw []))
     :unresolved-written (:thread (collected s [proposal assessment] written []))
     :resolved (:thread (collected s [proposal assessment] (assoc written :isResolved true) []))}))

(deftest native-p1-preservation-requires-actual-writer-and-resolution
  ;; With the unchanged 5cea law.cljc and this source/test scaffolding, the final
  ;; empty-blocker assertion is causal RED on the genuine existing P1 law.
  ;; It is not a missing-export RED and makes no native settlement claim.
  (doseq [s seeds]
    (let [{:keys [pending unresolved-written resolved]} (fixture s)
          final (law/classify-thread resolved)]
      (is (:qualified? (positive-thread/proof pending)))
      (is (not (:settled? (positive-thread/proof pending))))
      (is (not (:settled? (positive-thread/proof unresolved-written))))
      (is (= :p1 (:severity final)))
      (is (law/finding-obligation? final))
      (is (= 1 (count (law/unsettled-blockers [(law/classify-thread pending)]))))
      (is (= 1 (count (law/unsettled-blockers [(law/classify-thread unresolved-written)]))))
      (is (:settled? (positive-thread/proof resolved)))
      (is (empty? (law/unsettled-blockers [final])))
      (is (= (:body (get-in s [:root])) (get-in final [:comments 0 :body]))))))

(deftest preservation-prospective-publication-never-invents-native-records
  (let [{:keys [proposed assessment]} (fixture (first seeds))
        result (positive-thread/assessment-publication proposed (:body assessment) assessor)]
    (is (:eligible? result))
    (is (= (:body assessment) (:body result)))
    (is (= "2026-10-12T23:00:00Z" (:created-after result)))
    (is (not-any? #(contains? result %) [:id :node_id :created_at :html_url :observations :qualified? :settled?]))
    (doseq [who [actor (assoc assessor :id 41898282 :node_id "MDM6Qm90NDE4OTgyODI=")
                  (dissoc assessor :node_id) (assoc assessor :type "User")]]
      (is (false? (:eligible? (positive-thread/assessment-publication proposed (:body assessment) who)))))
    (doseq [summary ["I agree." (str "> " (:body assessment)) (str "```\n" (:body assessment) "\n```")
                     (str/replace (:body assessment) "\"preservation\"" "\"finding\"")
                     (str/replace (:body assessment) "\"preservation\"" "\"uncertain\"")]]
      (is (false? (:eligible? (positive-thread/assessment-publication proposed summary assessor)))))))

(deftest preservation-denies-stale-mixed-foreign-or-self-evidence
  (let [{:keys [seed proposal assessment written resolved]} (fixture (first seeds))
        build (fn [issues thread] (:thread (collected seed issues thread [])))]
    (doseq [[label t]
            [["no independent assessment" (build [proposal] written)]
             ["generic handled" (build [proposal assessment] (assoc-in (assoc written :isResolved true) [:comments :nodes 1 :body] "Handled: already fine"))]
             ["finding judgment retains mixed/corrective obligation" (build [proposal (update assessment :body #(str/replace % "\"preservation\"" "\"finding\""))] written)]
             ["uncertainty is not preservation" (build [proposal (update assessment :body #(str/replace % "\"preservation\"" "\"uncertain\""))] written)]
             ["informational purpose cannot be copied" (build [proposal (update assessment :body #(str/replace % "Preservation assessment" "Actionability assessment"))] written)]
             ["same publisher with a different model label" (build [proposal (assoc assessment :user {:login "github-actions[bot]" :id 41898282 :node_id "MDM6Qm90NDE4OTgyODI=" :type "Bot"})] written)]
             ["public user is not assessor" (build [proposal (assoc assessment :user actor)] written)]
             ["edited assessment" (build [proposal (assoc assessment :updated_at "2026-10-12T23:11:00Z")] written)]
             ["assessment predates proposal" (build [proposal (assoc assessment :created_at "2026-10-12T22:59:00Z" :updated_at "2026-10-12T22:59:00Z")] written)]
             ["foreign repository node" (assoc-in resolved [:native-context :repository :id] "R_foreign")]
             ["head changed" (assoc resolved :head (apply str (repeat 40 "f")))]
             ["missing complete history" (assoc resolved :actionability-observations nil)]
             ["missing root numeric identity" (assoc-in resolved [:native-context :thread :comments :nodes 0 :author :databaseId] nil)]
             ["candidate proof flag cannot supply admission" (assoc resolved :preservation {:settled? true} :preservation-policy nil)]
             ["unreadable latest proposal" (build [proposal assessment (issue 9005 actor (str "Preservation proposal v2 for " (:head resolved) ":\nnot-edn") "2026-10-12T23:15:00Z")] written)]
             ["new issue participant" (build [proposal assessment (issue 9005 actor "I still have an unresolved question about this root." "2026-10-12T23:15:00Z")] written)]
             ["native resolution flag mismatch" (assoc resolved :resolved? false)]
             ["outdated target" (assoc resolved :outdated? true)]]]
      (testing label
        (is (false? (:settled? (positive-thread/proof t))))
        (is (= 1 (count (law/unsettled-blockers [(law/classify-thread t)]))))))
    (doseq [at [[:comments :nodes 0 :body] [:comments :nodes 0 :diffHunk] [:comments :nodes 0 :pullRequestReview :body]]]
      (is (false? (:qualified? (positive-thread/proof (build [proposal assessment] (assoc-in written at "Changed root/source review now contains an unresolved corrective request.")))))))))

(deftest preservation-revocation-survives-restoration-and-new-native-context
  (let [{:keys [seed raw proposal assessment resolved]} (fixture (second seeds))
        observation (first (:observations (positive-thread/proof resolved)))
        changed (:thread (collected seed [proposal assessment (issue 9010 actor "Additional live source dispute." "2026-10-12T23:25:00Z")]
                                    (assoc raw :isResolved true) [observation]))
        revoked (:observations (positive-thread/proof changed))
        restored (assoc resolved :actionability-observations (into [observation] revoked))]
    (is (= :revoked (:status (first revoked))))
    (is (false? (:qualified? (positive-thread/proof restored))))
    (is (= 1 (count (law/unsettled-blockers [(law/classify-thread restored)]))))
    (let [other (assoc raw :id "PRRT_fixture_other" :isResolved false)
          t (:thread (collected seed [proposal assessment] (get-in resolved [:native-context :thread]) []
                                [(get-in resolved [:native-context :thread]) other]))]
      (is (false? (:qualified? (positive-thread/proof t)))))))

(deftest actual-writer-phase-cannot-be-deleted-reopened-or-restored
  (let [{:keys [pending resolved]} (fixture (first seeds))
        observed (first (:observations (positive-thread/proof resolved)))
        deleted (assoc pending :actionability-observations [observed])
        revoked (:observations (positive-thread/proof deleted))
        restored (assoc resolved :actionability-observations (into [observed] revoked))]
    (is (false? (:qualified? (positive-thread/proof deleted))))
    (is (= :revoked (:status (first revoked))))
    (is (false? (:settled? (positive-thread/proof restored))))
    (is (= 1 (count (law/unsettled-blockers [(law/classify-thread restored)]))))))

(deftest preservation-collector-binds-bot-ids-and-refuses-bad-immutable-blobs
  (let [s (first seeds) raw (root-thread s) calls (atom []) roles (atom [])
        deps {:preservation-policy profile :authorized-author? (fn [& args] (swap! roles conj args) true)
              :read-history (fn [_] [])
              :read-json (fn [& args] (swap! calls conj args) (page s [raw]))}
        snapshot (native-context/collect-context deps "open-hax/knoxx" "388" [])
        t (assoc (first (:threads snapshot)) :actionability-observations [])]
    (is (empty? @roles) "Native Bot root never gains User writer permission")
    (is (str/includes? (str (first @calls)) "... on Bot{id databaseId}"))
    (is (false? (get-in t [:comments 0 :authorized?])))
    (doseq [bad [(assoc-in (blob-response s) [:data :repository :object :isTruncated] true)
                 (assoc-in (blob-response s) [:data :repository :object :oid] (apply str (repeat 40 "f")))
                 (assoc-in (blob-response s) [:data :repository :object :byteSize] 1)
                 (assoc-in (blob-response s) [:data :repository :object :text] nil)
                 (assoc-in (blob-response s) [:data :repository :id] "R_foreign")]]
      (let [broken (assoc deps :read-json (fn [& _] bad))]
        (is (thrown? js/Error (native-context/hydrate-preservation-context broken snapshot t [(get-in s [:source :path])])))))))

(deftest separate-purpose-history-cannot-revoke-the-other-admission
  (let [t (law/classify-thread (informational/knoxx-pre-resolution-input (first informational/knoxx-author-contexts)))
        before (actionability/disposition t)
        positive (assoc (first (:observations (positive-thread/proof (:resolved (fixture (first seeds))))))
                        :assessment-id (:assessment-id before) :status :revoked)
        after (actionability/disposition (assoc t :actionability-observations [positive]))]
    (is (= :informational (:kind before)))
    (is (= :qualified (:status after)))
    (is (= (:assessment-id before) (:assessment-id after)))
    (is (not-any? #(= :revoked (:status %)) (:observations after))))
  (let [t (:resolved (fixture (second seeds)))
        other-purpose (assoc (first (:observations (positive-thread/proof t))) :purpose :thread-actionability :status :revoked)]
    (is (:settled? (positive-thread/proof (assoc t :actionability-observations [other-purpose]))))))

(defn cohort-collected [issues raws history]
  (let [deps {:preservation-policy profile :authorized-author? (fn [& _] true) :read-history (fn [_] history)
              :read-json (fn [& args]
                           (if (str/includes? (str (nth args 3 nil)) "object(expression:")
                             (let [expression (some #(when (str/starts-with? % "expression=") (subs % 11)) args)
                                   file (subs expression 41)
                                   seed (some #(when (= file (get-in % [:source :path])) %) seeds)]
                               (blob-response seed))
                             (page (first seeds) raws)))}
        snapshot (native-context/collect-context deps "open-hax/knoxx" "388" issues)]
    {:deps deps :snapshot snapshot :threads (mapv #(assoc % :actionability-observations history) (:threads snapshot))}))
(defn assessment-text [t proposal-id proposal-sha rows]
  (str "Preservation assessment v2 for " (:head t) ":\n"
       (pr-str (into (subvec (:payload (positive-thread/latest-proposal t)) 0 8) [proposal-id proposal-sha rows]))))
(defn cohort-fixture []
  (let [raws (mapv root-thread seeds)
        initial (cohort-collected [] raws []) targets (mapv positive-thread/member-target (:threads initial))
        input (native-context/hydrate-preservation-context (:deps initial) (:snapshot initial) (first (:threads initial))
                                                          (mapv #(get-in % [:source :path]) seeds) targets)
        proposal (issue 9201 actor (positive-thread/proposal-body input reason evidence) "2026-10-12T23:00:00Z")
        proposed (first (:threads (cohort-collected [proposal] raws [])))
        proposal-sha (get-in (positive-thread/latest-proposal proposed) [:source :body-sha256])
        rows (mapv #(into % ["preservation" positive-thread/judgment
                             (str reason " Explicit member " (first %) ".") evidence]) targets)
        assessment (issue 9202 assessor (assessment-text proposed 9201 proposal-sha rows) "2026-10-12T23:10:00Z")
        single (mapv fixture seeds)
        bodies (mapv #(str/replace (:body %) "#issuecomment-9002" "#issuecomment-9202") single)
        replies (mapv (fn [f body] (assoc (:reply f) :body body)) single bodies)
        written (mapv (fn [raw reply] (-> raw (update-in [:comments :nodes] conj reply) (update-in [:comments :totalCount] inc))) raws replies)
        first-resolved [(assoc (first written) :isResolved true) (second raws)]
        both-written [(assoc (first written) :isResolved true) (second written)]
        final (mapv #(assoc % :isResolved true) written)
        issues [proposal assessment]]
    {:raws raws :targets targets :proposed proposed :proposal proposal :assessment assessment :issues issues
     :bodies bodies :replies replies :written written :first-resolved first-resolved :both-written both-written :final final
     :pending (:threads (cohort-collected issues raws []))}))

(deftest two-native-p1-roots-remain-qualified-through-sequential-actual-phases
  (let [{:keys [issues raws written first-resolved both-written final]} (cohort-fixture)
        pending (:threads (cohort-collected issues raws []))
        pending-observations (mapv #(first (:observations (positive-thread/proof %))) pending)
        only-written (:threads (cohort-collected issues [(first written) (second raws)] pending-observations))
        first-done (:threads (cohort-collected issues first-resolved pending-observations))
        first-observations (vec (mapcat #(:observations (positive-thread/proof %)) first-done))
        second-written (:threads (cohort-collected issues both-written (into pending-observations first-observations)))
        final-inputs (:threads (cohort-collected issues final (into pending-observations first-observations)))
        classified (mapv law/classify-thread final-inputs)]
    (is (every? :qualified? (map positive-thread/proof pending)))
    (is (= 2 (count (law/unsettled-blockers (mapv law/classify-thread pending)))))
    (is (= 2 (count (law/unsettled-blockers (mapv law/classify-thread only-written)))))
    (is (= [true false] (mapv :settled? (map positive-thread/proof first-done))))
    (is (= 1 (count (law/unsettled-blockers (mapv law/classify-thread first-done)))))
    (is (= 1 (count (law/unsettled-blockers (mapv law/classify-thread second-written)))))
    (is (= [:p1 :p1] (mapv :severity classified)))
    (is (every? :qualified? (map positive-thread/proof final-inputs)))
    (is (every? :settled? (map positive-thread/proof final-inputs)))
    ;; Unchanged 5cea law.cljc with this scaffolding fails this genuine P1
    ;; assertion; the native metadata and protocol records remain synthetic.
    (is (empty? (law/unsettled-blockers classified)))
    (is (= 2 (count (law/unsettled-blockers (mapv #(law/classify-thread (assoc % :preservation-policy nil)) final-inputs)))))))

(deftest cohort-judgments-are-an-exact-sorted-bijection-not-implicit-agreement
  (let [{:keys [proposed proposal assessment issues raws]} (cohort-fixture)
        parsed (positive-thread/protocol assessment) rows (get-in parsed [:payload 10])
        sha (hash-text (:body proposal))]
    (is (:eligible? (positive-thread/assessment-publication proposed (:body assessment) assessor)))
    (doseq [bad [(pop rows) (conj rows (first rows)) (vec (reverse rows))
                 (assoc-in rows [1 2] "finding") (assoc-in rows [1 2] "uncertain")
                 (assoc-in rows [1 4] "Too short") (assoc-in rows [1 1] 1)]]
      (let [body (assessment-text proposed 9201 sha bad)
            changed [(first issues) (assoc assessment :body body)]
            ts (:threads (cohort-collected changed raws []))]
        (is (false? (:eligible? (positive-thread/assessment-publication proposed body assessor))))
        (is (every? #(false? (:qualified? (positive-thread/proof %))) ts))
        (is (every? #(empty? (:observations (positive-thread/proof %))) ts))))))

(deftest bad-other-member-never-disappears-from-the-base-corpus
  (let [{:keys [issues raws written first-resolved]} (cohort-fixture)
        extra (assoc-in (second written) [:comments :nodes 1 :id] "PRRC_fixture_extra")]
    (doseq [bad [(assoc-in (second written) [:comments :nodes 1 :body] "Handled: preservation")
                 (assoc-in (second written) [:comments :nodes 1 :author :__typename] "Bot")
                 (assoc-in (second written) [:comments :nodes 1 :pullRequestReview :body] "Mixed unresolved corrective request.")
                 (assoc-in (second written) [:comments :nodes 1 :body] (str/replace (get-in (second written) [:comments :nodes 1 :body]) "#issuecomment-9202" "#issuecomment-9999"))
                 (assoc (second raws) :isResolved true)
                 (-> (second written) (update-in [:comments :nodes] conj (last (get-in extra [:comments :nodes]))) (update-in [:comments :totalCount] inc))]]
      (let [ts (:threads (cohort-collected issues [(first first-resolved) bad] []))]
        (is (every? #(false? (:qualified? (positive-thread/proof %))) ts))
        (is (every? #(empty? (:observations (positive-thread/proof %))) ts))))))

(deftest cohort-phase-loss-and-restoration-revoke-the-shared-assessment
  (let [{:keys [issues raws final]} (cohort-fixture)
        live (:threads (cohort-collected issues final []))
        history (mapv #(first (:observations (positive-thread/proof %))) live)
        lost (:threads (cohort-collected issues [(first final) (second raws)] history))
        revocations (vec (mapcat #(:observations (positive-thread/proof %)) lost))
        restored (:threads (cohort-collected issues final (into history revocations)))]
    (is (every? #(false? (:qualified? (positive-thread/proof %))) lost))
    (is (seq revocations))
    (is (every? #(= :revoked (:status %)) revocations))
    (is (every? #(false? (:settled? (positive-thread/proof %))) restored))
    (is (= 2 (count (law/unsettled-blockers (mapv law/classify-thread restored)))))))

(deftest already-resolved-prewriter-actual-records-emit-no-invalid-phase
  (let [{:keys [raws proposal assessment]} (cohort-fixture)
        resolved-raws (mapv #(assoc % :isResolved true) raws)
        initial (cohort-collected [] resolved-raws [])
        t (native-context/hydrate-preservation-context (:deps initial) (:snapshot initial) (first (:threads initial))
                                                      (mapv #(get-in % [:source :path]) seeds) (mapv positive-thread/member-target (:threads initial)))
        v (:payload (positive-thread/protocol proposal))
        manual (assoc proposal :body (str "Preservation proposal v2 for " (:head t) ":\n"
                                         (pr-str (assoc v 4 (get-in t [:preservation-custody :conversation-digest])))))
        proposed (first (:threads (cohort-collected [manual] resolved-raws [])))
        rows (get-in (positive-thread/protocol assessment) [:payload 10])
        actual-record (assoc assessment :body (assessment-text proposed 9201 (hash-text (:body manual)) rows))
        inputs (:threads (cohort-collected [manual actual-record] resolved-raws []))]
    (is (nil? (positive-thread/proposal-body t reason evidence)))
    (is (every? #(false? (:qualified? (positive-thread/proof %))) inputs))
    (is (every? #(empty? (:observations (positive-thread/proof %))) inputs))))

(deftest prospective-assessment-rejects-an-existing-preassessment-writer-candidate
  (let [{:keys [seed proposal assessment written]} (fixture (first seeds))
        t (:thread (collected seed [proposal] written []))
        result (positive-thread/assessment-publication t (:body assessment) assessor)]
    (is (false? (:eligible? result)))
    (is (not-any? #(contains? result %) [:id :node_id :created_at :html_url :observations :qualified? :settled?]))))

(deftest malformed-purpose-history-emits-no-new-observation
  (let [t (:resolved (fixture (first seeds)))]
    (doseq [history [[42] [{}] [{:purpose :unknown :repo-id (:id repository)}]
                     [{:purpose :positive-bot-thread-preservation :repo-id (:id repository) :status :qualified}]]]
      (let [result (positive-thread/proof (assoc t :actionability-observations history))]
        (is (false? (:qualified? result)))
        (is (= :unavailable (:status result)))
        (is (empty? (:observations result)))))))

(deftest nested-scalar-phase-and-judgment-rows-refuse-without-throwing
  (let [{:keys [resolved]} (fixture (first seeds))
        valid (first (:observations (positive-thread/proof resolved)))
        malformed (assoc valid :cohort-phases [42])
        result (positive-thread/proof (assoc resolved :actionability-observations [malformed]))]
    (is (false? (:qualified? result)))
    (is (= :unavailable (:status result)))
    (is (empty? (:observations result))))
  (let [{:keys [proposed proposal assessment raws]} (cohort-fixture)
        body (assessment-text proposed 9201 (hash-text (:body proposal)) [42])
        result (positive-thread/assessment-publication proposed body assessor)
        inputs (:threads (cohort-collected [proposal (assoc assessment :body body)] raws []))]
    (is (false? (:eligible? result)))
    (is (every? #(false? (:qualified? (positive-thread/proof %))) inputs))
    (is (every? #(empty? (:observations (positive-thread/proof %))) inputs))))


(deftest reserved-preservation-family-includes-literal-prefixes-and-near-forms
  (doseq [n (range 1 (inc (count "preservation")))
          opening ["Handled: " "Handled " "**Handled:** " " handled:\n\t"]]
    (let [body (str opening (subs "preservation" 0 n))]
      (is (positive-thread/settlement-attempt? body) (pr-str body))
      (is (nil? (positive-thread/settlement-details body)))))
  (doseq [body ["Handled: preservationv2" "Handled: preservation_v2"
               "Handled: preservations" "Handled: pre-servation" "Handled: pres ervation"
               "HANDLED:\tPRESERVATIO" "**Handled:** preservatio\nbad"]]
    (is (positive-thread/settlement-attempt? body) (pr-str body))
    (is (nil? (positive-thread/settlement-details body))))
  (doseq [body ["Handled: preserve existing behavior after the verified tests."
               "Handled: previously verified behavior already addresses this concern."
               "Handled: project policy and its tests already cover this case."
               "Handled: P2 concern already covered by the existing source."
               "Handled: The finding opener withdrew this finding on the current head."
               "Handled cr-comment:v1:abcdef: the native positive body item is already addressed."]]
    (is (false? (positive-thread/settlement-attempt? body)) (pr-str body)))
  (doseq [s seeds]
    (is (positive-thread/settlement-attempt? (:body (fixture s))))
    (is (some? (positive-thread/settlement-details (:body (fixture s)))))))

(deftest preservation-collector-contains-failed-source-custody-per-member
  (let [s (first seeds) raws (mapv root-thread seeds)
        {:keys [proposal]} (fixture s)
        calls (atom [])
        deps {:preservation-policy profile :authorized-author? (fn [& _] true) :read-history (fn [_] [])
              :read-json (fn [& args]
                           (if (str/includes? (str (nth args 3 nil)) "object(expression:")
                             (blob-response s) (page s raws)))}
        healthy (native-context/collect-context deps "open-hax/knoxx" "388" [proposal])
        affected (first (:threads healthy)) untouched (second (:threads healthy))
        source-path (get-in s [:source :path])]
    (doseq [bad [(assoc-in (blob-response s) [:data :repository :object] nil)
                 (assoc-in (blob-response s) [:data :repository :object :text] nil)
                 (assoc-in (blob-response s) [:data :repository :object :isTruncated] true)
                 (assoc-in (blob-response s) [:data :repository :object :byteSize] 1)
                 (assoc-in (blob-response s) [:data :repository :object :oid] (apply str (repeat 40 "f")))]]
      (let [broken (assoc deps :read-json (fn [& args]
                                          (swap! calls conj args)
                                          (if (str/includes? (str (nth args 3 nil)) "object(expression:") bad (page s raws))))
            collected (native-context/collect-context broken "open-hax/knoxx" "388" [proposal])
            ts (mapv #(assoc % :actionability-observations []) (:threads collected))
            failed (first ts)]
        (is (= raws (:raw-threads collected)))
        (is (= 2 (count ts)))
        (is (= untouched (second (:threads collected))) "An unrelated member keeps its complete native carrier")
        (is (= [] (:preservation-source-inputs failed)))
        (is (false? (get-in failed [:preservation-custody :complete?])))
        (is (= :immutable-source-unavailable (get-in failed [:preservation-custody :error])))
        (is (= {:path source-path :head (:head affected)} (get-in failed [:preservation-custody :source-error])))
        (is (every? #(false? (:qualified? (positive-thread/proof %))) ts))
        (is (every? #(empty? (:observations (positive-thread/proof %))) ts))
        (is (= [:p1 :p1] (mapv :severity (mapv law/classify-thread ts))))
        (is (= 2 (count (law/unsettled-blockers (mapv law/classify-thread ts)))))
        (is (thrown? js/Error (native-context/hydrate-preservation-context broken healthy affected [source-path])))))
    (let [provider-error (assoc deps :read-json (fn [& args]
                                                (if (str/includes? (str (nth args 3 nil)) "object(expression:")
                                                  (throw (ex-info "Untrusted provider error with private details" {:private-data "never retain" :head 123 :path 42}))
                                                  (page s raws))))
          collected (native-context/collect-context provider-error "open-hax/knoxx" "388" [proposal])
          custody (get-in collected [:threads 0 :preservation-custody])]
      (is (= {:version 2 :complete? false :error :preservation-hydration-unavailable :source-error {}} custody))
      (is (not (str/includes? (pr-str custody) "private"))))))
