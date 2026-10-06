(ns pr-flow.actionability
  "Bounded native actionability evidence. No semantic classifier, I/O, or approval law."
  (:require [clojure.string :as str] [clojure.edn :as edn]))

(def scope "resolved-author-only-empty-reviews")
(def empty-sha "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
(defn- text? [s] (and (string? s) (not (str/blank? s))))
(defn- positive-id? [n] (and (integer? n) (pos? n)))
(defn- sha? [n s] (and (string? s) (boolean (re-matches (re-pattern (str "[0-9a-f]{" n "}")) s))))
(defn- time? [s] (and (string? s) (boolean (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z" s))))
(defn author-row [a] [(:id a) (:databaseId a) (:login a) (:__typename a)])
(defn- user? [a] (and (= "User" (:__typename a)) (text? (:id a))
                          (positive-id? (:databaseId a)) (text? (:login a))))

(defn context-manifest
  "Version 1 ordered EDN vector. SHA-256 fields are computed over exact native
   decoded UTF-8 bodies/diff hunks by the effectful boundary; that boundary also
   hashes UTF-8 (pr-str manifest). Map iteration order is never part of the codec."
  [{:keys [repository pr thread]}]
  [1 [(:id repository) (:databaseId repository) (:nameWithOwner repository)]
   [(:id pr) (:number pr) (:headRefOid pr) (author-row (:author pr))]
   [(:id thread) (:isResolved thread) (:isOutdated thread) (:path thread) (:line thread)]
   (mapv (fn [c]
           (let [r (:pullRequestReview c)]
             [(:id c) (:databaseId c) (:url c) (author-row (:author c))
              (:createdAt c) (:updatedAt c) (get-in c [:commit :oid]) (get-in c [:originalCommit :oid])
              (:body-sha256 c) (:diff-sha256 c)
              [(:id r) (:databaseId r) (:state r) (:updatedAt r) (author-row (:author r))
               (get-in r [:commit :oid]) (:body-sha256 r)]])) (get-in thread [:comments :nodes]))])

(defn- context-valid? [{:keys [id head pr-author resolved? root-comment-id comments native-context context-manifest context-digest]}]
  (let [{:keys [repository pr thread]} native-context
        nodes (get-in thread [:comments :nodes]) a (:author pr)
        times (mapv :createdAt nodes)]
    (and (map? native-context) (text? (:id repository)) (positive-id? (:databaseId repository))
         (text? (:nameWithOwner repository)) (text? (:id pr)) (positive-id? (:number pr))
         (= "OPEN" (:state pr)) (user? a) (= pr-author (:login a)) (sha? 40 head) (= head (:headRefOid pr))
         (= id (:id thread)) (text? id) (true? resolved?) (true? (:isResolved thread))
         (boolean? (:isOutdated thread)) (text? (:path thread)) (positive-id? (:line thread))
         (vector? nodes) (seq nodes) (false? (get-in thread [:comments :pageInfo :hasNextPage]))
         (= (count nodes) (get-in thread [:comments :totalCount]))
         (= (count nodes) (count (set (map :id nodes))) (count (set (map :databaseId nodes))))
         (= root-comment-id (:databaseId (first nodes)))
         (= times (sort times))
         (= (mapv #(select-keys % [:id :author :body :url :created-at :updated-at]) comments)
            (mapv (fn [c] {:id (:databaseId c) :author (get-in c [:author :login]) :body (:body c)
                           :url (:url c) :created-at (:createdAt c) :updated-at (:updatedAt c)}) nodes))
         (every? (fn [c]
                   (let [r (:pullRequestReview c)]
                     (and (text? (:id c)) (positive-id? (:databaseId c)) (text? (:url c))
                          (string? (:body c)) (string? (:diffHunk c)) (sha? 64 (:body-sha256 c))
                          (sha? 64 (:diff-sha256 c)) (= (author-row a) (author-row (:author c)))
                          (time? (:createdAt c)) (time? (:updatedAt c))
                          (not (pos? (compare (:createdAt c) (:updatedAt c))))
                          (= head (get-in c [:commit :oid])) (sha? 40 (get-in c [:originalCommit :oid]))
                          (text? (:id r)) (positive-id? (:databaseId r)) (= "COMMENTED" (:state r))
                          (= "" (:body r)) (= empty-sha (:body-sha256 r))
                          (= (author-row a) (author-row (:author r))) (time? (:updatedAt r))
                          (sha? 40 (get-in r [:commit :oid]))))) nodes)
         (sha? 64 context-digest)
         (= context-manifest (pr-flow.actionability/context-manifest native-context)))))

(defn target [t]
  ["actionability/v1" (get-in t [:native-context :repository :id])
   (get-in t [:native-context :pr :id]) (:id t) (:root-comment-id t)])
(defn context-binding [t] (into (target t) [(:context-digest t) scope]))

(defn protocol
  "Two unquoted protocol lines: head-bound first line + canonical EDN scalar
   vector. Fixed arity prevents duplicate fields; no prose/HTML/fences, trailing
   payloads, implicit map keys, or generated examples can be live evidence.
   The observed OpenCode blank-line + [github run] footer is transport only."
  [c]
  (let [[header payload blank footer :as lines] (str/split (or (:body c) "") #"\n" -1)
        run-link (when (and (= 4 (count lines)) (= "" blank))
                   (re-matches #"\[github run\]\(/([a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+)/actions/runs/([1-9][0-9]*)\)" (or footer "")))]
    (when-let [[_ kind head] (and (or (= 2 (count lines)) run-link)
                                 (re-matches #"Actionability (proposal|assessment|withdrawal) v1 for ([0-9a-f]{40}):" header))]
      (try
        (let [v (edn/read-string payload)]
          (when (and (vector? v) (every? #(or (string? %) (integer? %)) v) (= payload (pr-str v)))
            {:kind (keyword kind) :head head :payload v :source c :footer-repo (second run-link)}))
        (catch #?(:clj Exception :cljs :default) _ nil)))))

(defn- native? [c]
  (and (= :github-issue-comment (:source-channel c)) (positive-id? (:id c)) (text? (:node_id c))
       (text? (:html_url c)) (time? (:created_at c)) (time? (:updated_at c))
       (= (:created_at c) (:updated_at c)) (sha? 64 (:body-sha256 c))))
(defn assessor-policy
  "Select separate actionability identities by exact native repository name/ID.
   Existing global identities remain; no approval or rejection roster is read."
  [policy repository]
  (assoc policy :identities
         (into (set (:identities policy))
               (get (:repository-identities policy)
                    [(:nameWithOwner repository) (:databaseId repository)]))))

(defn assessor-identity? [c policy]
  (and (= 1 (:version policy)) (= :provisional (:status policy))
       (= "Bot" (get-in c [:user :type]))
       (contains? (:identities policy) {:login (get-in c [:user :login])
                                       :id (get-in c [:user :id]) :node-id (get-in c [:user :node_id])})))
(defn- writer? [c] (and (= :github-issue-comment (:source-channel c)) (= "User" (get-in c [:user :type]))
                        (positive-id? (:id c)) (text? (:node_id c))
                        (positive-id? (get-in c [:user :id])) (text? (get-in c [:user :node_id]))
                        (true? (:authorized? c))))
(defn- scoped? [record t]
  (and (= (:head t) (:head record)) (= (target t) (vec (take 5 (:payload record))))
       (or (nil? (:footer-repo record)) (= (:footer-repo record) (get-in t [:native-context :repository :nameWithOwner])))))
(defn- proposal-attempt [c t]
  ;; Select live writer attempts before validating the protocol. An unreadable
  ;; payload cannot justify falling back to an older pass. Only whole protocol
  ;; examples lacking the bare header, or explicit other targets, are excluded.
  (let [[header payload] (str/split-lines (or (:body c) ""))
        explicit-target (try
                          (let [v (edn/read-string (or payload "")) prefix (vec (take 5 v))]
                            (when (and (vector? v) (= 5 (count prefix)) (= "actionability/v1" (first prefix))
                                       (every? text? (subvec prefix 1 4)) (positive-id? (nth prefix 4)))
                              prefix))
                          (catch #?(:clj Exception :cljs :default) _ nil))]
    (when (and (writer? c) (time? (:updated_at c))
               (str/starts-with? (or header "") "Actionability proposal ")
               (str/ends-with? (or header "") (str " for " (:head t) ":"))
               (or (nil? explicit-target) (= (target t) explicit-target)))
      (or (protocol c) {:kind :proposal :head (:head t) :source c}))))
(defn details?
  "Mechanical reason/evidence minimum for adapters before publication.
   Native identity, scope, freshness and independent judgment remain separate
   disposition requirements; details alone grant no classification or review."
  [reason evidence]
  (and (string? reason) (<= 40 (count (str/trim reason)))
       (string? evidence) (boolean (re-find #"https?://\S+|[a-zA-Z0-9_.-]+\.(?:cljc?|cljs|[cm]?js|md|edn|json|ya?ml|tsx?|py|sh)" evidence))))

(defn disposition
  "Default finding. Only the separate provisional policy and complete native
   binding qualify informational status; supplied classification flags do not.
   Observations are immutable facts for Receipt River, not GitHub settlements."
  [{:keys [issue-comments actionability-policy actionability-observations actionability-source-withdrawals] :as t}]
  (if-not (vector? actionability-observations)
    {:kind :finding :status :unavailable :context-digest (:context-digest t) :observations []}
  (let [actionability-policy (assessor-policy actionability-policy (get-in t [:native-context :repository]))
        records (keep protocol issue-comments)
        scoped (filter #(scoped? % t) records)
        proposals (sort-by #(get-in % [:source :updated_at])
                           (keep #(proposal-attempt % t) issue-comments))
        p (last proposals) ps (:source p)
        context-times (mapcat (fn [c] [(:updatedAt c) (get-in c [:pullRequestReview :updatedAt])])
                              (get-in t [:native-context :thread :comments :nodes]))
        proposal-ok? (and p (scoped? p t) (native? ps) (= (context-binding t) (:payload p)) (= 7 (count (:payload p)))
                          (every? #(pos? (compare (:created_at ps) %)) context-times))
        assessments (filter #(and (= :assessment (:kind %)) (assessor-identity? (:source %) actionability-policy)
                                   (= (context-binding t) (vec (take 7 (:payload %))))) scoped)
        revoked (set (map :assessment-id (filter #(= :revoked (:status %)) actionability-observations)))
        source-withdrawn? (fn [assessment]
                            (let [source (:source assessment)]
                              (or (contains? (set actionability-source-withdrawals) (:id source))
                                  (some (fn [r]
                                          (and (= :withdrawal (:kind r))
                                               (assessor-identity? (:source r) actionability-policy)
                                               (= (select-keys (get-in r [:source :user]) [:login :id :node_id])
                                                  (select-keys (:user source) [:login :id :node_id]))
                                               (= (context-binding t) (vec (take 7 (:payload r))))
                                               (= 10 (count (:payload r)))
                                               (= [(:id source) (:body-sha256 source)] (subvec (:payload r) 7 9))
                                               ;; Withdrawal edits cannot resurrect its source.
                                               (time? (get-in r [:source :updated_at]))
                                               (pos? (compare (get-in r [:source :updated_at]) (:created_at source))))) scoped))))
        valid (filter (fn [r]
                        (let [v (:payload r) c (:source r)]
                          (and proposal-ok? (native? c) (= 13 (count v)) (= [(:id ps) (:body-sha256 ps)] (subvec v 7 9))
                               (= ["informational" "complete-context/no-defect/no-request/no-question"] (subvec v 9 11))
                               (details? (nth v 11) (nth v 12))
                               (pos? (compare (:created_at c) (:updated_at ps)))
                               (not= (get-in c [:user :id]) (get-in ps [:user :id]))
                               (not= (get-in c [:user :node_id]) (get-in t [:native-context :pr :author :id]))
                               (not (source-withdrawn? r))
                               (not (revoked (:id c)))))) assessments)
        a (last (sort-by #(get-in % [:source :created_at]) valid)) as (:source a)
        conflict? (or (some (fn [c]
                              (let [header (first (str/split-lines (or (:body c) "")))]
                                (and (assessor-identity? c actionability-policy)
                                     (str/starts-with? (or header "") "Actionability assessment ")
                                     (str/ends-with? (or header "") (str " for " (:head t) ":"))
                                     (nil? (protocol c))))) issue-comments)
                      (not= (count scoped) (count (set (map #(get-in % [:source :id]) scoped))))
                      (not= (count proposals) (count (set (map #(get-in % [:source :updated_at]) proposals))))
                      (some #(or (not= "informational" (get-in % [:payload 9]))
                                 (and ps (not (neg? (compare (get-in % [:source :created_at]) (:created_at ps))))
                                      (not (some #{%} valid)))) assessments))
        withdrawn? (some source-withdrawn? assessments)
        qualified? (and (context-valid? t) proposal-ok? a (not conflict?))
        observation (fn [r]
                      (let [source (:source r)]
                        {:purpose :thread-actionability :repo-id (get-in t [:native-context :repository :id])
                         :pr-id (get-in t [:native-context :pr :id]) :thread-id (:id t) :head (:head t)
                         :context-digest (:context-digest t) :proposal-id (:id ps) :assessment-id (:id source)
                         :proposal-body-sha256 (:body-sha256 ps) :proposal-url (:html_url ps)
                         :channel :github-issue-comment :assessment-url (:html_url source)
                         :assessment-user-id (get-in source [:user :id]) :assessment-user-node (get-in source [:user :node_id])
                         :assessment-created-at (:created_at source) :assessment-updated-at (:updated_at source)
                         :assessment-body-sha256 (:body-sha256 source) :status :qualified}))
        now (observation a)
        ;; Selection is presentation, not revocation. Retain any independently
        ;; valid exact observation; changed/missing/withdrawn sources still lose it.
        live (set (map observation valid))
        old (filter #(and (= (:id t) (:thread-id %))
                           (= (get-in t [:native-context :repository :id]) (:repo-id %))
                           (= (get-in t [:native-context :pr :id]) (:pr-id %))
                           (= :qualified (:status %))) actionability-observations)
        lost (for [o old :when (and (not (revoked (:assessment-id o)))
                                    (not (and qualified? (contains? live o))))]
               (assoc o :status :revoked :reason :native-context-or-assessment-changed))
        observations (vec (concat lost (when (and qualified? (not (some #{now} old))) [now])))]
    {:kind (if qualified? :informational :finding)
     :status (cond qualified? :qualified (or conflict? withdrawn? (seq lost)) :revoked
                   (seq scoped) :ineligible :else :absent)
     :channel (when (or a (seq lost)) :github-issue-comment)
     :assessment-id (or (:id as) (:assessment-id (first lost))) :proposal-id (:id ps)
     :url (or (:html_url as) (:assessment-url (first lost)))
     :context-digest (:context-digest t) :observations observations})))

(defn finding-obligation? [t] (not= :informational (:kind (disposition t))))
