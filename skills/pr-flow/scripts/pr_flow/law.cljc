(ns pr-flow.law
  "Pure laws for the PR flow: severity, thread classification, review state,
   and the merge gate. No I/O; the CLI in ../pr.cljs feeds it GitHub data
   already decoded into Clojure maps."
  (:require [clojure.string :as str]))

;; --- severity -------------------------------------------------------------

(def severity-rank
  "Lower is more severe. P0/P1 must be fixed before merge."
  {:p0 0 :p1 1 :p2 2 :p3 3 :unknown 2})

(def ^:private severity-markers
  ;; Ordered: first match wins. CodeRabbit writes e.g.
  ;; "_⚠️ Potential issue_ | _🔴 Critical_" or "_🧹 Nitpick_ | _🔵 Trivial_".
  ;; Explicit P-labels (from humans or other agents) are honoured first.
  ;; JS regexes accept an inline (?i) only at the start of a pattern.
  [[#"(?i)\bP0\b" :p0]
   [#"(?i)\bP1\b" :p1]
   [#"(?i)\bP2\b" :p2]
   [#"(?i)\bP3\b" :p3]
   [#"(?i)🔴|\bcritical\b" :p0]
   [#"(?i)🟠|\bmajor\b" :p1]
   [#"(?i)🟡|\bminor\b" :p2]
   [#"(?i)🔵|🧹|\btrivial\b|\bnitpick\b" :p3]
   [#"(?i)⚠|potential issue" :p1]
   [#"(?i)🛠|refactor suggestion" :p2]])

(defn- head-text
  "Only the first few lines carry the severity banner; the rest is prose that
   may mention words like 'critical' incidentally."
  [body]
  (->> (str/split-lines (or body "")) (take 3) (str/join "\n")))

(defn severity
  "Classify a review comment body into :p0..:p3, or :unknown."
  [body]
  (let [head (head-text body)]
    (or (some (fn [[re sev]] (when (re-find re head) sev)) severity-markers)
        :unknown)))

(defn blocking?
  "P0 and P1 must be fixed (not deferred) before merge."
  [sev]
  (<= (severity-rank sev 2) 1))

;; --- titles ---------------------------------------------------------------

(defn title-of
  "One-line summary of a review comment: CodeRabbit's bold title when present,
   else the first prose line. HTML comments, tags and banner lines are skipped."
  [body]
  (let [text (-> (or body "")
                 (str/replace #"<!--[\s\S]*?-->" "")
                 (str/replace #"<details>[\s\S]*?</details>" "")
                 (str/replace #"</?sub>" "")
                 (str/replace #"!\[[^\]]*\]\([^)]*\)" ""))
        bold (some-> (re-find #"\*\*([^*\n]+)\*\*" text) second str/trim not-empty)
        prose (->> (str/split-lines text)
                   (map str/trim)
                   (remove #(or (str/blank? %) (str/starts-with? % "<") (re-find #"^_.*_$" %)))
                   first)
        t (or bold prose "")]
    (if (> (count t) 110) (str (subs t 0 107) "...") t)))

;; --- threads --------------------------------------------------------------

(def resolution-verbs
  "A settlement reply must open with one of these, so the outcome is
   machine-readable: `Fixed in <sha>: ...`, `Deferred to <card>: ...`,
   `Rejected: <reason>`, `Handled: ...`."
  #{"fixed" "deferred" "rejected" "handled"})

(defn resolution-of
  "Parse the outcome keyword from a settlement reply, or nil."
  [body]
  (when-let [[_ verb] (re-find #"(?i)^\s*\**\s*(fixed|deferred|rejected|handled)\b" (or body ""))]
    (keyword (str/lower-case verb))))

(defn bot? [login]
  (boolean (and login (re-find #"(?i)\[bot\]$|coderabbit|copilot|kimi|codex|opencode|github-actions" login))))

(def ^:private confirmation
  "A reviewer reply that accepts the settlement rather than disputing it."
  #"(?i)review thread resolved|✅ review thread resolved|(?:^|[.!]\s*)verified(?: the fix)?[.!]?\s*$|(?:^|[.!]\s*)this addresses (?:the|my) (?:finding|issue|comment)[.!]?\s*$")

(defn classify-thread
  "thread: {:id :resolved? :outdated? :path :line
             :comments [{:author :body :url :created-at}]}
   Returns the thread with :severity, :reviewer, :resolution, :settled?, and
   :contested? — true when a reviewer replied after the last settlement
   without confirming it. A contested thread blocks the gate until it is
   settled again, even if GitHub shows it resolved."
  [{:keys [comments] :as thread}]
  (let [opener (first comments)
        replies (vec (rest comments))
        settle-idx (->> (map-indexed vector replies)
                        (filter (fn [[_ c]] (and (not (bot? (:author c))) (resolution-of (:body c)))))
                        (map first) last)
        settlement (when settle-idx (resolution-of (:body (nth replies settle-idx))))
        later (when settle-idx (subvec replies (inc settle-idx)))
        settler (when settle-idx (:author (nth replies settle-idx)))
        contested? (boolean (some #(and (not= settler (:author %))
                                       (not (re-find confirmation (str (:body %))))) later))]
    (assoc thread
           :reviewer (:author opener)
           :severity (severity (:body opener))
           :resolution settlement
           :settled? (and (some? settlement) (not contested?))
           :contested? contested?)))

(defn review-body-findings
  "Extract each outside-diff or nitpick finding and its stable CodeRabbit ID."
  [body]
  (let [identified (->> (re-seq #"<summary><em>([^<]*)</em> · ([\s\S]*?) · <code>[^<]*</code></summary>[\s\S]*?<!-- cr-comment:v1:([a-z0-9]+) -->" (str body))
                        (mapv (fn [[_ banner title id]]
                                {:id id :severity (severity banner) :title title})))
        known (set (map :id identified))
        other (for [[_ id] (re-seq #"<!-- cr-comment:v1:([a-z0-9]+) -->" (str body))
                    :when (not (known id))]
                {:id id :severity :p1 :title "Unclassified review-body finding; fix before merge"})]
    (into identified other)))

(defn- answered-finding? [review comments {:keys [id severity]}]
  (some (fn [{:keys [body created_at]}]
          (and created_at
               (not (neg? (compare created_at (:submitted_at review))))
               (re-find (re-pattern (str "(?i)review-id:" (:id review) "\\b")) (str body))
               (some (fn [[_ verb marker]]
                       (and (= marker id)
                            (or (not (blocking? severity))
                                (= "fixed" (str/lower-case verb)))))
                     (re-seq #"(?mi)^\s*[-*]\s*(Fixed|Deferred|Rejected|Handled)\b[^\n]*?cr-comment:v1:([a-z0-9]+)\b" (str body)))))
        comments))

(defn unanswered-review-count
  "A flagged review clears only when each identified item has an authorized,
   later settlement; P0/P1 items require Fixed. Unknown items fail closed."
  [reviews comments]
  (reduce +
          (for [review reviews
                :let [findings (review-body-findings (:body review))]]
            (if (seq findings)
              (count (remove #(answered-finding? review comments %) findings))
              1))))

(defn stage-review-rounds
  "Count full review timestamps only within the latest consecutive stage run.
   Comments are persisted PR markers; legacy PRs without markers count all."
  [reviews stage-comments stage]
  (let [markers (sort-by :created_at stage-comments)
        latest (last markers)]
    (if (and latest (not= stage (:stage latest)))
      0
      (let [current (reverse (take-while #(= stage (:stage %)) (reverse markers)))
            start (:created_at (first current))]
        (count (filter #(or (nil? start)
                            (not (neg? (compare (:submitted_at %) start)))) reviews))))))

;; --- trusted hosted review evidence ---------------------------------------

(def eligible-reviewers #{"coderabbit" "codex" "mimo" "kimi"})
(def default-reviewer-identities
  ;; GitHub REST identities observed in this workflow. Kimi must be bound to
  ;; its verified GitHub App identity by reviewed repository configuration.
  {"coderabbit" #{"coderabbitai[bot]"}
   "codex" #{"chatgpt-codex-connector[bot]"}
   "mimo" #{"eta-mu-ai[bot]"}
   "kimi" #{}})

(defn valid-head? [head]
  (boolean (and (string? head) (re-matches #"[0-9a-f]{40}" head))))

(defn trusted-reviewer
  "Match exact Bot identities; substring matches and CLI provider claims are
   never authority. An identity configured for two providers is ambiguous."
  [{:keys [user provider]} identities]
  (let [login (str/lower-case (str (:login user)))
        matches (for [[reviewer logins] identities
                      :when (and (eligible-reviewers reviewer)
                                 (contains? (set logins) login))] reviewer)]
    (when (and (= "Bot" (:type user)) (= 1 (count matches))
               (or (nil? provider) (= provider (first matches))))
      (first matches))))

(defn coderabbit-covered-heads
  "Decode only the observed final_review_risk_coverage fields. This marker
   establishes coverage, never APPROVED state. Duplicated fields fail closed."
  [body]
  (set
   (keep (fn [[_ payload]]
           (let [field (fn [key]
                         (let [values (map second (re-seq (re-pattern (str "\"" key "\"\\s*:\\s*\"([^\"]*)\"")) payload))]
                           (when (= 1 (count values)) (first values))))
                 source (field "sourceCommitId") covered (field "coveredCommitId")]
             (when (and (= "reviewed" (field "kind"))
                        (valid-head? source) (= source covered)) covered)))
         (re-seq #"<!--\s*final_review_risk_coverage:\s*\{([^}]*)\}\s*-->" (str body)))))

(defn verdict-prose
  "Exclude quoted code examples from provider verdict recognition. An
   unterminated fenced block is ambiguous and supplies no passing prose."
  [body]
  (let [prose (str/replace (str body) #"(?ms)^[ \t]*(`{3,}|~{3,})[^\n]*\n.*?^[ \t]*\1[ \t]*$" "")]
    (when-not (re-find #"(?m)^[ \t]*(?:`{3,}|~{3,})" prose) prose)))

(defn review-evidence
  "Trust completed explicit verdicts as well as formal approvals, while
   retaining their distinct channels and immutable commit coverage."
  [head reviews comments identities]
  (let [trusted (->> reviews
                     (keep (fn [r]
                             (when-let [p (trusted-reviewer r identities)]
                               (when (and (valid-head? head) (= head (:commit_id r))
                                          (string? (:submitted_at r)))
                                 (assoc r :reviewer p)))))
                     (sort-by (juxt :submitted_at :id)))
        review-verdicts (keep (fn [r]
                               (cond
                                 (#{"CHANGES_REQUESTED" "DISMISSED"} (:state r))
                                 (assoc r :positive? false :channel :github-review)
                                 (= "APPROVED" (:state r))
                                 (assoc r :positive? true :channel :github-approved)
                                 (and (= "COMMENTED" (:state r))
                                      (not (re-find #"(?i)review (?:incomplete|rate limited)|partial review|unreviewed files|unable to complete (?:the )?review" (str (:body r))))
                                      (re-find #"(?im)^\s*(?:\*\*)?(?:Confirmed findings:\s*none|No confirmed findings|No issues found)(?:\*\*)?\s*(?:[.—-]|$)" (or (verdict-prose (:body r)) "")))
                                 (assoc r :positive? true :channel :explicit-review-verdict))) trusted)
        comment-verdicts
        (keep (fn [c]
                (let [p (trusted-reviewer c identities)
                      body (str (:body c))
                      recent (second (re-find #"(?s)<!--\s*recent_review_start\s*-->(.*?)<!--\s*recent_review_end\s*-->" body))
                      passing? (case p
                                 "coderabbit" (and ((coderabbit-covered-heads body) head)
                                                   recent (str/includes? recent head)
                                                   (re-find #"(?m)^No actionable comments were generated in the recent review\." recent))
                                 "codex" (and (= head (:resolved-commit-id c))
                                              (re-find #"^Codex Review: Didn't find any major issues\." body))
                                 false)]
                  (when (and (valid-head? head) passing?
                             (string? (or (:updated_at c) (:created_at c))))
                    (assoc c :reviewer p :positive? true :channel :explicit-issue-verdict
                           :submitted_at (or (:updated_at c) (:created_at c)))))) comments)
        decisive (reduce (fn [m r] (assoc m (:reviewer r) r)) {}
                         (sort-by (juxt :submitted_at :id) (concat review-verdicts comment-verdicts)))
        approved (into {} (for [[p r] decisive :when (:positive? r)] [p #{head}]))
        covered (reduce (fn [m r]
                          (if (and (not= "coderabbit" (:reviewer r))
                                   (or (not (str/blank? (:body r)))
                                       (#{"APPROVED" "CHANGES_REQUESTED"} (:state r))))
                            (update m (:reviewer r) (fnil conj #{}) head) m)) {} trusted)
        covered (reduce (fn [m c]
                          (if (and (valid-head? head)
                                   (= "coderabbit" (trusted-reviewer c identities))
                                   ((coderabbit-covered-heads (:body c)) head))
                            (update m "coderabbit" (fnil conj #{}) head) m)) covered comments)]
    {:approved-heads approved :reviewed-heads covered
     :approval-evidence (into {} (for [[p r] decisive :when (:positive? r)]
                                  [p {:head head :channel (:channel r) :id (:id r)}]))}))

(defn reviewer-check
  "Only named reviewer outputs are optional. Evidence gates and required
   checks stay deterministic obligations, even when their name mentions AI."
  [check]
  (get {"CodeRabbit" "coderabbit" "Codex" "codex" "MiMo" "mimo" "Kimi" "kimi"}
       (:name check)))

(defn cooldown-ms
  "Parse a wait/retry duration from a rate-limit reply, not allowance counts."
  [body]
  (when-let [[_ duration] (re-find #"(?i)(?:wait|retry(?: again)?(?: in| after)?|try again in|cooldown:?|next included review will be available in)[^0-9]*([^\n.<]+)" (str body))]
    (let [parts (re-seq #"(?i)([0-9]+)\s*(hours?|minutes?|seconds?)" duration)]
      (when (seq parts)
        (reduce + (for [[_ n unit] parts]
                    (* #?(:clj (Long/parseLong n) :cljs (js/parseInt n 10))
                       (cond (str/starts-with? (str/lower-case unit) "hour") 3600000
                             (str/starts-with? (str/lower-case unit) "minute") 60000
                             :else 1000))))))))

(defn- instant-ms [value]
  (when (string? value)
    #?(:clj (try (.toEpochMilli (java.time.Instant/parse value)) (catch Exception _ nil))
       :cljs (let [ms (js/Date.parse value)] (when-not (js/isNaN ms) ms)))))

(defn request-verdict
  "Manual requests are bounded: exact-head pending requests are reused,
   quota replies yield retry timestamps, and reaching the cap never posts."
  [{:keys [head reviewer comments checks rounds max-loops now-ms identities]
    :or {max-loops 5 identities default-reviewer-identities}}]
  (let [requests (filter #(and (:trusted? %)
                               (str/includes? (str (:body %)) (str "pr-flow-review:" head " -->"))
                               (or (str/includes? (str (:body %)) (str "pr-flow-reviewer:" reviewer " -->"))
                                   (and (= "coderabbit" reviewer)
                                        (not (str/includes? (str (:body %)) "pr-flow-reviewer:"))
                                        (re-find #"(?i)@coderabbitai (?:full )?review" (str (:body %)))))) comments)
        request (last (sort-by :created_at requests))
        replies (filter #(= reviewer (trusted-reviewer % identities)) comments)
        limit (->> replies
                   (filter #(re-find #"(?i)rate.?limit|review limit|quota.*(?:reached|exceeded)" (str (:body %))))
                   (sort-by #(or (:updated_at %) (:created_at %) "")) last)
        delay (some-> limit :body cooldown-ms)
        limit-at (when limit (instant-ms (or (:updated_at limit) (:created_at limit))))
        retry-at (when (and delay limit-at) (+ limit-at delay))
        covered? (some #(and (= "coderabbit" reviewer)
                             ((coderabbit-covered-heads (:body %)) head)
                             (or (nil? request)
                                 (not (neg? (compare (or (:updated_at %) (:created_at %) "") (:created_at request)))))) replies)
        pending? (some #(and (= reviewer (reviewer-check %))
                            (#{"PENDING" "QUEUED" "IN_PROGRESS"} (str/upper-case (str (:state %))))) checks)]
    (cond
      (or (not (valid-head? head)) (not (eligible-reviewers reviewer))) {:status :invalid}
      (>= (or rounds 0) max-loops) {:status :budget-exhausted :rounds rounds :max-loops max-loops}
      (and limit (or (nil? retry-at) (nil? now-ms))) {:status :rate-limited}
      (and retry-at (< now-ms retry-at)) {:status :cooldown :retry-at-ms retry-at}
      pending? {:status :pending}
      covered? {:status :completed}
      ;; An expired quota reply ended that attempt. A later operator may make
      ;; one new manual request; this law never schedules a retry itself.
      (and request (or (nil? limit-at)
                       (<= limit-at (or (instant-ms (:created_at request)) 0)))) {:status :pending}
      :else {:status :request})))

(defn required-reviewers-for
  "Repository-specific reviewer requirements cannot be removed by a CLI flag."
  [defaults repo requested]
  (let [repo-name (last (str/split repo #"/"))]
    (into (into (:review/required defaults) (or requested #{}))
          (get-in defaults [:review/by-repo-name repo-name] #{}))))

(defn unsettled-blockers
  "Blocking threads not fixed. Only `Fixed` clears a P0/P1: deferring,
   rejecting or calling it `Handled` does not. A disputed blocker stays open
   until the user adjudicates it."
  [threads]
  (filter (fn [{:keys [severity resolution]}]
            (and (blocking? severity) (not= :fixed resolution)))
          threads))

;; --- review state ---------------------------------------------------------

(defn coderabbit-state
  "From PR check rows [{:name :state :description}] derive CodeRabbit's state:
   :absent :pending :completed :rate-limited :skipped :failed."
  [checks]
  (if-let [{:keys [state description]} (first (filter #(= "coderabbit" (str/lower-case (str (:name %)))) checks))]
    (let [d (str/lower-case (str description))
          s (str/lower-case (str state))]
      (cond
        (re-find #"rate limit" d) :rate-limited
        (re-find #"skip" d) :skipped
        (#{"pending" "queued" "in_progress"} s) :pending
        (re-find #"in progress" d) :pending
        (#{"skipped" "skipping" "cancelled"} s) :skipped
        (#{"success" "pass"} s) :completed
        :else :failed))
    :absent))

(defn check-summary
  "Counts of non-CodeRabbit checks by bucket."
  [checks]
  (->> checks
       (remove #(re-find #"(?i)coderabbit" (str (:name %))))
       (map (fn [{:keys [state]}]
              (case (str/lower-case (str state))
                ("pass" "success" "neutral") :pass
                ("skipping" "skipped" "cancelled") :skip
                ("pending" "queued" "in_progress") :pending
                :fail)))
       frequencies))

;; --- merge gate -----------------------------------------------------------

(defn latest-checks
  "Select the latest run of each context/workflow on this head. Requiredness
   survives reruns; ambiguous ordering fails closed by retaining all rows."
  [head checks]
  (mapcat
   (fn [[_ rows]]
     (let [required? (boolean (some :required? rows))
           current (filter #(or (nil? (:headSha %)) (= head (:headSha %))) rows)
           stamp #(instant-ms (:startedAt %))
           stamps (map stamp current)
           picked (cond
                    (empty? current) [(assoc (first rows) :state "PENDING")]
                    (= 1 (count current)) current
                    (and (every? some? stamps) (= (count stamps) (count (set stamps))))
                    [(last (sort-by stamp current))]
                    :else current)]
       (map #(assoc % :required? required?) picked)))
   (group-by (juxt :name :workflow) checks)))

(defn merge-gate
  "One positive exact-head hosted review is the default quorum. Mandatory
   reviewer overrides remain all-of; all deterministic checks and findings
   remain obligations. Observed coverage is never approval."
  [{:keys [threads checks review-bodies-unanswered head snapshot-head
           required-reviewers approved-heads incomplete? approval-quorum]
    :or {approval-quorum 1}}]
  (let [checks (latest-checks head checks)
        cr (coderabbit-state checks)
        mandatory (or required-reviewers #{})
        approved (set (for [[p heads] approved-heads
                            :when (and (eligible-reviewers p) (contains? (set heads) head))] p))
        missing (sort (remove approved mandatory))
        obligations (filter #(or (:required? %) (nil? (reviewer-check %))
                                 (mandatory (reviewer-check %))) checks)
        sums (check-summary obligations)
        blockers (unsettled-blockers threads)
        unsettled (remove :settled? threads)
        contested (filter :contested? threads)
        unresolved (remove :resolved? threads)
        valid-quorum? (and (integer? approval-quorum) (<= 1 approval-quorum (count eligible-reviewers)))
        reasons (cond-> []
                  (not valid-quorum?)
                  (conj "Invalid approval quorum")
                  (not (valid-head? head)) (conj "Missing or invalid exact PR head")
                  (and snapshot-head (not= head snapshot-head)) (conj "PR head changed while collecting evidence")
                  incomplete? (conj "Review data was truncated; refusing a partial view")
                  (and valid-quorum? (< (count approved) approval-quorum)) (conj "No trusted exact-head APPROVED review meets the quorum")
                  (seq missing) (conj (str "Missing exact-head approval from mandatory reviewers: " (str/join ", " missing)))
                  (some #(and (reviewer-check %) (not= :completed (coderabbit-state [(assoc % :name "CodeRabbit")]))) obligations)
                  (conj "A required reviewer/check is pending, skipped, failed or rate-limited")
                  (pos? (get sums :fail 0)) (conj (str (get sums :fail) " failing deterministic/required check(s)"))
                  (pos? (get sums :pending 0)) (conj (str (get sums :pending) " pending deterministic/required check(s)"))
                  (some #(and (:required? %) (#{"skipped" "skipping" "cancelled"} (str/lower-case (str (:state %))))) checks)
                  (conj "A required check was skipped or cancelled")
                  (seq blockers) (conj (str (count blockers) " P0/P1 thread(s) not fixed"))
                  (seq contested) (conj (str (count contested) " disputed settlement(s)"))
                  (seq unsettled) (conj (str (count unsettled) " thread(s) without a settlement reply"))
                  (seq unresolved) (conj (str (count unresolved) " unresolved thread(s)"))
                  (pos? (or review-bodies-unanswered 0)) (conj (str review-bodies-unanswered " unanswered review summary item(s)")))]
    {:pass? (empty? reasons) :head head :reasons reasons :coderabbit cr :checks sums
     :approving-reviewers approved :approval-quorum approval-quorum}))

(def default-max-loops 5)

(defn loop-verdict
  "rounds: completed review rounds; open-blockers: count of P0/P1 still open."
  [{:keys [rounds open-blockers max-loops] :or {max-loops default-max-loops}}]
  (cond
    (zero? open-blockers) :converged
    (>= rounds max-loops) :escalate
    :else :iterate))
