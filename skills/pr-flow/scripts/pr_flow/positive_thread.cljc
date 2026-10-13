;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.positive-thread
  "Independent preservation of a native Bot finding. Pure custody/admission;
   no semantic classifier, publication, approval, coverage or resolution effect."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [pr-flow.actionability :as actionability]))

(def purpose :positive-bot-thread-preservation)
(def judgment "complete-context/existing-behavior-preserved/no-unaddressed-defect/no-corrective-request/no-unresolved-question")
(defn- text? [s] (and (string? s) (not (str/blank? s))))
(defn- body-text [c] (if (string? (:body c)) (:body c) ""))
(defn- positive-id? [n] (and (integer? n) (pos? n)))
(defn- sha? [n s] (and (string? s) (boolean (re-matches (re-pattern (str "[0-9a-f]{" n "}")) s))))
(defn- time? [s] (and (string? s) (boolean (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z" s))))
(defn- after? [a b] (and (time? a) (time? b) (pos? (compare a b))))
(defn native-author [a] [(:login a) (:databaseId a) (:id a) (:__typename a)])
(defn issue-author [c] [(get-in c [:user :login]) (get-in c [:user :id]) (get-in c [:user :node_id]) (get-in c [:user :type])])
(defn- author? [[login id node type]]
  (and (text? login) (positive-id? id) (text? node) (#{"User" "Bot"} type)))
(defn target [t]
  ["positive-bot-cohort/v2" (get-in t [:native-context :repository :id])
   (get-in t [:native-context :pr :id])])
(defn member-target [t] [(:id t) (:root-comment-id t)])
(defn cohort-targets? [targets]
  (and (vector? targets) (seq targets)
       (every? #(and (vector? %) (= 2 (count %)) (text? (first %)) (positive-id? (second %))) targets)
       (= targets (vec (sort-by first targets)))
       (= (count targets) (count (set (map first targets))) (count (set (map second targets))))))
(defn source-path? [p]
  (and (text? p) (not (str/starts-with? p "/")) (not (str/includes? p "\u0000"))
       (not (str/includes? p "\\")) (every? #(and (text? %) (not (#{"." ".."} %))) (str/split p #"/" -1))))
(defn issue-row [c]
  [(:id c) (:node_id c) (:html_url c) (issue-author c)
   (:created_at c) (:updated_at c) (:body-sha256 c)])
(defn source-rows [inputs]
  (mapv (fn [i] [(:path i) (:head i) (:blob-oid i) (:body-sha256 i)]) inputs))

(defn protocol
  "Separate two-line canonical EDN protocol. A verified OpenCode run footer is
   transport only; no quoted, generated, trailing or alternate-purpose record."
  [c]
  (let [[header payload blank footer :as lines] (str/split (body-text c) #"\n" -1)
        link (when (and (= 4 (count lines)) (= "" blank))
               (re-matches #"\[github run\]\(/([a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+)/actions/runs/([1-9][0-9]*)\)" (or footer "")))]
    (when-let [[_ kind head] (and (or (= 2 (count lines)) link)
                                 (re-matches #"Preservation (proposal|assessment|withdrawal) v2 for ([0-9a-f]{40}):" (or header "")))]
      (try (let [v (edn/read-string payload)]
             (when (and (vector? v) (= payload (pr-str v)))
               {:kind (keyword kind) :head head :payload v :source c :footer-repo (second link)}))
           (catch #?(:clj Exception :cljs :default) _ nil)))))

(defn settlement-attempt? [body]
  (boolean (re-find #"(?i)^\s*\**\s*handled\b[^A-Za-z0-9_]*preservation\b" (if (string? body) body ""))))
(defn settlement-details [body]
  (let [[header finding assessment reason evidence :as lines] (str/split (if (string? body) body "") #"\n" -1)]
    (when-let [[_ head] (and (= 5 (count lines))
                            (re-matches #"Handled: preservation v2 for ([0-9a-f]{40}):" (or header "")))]
      (when-let [[_ thread root] (re-matches #"Finding: (\S+) comment([1-9][0-9]*)" (or finding ""))]
        (when (and (str/starts-with? (or assessment "") "Assessment: https://github.com/")
                   (str/starts-with? (or reason "") "Reason: ") (str/starts-with? (or evidence "") "Evidence: ")
                   (actionability/details? (subs reason 8) (subs evidence 10)))
          {:head head :thread thread :root (str root) :assessment-url (subs assessment 12)
           :reason (subs reason 8) :evidence (subs evidence 10)})))))

(defn- profile? [t]
  (let [p (:preservation-policy t) r (get-in t [:native-context :repository])]
    (and (= 2 (:version p)) (= :provisional (:status p))
         (= [(:nameWithOwner r) (:databaseId r) (:id r)] (:repository p))
         (set? (:root-identities p)) (set? (:assessor-identities p)))))
(defn- assessor? [c t]
  (and (profile? t) (= "Bot" (get-in c [:user :type]))
       (contains? (get-in t [:preservation-policy :assessor-identities]) (issue-author c))))
(defn- native-issue? [c]
  (and (= :github-issue-comment (:source-channel c)) (positive-id? (:id c))
       (text? (:node_id c)) (text? (:html_url c)) (author? (issue-author c))
       (time? (:created_at c)) (= (:created_at c) (:updated_at c))
       (string? (:body c)) (sha? 64 (:body-sha256 c))))
(defn- proposal-writer? [c]
  (and (= "User" (get-in c [:user :type])) (true? (:authorized? c))
       (author? (issue-author c))))
(defn- scoped? [r t]
  (and (= (:head t) (:head r)) (= (target t) (vec (take 3 (:payload r))))
       (= (get-in t [:preservation-custody :cohort-targets]) (nth (:payload r) 3 nil))
       (or (nil? (:footer-repo r)) (= (get-in t [:native-context :repository :nameWithOwner]) (:footer-repo r)))))
(defn latest-proposal
  "Select native writer attempts before validation. An ambiguous/malformed
   latest current-head attempt denies; only explicit disjoint targets exclude."
  [t]
  (last (sort-by #(get-in % [:source :updated_at])
                (keep (fn [c]
                        (let [[header payload] (str/split-lines (body-text c))
                              explicit (try (let [v (edn/read-string (or payload "")) targets (nth v 3 nil)]
                                              (when (and (vector? v) (= "positive-bot-cohort/v2" (first v))
                                                         (every? text? (take 2 (rest v))) (cohort-targets? targets))
                                                [(vec (take 3 v)) targets]))
                                            (catch #?(:clj Exception :cljs :default) _ nil))]
                          (when (and (proposal-writer? c)
                                     (str/starts-with? (or header "") "Preservation proposal ")
                                     (str/ends-with? (or header "") (str " for " (:head t) ":"))
                                     (or (nil? explicit)
                                         (and (= (target t) (first explicit)) (some #{(member-target t)} (second explicit)))))
                            (or (protocol c) {:kind :proposal :head (:head t) :source c}))))
                      (:issue-comments t)))))
(defn proposal-cohort-targets [t]
  (let [v (:payload (latest-proposal t)) targets (nth v 3 nil)]
    (when (and (= 10 (count v)) (cohort-targets? targets) (some #{(member-target t)} targets)) targets)))
(defn proposal-source-paths [t]
  (let [v (:payload (latest-proposal t)) rows (nth v 7 nil)]
    (when (and (proposal-cohort-targets t) (vector? rows) (seq rows)
               (every? #(and (vector? %) (= 4 (count %)) (source-path? (first %))) rows)
               (= (count rows) (count (set (map first rows)))))
      (mapv first rows))))

(defn- valid-node? [c head]
  (let [r (:pullRequestReview c)]
    (and (text? (:id c)) (positive-id? (:databaseId c)) (text? (:url c))
         (author? (native-author (:author c))) (string? (:body c)) (string? (:diffHunk c))
         (sha? 64 (:body-sha256 c)) (sha? 64 (:diff-sha256 c))
         (time? (:createdAt c)) (time? (:updatedAt c)) (not (pos? (compare (:createdAt c) (:updatedAt c))))
         (= head (get-in c [:commit :oid])) (sha? 40 (get-in c [:originalCommit :oid]))
         (text? (:id r)) (positive-id? (:databaseId r)) (text? (:state r))
         (author? (native-author (:author r))) (string? (:body r)) (sha? 64 (:body-sha256 r))
         (time? (:updatedAt r)) (sha? 40 (get-in r [:commit :oid])))))
(defn- native-normalized [c]
  {:id (:databaseId c) :author (get-in c [:author :login]) :body (:body c)
   :url (:url c) :created-at (:createdAt c) :updated-at (:updatedAt c)})
(defn- context-valid? [t]
  (let [{:keys [pr thread]} (:native-context t) nodes (get-in thread [:comments :nodes])
        root (first nodes) inputs (:preservation-source-inputs t) custody (:preservation-custody t)
        issues (:issue-comments t)]
    (and (profile? t) (= "OPEN" (:state pr)) (false? (:isDraft pr))
         (sha? 40 (:head t)) (= (:head t) (:headRefOid pr))
         (text? (:id pr)) (positive-id? (:number pr)) (= "User" (get-in pr [:author :__typename]))
         (author? (native-author (:author pr))) (= (:pr-author t) (get-in pr [:author :login]))
         (= (:id t) (:id thread)) (text? (:id t)) (= (:root-comment-id t) (:databaseId root))
         (boolean? (:isResolved thread)) (= (:resolved? t) (:isResolved thread))
         (false? (:isOutdated thread)) (false? (:outdated? t))
         (text? (:path thread)) (positive-id? (:line thread))
         (vector? nodes) (seq nodes) (false? (get-in thread [:comments :pageInfo :hasNextPage]))
         (= (count nodes) (get-in thread [:comments :totalCount]) (count (:comments t))
            (count (set (map :id nodes))) (count (set (map :databaseId nodes))))
         (= (mapv :createdAt nodes) (sort (map :createdAt nodes)))
         (every? #(valid-node? % (:head t)) nodes)
         (= (mapv native-normalized nodes)
            (mapv #(select-keys % [:id :author :body :url :created-at :updated-at]) (:comments t)))
         (= "Bot" (get-in root [:author :__typename]))
         (contains? (get-in t [:preservation-policy :root-identities]) (native-author (:author root)))
         (= (native-author (:author root)) (native-author (get-in root [:pullRequestReview :author])))
         (= "COMMENTED" (get-in root [:pullRequestReview :state]))
         (not= (get-in root [:author :id]) (get-in pr [:author :id]))
         (= (:context-manifest t) (actionability/context-manifest (:native-context t)))
         (sha? 64 (:context-digest t))
         (= 2 (:version custody)) (true? (:complete? custody))
         (cohort-targets? (:cohort-targets custody)) (some #{(member-target t)} (:cohort-targets custody))
         (= (contains? (set (:writer-cut-targets custody)) (member-target t))
            (boolean (and (> (count nodes) 1) (settlement-attempt? (:body (last nodes))))))
         (sha? 64 (:conversation-digest custody)) (map? (:issue-prefix-digests custody))
         (vector? issues) (= (count issues) (count (set (map :id issues))))
         (= (count issues) (count (set (map :node_id issues))))
         (= (mapv :created_at issues) (sort (map :created_at issues)))
         (every? #(and (positive-id? (:id %)) (text? (:node_id %)) (text? (:html_url %))
                      (author? (issue-author %)) (time? (:created_at %)) (time? (:updated_at %))
                      (not (pos? (compare (:created_at %) (:updated_at %))))
                      (sha? 64 (:body-sha256 %))) issues)
         (vector? inputs) (seq inputs) (= (count inputs) (count (set (map :path inputs))))
         (every? #(and (source-path? (:path %)) (= (:head t) (:head %))
                      (sha? 40 (:blob-oid %)) (sha? 64 (:body-sha256 %)) (string? (:body %))) inputs))))

(defn- members [t]
  (mapv #(assoc % :preservation-policy (:preservation-policy t)
                 :preservation-custody (:preservation-custody t)
                 :preservation-source-inputs (:preservation-source-inputs t)
                 :actionability-observations (:actionability-observations t))
        (get-in t [:preservation-custody :cohort-members])))
(defn- cohort-context-valid? [t]
  (let [ms (members t) targets (get-in t [:preservation-custody :cohort-targets])
        fields [:id :resolved? :outdated? :path :line :head :pr-author :root-comment-id
                :comments :native-context :context-manifest :context-digest :issue-comments]
        selected (some #(when (= (member-target t) (member-target %)) %) ms)]
    (and (cohort-targets? targets) (= targets (mapv member-target ms))
         (= (select-keys selected fields) (select-keys t fields))
         (every? context-valid? ms))))

(defn proposal-body
  "Prospective shared proposal over complete ordered cohort/corpus/source rows.
   Native User authentication/publication/chronology remain host obligations."
  [t reason evidence]
  (when (and (cohort-context-valid? t) (vector? (:actionability-observations t))
             (every? #(and (false? (:resolved? %))
                           (not (contains? (set (get-in t [:preservation-custody :writer-cut-targets])) (member-target %)))) (members t))
             (actionability/details? reason evidence))
    (let [n (count (:issue-comments t)) digest (get-in t [:preservation-custody :issue-prefix-digests n])]
      (when (sha? 64 digest)
        (str "Preservation proposal v2 for " (:head t) ":\n"
             (pr-str (into (target t) [(get-in t [:preservation-custody :cohort-targets])
                                      (get-in t [:preservation-custody :conversation-digest])
                                      n digest (source-rows (:preservation-source-inputs t)) reason evidence])))))))

(defn- phases? [targets phases]
  (and (vector? phases)
       (every? #(and (vector? %) (= 4 (count %)) (boolean? (nth % 3 nil))
                     (or (and (nil? (nth % 2 nil)) (false? (nth % 3 nil)))
                         (and (vector? (nth % 2 nil)) (= 11 (count (nth % 2 nil)))))) phases)
       (= targets (mapv #(vec (take 2 %)) phases))))
(defn- history-valid? [t]
  (and (vector? (:actionability-observations t))
       (every? (fn [o]
                 (and (map? o) (contains? #{:thread-actionability :positive-bot-thread-preservation} (:purpose o))
                      (text? (:repo-id o))
                      (or (not= purpose (:purpose o))
                     (and (= 2 (:version o)) (#{:qualified :revoked} (:status o))
                          (every? text? (map o [:repo-id :pr-id :thread-id :assessment-url :assessment-user-node]))
                          (every? positive-id? (map o [:root-comment-id :proposal-id :assessment-id :assessment-user-id]))
                          (sha? 40 (:head o))
                          (every? #(sha? 64 %) (map o [:context-digest :proposal-body-sha256 :assessment-body-sha256]))
                          (cohort-targets? (:cohort-targets o))
                          (some #{[(:thread-id o) (:root-comment-id o)]} (:cohort-targets o))
                          (phases? (:cohort-targets o) (:cohort-phases o))))))
               (:actionability-observations t))))
(defn- judgment-rows? [targets rows]
  (and (vector? rows)
       (every? #(and (vector? %) (= 6 (count %)) (= ["preservation" judgment] (subvec % 2 4))
                     (actionability/details? (nth % 4 nil) (nth % 5 nil))) rows)
       (= targets (mapv #(vec (take 2 %)) rows))))

(defn- evaluation [t]
  (let [p (latest-proposal t) ps (:source p) v (:payload p) issues (:issue-comments t)
        count-before (nth v 5 nil) prefix (when (and (vector? issues) (integer? count-before) (<= 0 count-before (count issues))) (subvec issues 0 count-before))
        targets (get-in t [:preservation-custody :cohort-targets])
        context-times (concat (map :updated_at prefix) (get-in t [:preservation-custody :source-updated-at]))
        p-ok? (and (cohort-context-valid? t) (= 10 (count v)) (scoped? p t) (native-issue? ps)
                   (= (:html_url ps) (str "https://github.com/" (get-in t [:native-context :repository :nameWithOwner])
                                         "/pull/" (get-in t [:native-context :pr :number]) "#issuecomment-" (:id ps)))
                   (= (nth v 4 nil) (get-in t [:preservation-custody :conversation-digest]))
                   (some? prefix) (= (nth v 6 nil) (get-in t [:preservation-custody :issue-prefix-digests count-before]))
                   (= (nth v 7 nil) (source-rows (:preservation-source-inputs t)))
                   (actionability/details? (nth v 8 nil) (nth v 9 nil))
                   (= ps (nth issues count-before nil)) (every? #(after? (:created_at ps) %) context-times))
        a-source (nth issues (if (and (integer? count-before) (<= 0 count-before)) (inc count-before) (count issues)) nil)
        a (protocol a-source) av (:payload a) identity (issue-author a-source)
        roots (map #(get-in % [:native-context :thread :comments :nodes 0]) (members t))
        revoked (set (map :assessment-id (filter #(and (= purpose (:purpose %)) (= :revoked (:status %))) (:actionability-observations t))))
        a-ok? (and p-ok? (history-valid? t) (= (+ count-before 2) (count issues))
                   (= :assessment (:kind a)) (scoped? a t) (native-issue? a-source) (assessor? a-source t)
                   (= (:html_url a-source) (str "https://github.com/" (get-in t [:native-context :repository :nameWithOwner])
                                               "/pull/" (get-in t [:native-context :pr :number]) "#issuecomment-" (:id a-source)))
                   (= 11 (count av)) (= (subvec v 0 8) (vec (take 8 av)))
                   (= [(:id ps) (:body-sha256 ps)] (subvec av 8 10))
                   (judgment-rows? targets (nth av 10 nil))
                   (after? (:created_at a-source) (:updated_at ps))
                   (not= (nth identity 1) (get-in ps [:user :id]))
                   (not= (nth identity 2) (get-in t [:native-context :pr :author :id]))
                   (every? #(and (not= (nth identity 1) (get-in % [:author :databaseId]))
                                 (not= (nth identity 2) (get-in % [:author :id]))) roots)
                   (not (contains? revoked (:id a-source))))]
    {:proposal ps :assessment a-source :proposal-ok? p-ok? :assessment-ok? a-ok? :revoked revoked}))

(defn- writer-ok? [t node normalized assessment body]
  (let [d (settlement-details body) root (get-in t [:native-context :thread :comments :nodes 0])
        actor (native-author (:author node))]
    (and d (= (:head t) (:head d)) (= (:id t) (:thread d)) (= (str (:root-comment-id t)) (:root d))
         (= (:html_url assessment) (:assessment-url d))
         (= "User" (nth actor 3)) (true? (:authorized? normalized))
         (= "User" (get-in normalized [:user :type])) (= (first actor) (get-in normalized [:user :login]))
         (not= (nth actor 1) (get-in root [:author :databaseId]))
         (not= (nth actor 2) (get-in root [:author :id]))
         (not= (nth actor 1) (get-in assessment [:user :id]))
         (not= (nth actor 2) (get-in assessment [:user :node_id]))
         (= (:createdAt node) (:updatedAt node)) (after? (:createdAt node) (:updated_at assessment))
         (after? (get-in node [:pullRequestReview :updatedAt]) (:updated_at assessment))
         (= (native-author (get-in node [:pullRequestReview :author])) actor)
         (= "COMMENTED" (get-in node [:pullRequestReview :state]))
         (= "" (get-in node [:pullRequestReview :body]))
         (= (:head t) (get-in node [:pullRequestReview :commit :oid]))
         (= body (:body node)))))

(defn- phase-advances? [old current]
  (and (= (vec (take 2 old)) (vec (take 2 current)))
       (or (nil? (nth old 2)) (= (nth old 2) (nth current 2)))
       (or (false? (nth old 3)) (true? (nth current 3)))))
(defn- observation-compatible? [old current]
  (and (= (dissoc old :cohort-phases :thread-id :root-comment-id)
          (dissoc current :cohort-phases :thread-id :root-comment-id))
       (some #{[(:thread-id old) (:root-comment-id old)]} (:cohort-targets current))
       (= (count (:cohort-phases old)) (count (:cohort-phases current)))
       (every? true? (map phase-advances? (:cohort-phases old) (:cohort-phases current)))))

(defn proof
  "Recompute all declared members and complete source/history. Every actual
   writer candidate must qualify before its delta can be normalized. Preserve
   original findings; only each member's own actual writer+resolution settles it.
   Shared monotone phases revoke deletion/edit/reopening/restoration across the
   whole cohort; unknown changes and negative/uncertain members fail closed."
  [t]
  (let [{:keys [proposal assessment assessment-ok? revoked]} (evaluation t)
        cuts (set (get-in t [:preservation-custody :writer-cut-targets]))
        phases (mapv (fn [m]
                       (let [nodes (get-in m [:native-context :thread :comments :nodes])
                             writer (when (contains? cuts (member-target m)) (last nodes))
                             valid? (and writer (writer-ok? m writer (last (:comments m)) assessment (:body writer)))]
                         {:valid? (boolean (if writer valid? (false? (:resolved? m))))
                          :phase (into (member-target m) [(when valid? (last (nth (:context-manifest m) 4 nil))) (true? (:resolved? m))])}))
                     (members t))
        observation {:purpose purpose :version 2 :repo-id (get-in t [:native-context :repository :id])
                     :pr-id (get-in t [:native-context :pr :id]) :thread-id (:id t) :root-comment-id (:root-comment-id t) :head (:head t)
                     :context-digest (get-in t [:preservation-custody :conversation-digest])
                     :cohort-targets (get-in t [:preservation-custody :cohort-targets]) :cohort-phases (mapv :phase phases)
                     :proposal-id (:id proposal) :assessment-id (:id assessment)
                     :proposal-body-sha256 (:body-sha256 proposal) :assessment-body-sha256 (:body-sha256 assessment)
                     :assessment-url (:html_url assessment) :assessment-user-id (get-in assessment [:user :id])
                     :assessment-user-node (get-in assessment [:user :node_id]) :status :qualified}
        old (filter #(and (= purpose (:purpose %)) (= :qualified (:status %))
                          (= (:repo-id observation) (:repo-id %)) (= (:pr-id observation) (:pr-id %))) (when (history-valid? t) (:actionability-observations t)))
        same-source (filter #(= (:assessment-id observation) (:assessment-id %)) old)
        history-conflict? (some #(not (observation-compatible? % observation)) same-source)
        qualified? (and assessment-ok? (every? :valid? phases) (not history-conflict?))
        relevant-old (filter #(or (some #{(member-target t)} (:cohort-targets %))
                                  (= (:assessment-id observation) (:assessment-id %))) old)
        lost (for [o relevant-old :when (and (not (contains? revoked (:assessment-id o)))
                                            (not (and qualified? (observation-compatible? o observation))))]
               (assoc o :status :revoked :reason :preservation-custody-or-assessment-changed))
        own-phase (some #(when (= (member-target t) (vec (take 2 %))) %) (:cohort-phases observation))
        writer-manifest (nth own-phase 2 nil)]
    {:qualified? (boolean qualified?) :settled? (boolean (and qualified? writer-manifest (true? (nth own-phase 3 nil))))
     :status (cond (not (history-valid? t)) :unavailable qualified? :qualified (seq lost) :revoked :else :ineligible)
     :head (:head t) :thread-id (:id t) :root-comment-id (:root-comment-id t)
     :cohort-targets (:cohort-targets observation) :cohort-phases (:cohort-phases observation)
     :proposal-id (:id proposal) :assessment-id (:id assessment) :assessment-url (:html_url assessment)
     :context-digest (get-in t [:preservation-custody :conversation-digest])
     :writer-id (when writer-manifest (nth writer-manifest 1 nil))
     :observations (vec (concat lost (when (and qualified? (not (some #{observation} old))) [observation])))}))

(defn settlement-authorization
  "Prospective body/actor check only. No fabricated native writer, time or proof."
  [t body actor]
  (let [{:keys [assessment assessment-ok?]} (evaluation t) d (settlement-details body)
        root (get-in t [:native-context :thread :comments :nodes 0])]
    (if (and assessment-ok? (:qualified? (proof t)) (false? (:resolved? t)) (not (contains? (set (get-in t [:preservation-custody :writer-cut-targets])) (member-target t)))
             d (= (:head t) (:head d)) (= (:id t) (:thread d)) (= (str (:root-comment-id t)) (:root d))
             (= (:html_url assessment) (:assessment-url d))
             (= "User" (:type actor)) (text? (:login actor)) (positive-id? (:id actor)) (text? (:node_id actor))
             (true? (:authorized? actor))
             (not= (:id actor) (get-in root [:author :databaseId])) (not= (:node_id actor) (get-in root [:author :id]))
             (not= (:id actor) (get-in assessment [:user :id])) (not= (:node_id actor) (get-in assessment [:user :node_id])))
      {:allowed? true :body body :head (:head t) :thread-id (:id t) :root-comment-id (:root-comment-id t)
       :assessment-id (:id assessment) :assessment-url (:html_url assessment) :created-after (:updated_at assessment)}
      {:allowed? false :reason :no-qualified-preservation-body-or-actor})))

(defn assessment-publication
  "Prospective exact full-cohort judgment/body. Native App authentication,
   accepted independent full-input inference and real readback remain separate."
  [t accepted-summary prospective-user]
  (let [{:keys [proposal proposal-ok?]} (evaluation t) parsed (protocol {:body accepted-summary})
        pv (:payload (latest-proposal t)) v (:payload parsed) targets (get-in t [:preservation-custody :cohort-targets])
        body (when (and proposal-ok? (= :assessment (:kind parsed)) (= (:head t) (:head parsed))
                        (= 11 (count v)) (= (subvec pv 0 8) (vec (take 8 v)))
                        (= [(:id proposal) (:body-sha256 proposal)] (subvec v 8 10))
                        (judgment-rows? targets (nth v 10 nil)))
               (str "Preservation assessment v2 for " (:head t) ":\n" (pr-str v)))
        roots (map #(get-in % [:native-context :thread :comments :nodes 0]) (members t))]
    (if (and proposal-ok? (empty? (get-in t [:preservation-custody :writer-cut-targets]))
             (every? #(false? (:resolved? %)) (members t)) (history-valid? t)
             (= (inc (nth pv 5)) (count (:issue-comments t)))
             (map? prospective-user) (= #{:login :id :node_id :type} (set (keys prospective-user)))
             (assessor? {:user prospective-user} t)
             (not= (:id prospective-user) (get-in proposal [:user :id]))
             (not= (:node_id prospective-user) (get-in t [:native-context :pr :author :id]))
             (every? #(and (not= (:id prospective-user) (get-in % [:author :databaseId]))
                           (not= (:node_id prospective-user) (get-in % [:author :id]))) roots)
             body (= accepted-summary body))
      {:eligible? true :body body :head (:head t) :cohort-targets targets :proposal-id (:id proposal)
       :proposal-body-sha256 (:body-sha256 proposal) :expected-user prospective-user :created-after (:updated_at proposal)}
      {:eligible? false :reason :ineligible-preservation-publication})))
