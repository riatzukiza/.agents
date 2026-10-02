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
  (if-let [{:keys [state description]} (first (filter #(re-find #"(?i)coderabbit" (str (:name %))) checks))]
    (let [d (str/lower-case (str description))
          s (str/lower-case (str state))]
      (cond
        (re-find #"rate limit" d) :rate-limited
        (re-find #"skip" d) :skipped
        (#{"pending" "queued" "in_progress"} s) :pending
        (re-find #"in progress" d) :pending
        (#{"fail" "failure" "error"} s) :failed
        :else :completed))
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

(defn merge-gate
  "Evaluate whether a PR may be marked ready and set to auto-merge.
   input: {:threads [classified] :checks [...] :review-bodies-unanswered n
           :head sha
           :required-reviewers #{\"coderabbit\" \"codex\" ...}  ; default #{\"coderabbit\"}
           :reviewed-heads {reviewer #{sha}}                ; full review passes per reviewer
           :incomplete? bool}                               ; any paginated connection truncated
   Head checks apply only when :head is given.
   Returns {:pass? bool :reasons [..]}."
  [{:keys [threads checks review-bodies-unanswered head required-reviewers reviewed-heads incomplete?]}]
  (let [cr (coderabbit-state checks)
        sums (check-summary checks)
        required (or (not-empty required-reviewers) #{"coderabbit"})
        uncovered (when head
                    (sort (remove #(contains? (set (get reviewed-heads %)) head) required)))
        blockers (unsettled-blockers threads)
        unsettled (remove :settled? threads)
        contested (filter :contested? threads)
        unresolved (remove :resolved? threads)
        reasons (cond-> []
                  incomplete? (conj "Review data was truncated (more than one page); refusing to judge a partial view")
                  (= cr :pending) (conj "CodeRabbit review still in progress")
                  (= cr :rate-limited) (conj "CodeRabbit rate-limited; re-request review after the cooldown")
                  (= cr :absent) (conj "No CodeRabbit check; request a review first")
                  (= cr :skipped) (conj "CodeRabbit skipped this head (draft, non-default base, or file cap); request a review explicitly")
                  (= cr :failed) (conj "CodeRabbit review failed; request a successful review")
                  (seq uncovered)
                  (conj (str "No full review of head " (subs head 0 (min 7 (count head))) " by: " (str/join ", " uncovered)))
                  (pos? (get sums :fail 0)) (conj (str (get sums :fail) " failing check(s)"))
                  (pos? (get sums :pending 0)) (conj (str (get sums :pending) " pending check(s)"))
                  (some #(and (:required? %) (#{"skipped" "skipping" "cancelled"} (str/lower-case (str (:state %))))) checks)
                  (conj "A required check was skipped or cancelled")
                  (seq blockers) (conj (str (count blockers) " P0/P1 thread(s) not fixed"))
                  (seq contested) (conj (str (count contested) " thread(s) where a reviewer disputed the settlement; reopen and settle again"))
                  (seq unsettled) (conj (str (count unsettled) " thread(s) without a settlement reply"))
                  (seq unresolved) (conj (str (count unresolved) " unresolved thread(s)"))
                  (pos? (or review-bodies-unanswered 0))
                  (conj (str review-bodies-unanswered " review summary item(s) (nitpicks/outside-diff) not answered in a PR comment")))]
    {:pass? (empty? reasons) :coderabbit cr :checks sums :head head :reasons reasons}))

;; --- loop budget ----------------------------------------------------------

(def default-max-loops
  "Review rounds before escalating to the user instead of iterating again."
  5)

(defn loop-verdict
  "rounds: completed review rounds; open-blockers: count of P0/P1 still open."
  [{:keys [rounds open-blockers max-loops] :or {max-loops default-max-loops}}]
  (cond
    (zero? open-blockers) :converged
    (>= rounds max-loops) :escalate
    :else :iterate))
