(ns pr-flow.law
  "Pure laws for the PR flow: severity, thread classification, review state,
   and the merge gate. No I/O; the CLI in ../pr.cljs feeds it GitHub data
   already decoded into Clojure maps."
  (:require [clojure.string :as str]))

;; --- severity -------------------------------------------------------------

(def severity-rank
  "Lower is more severe. P0/P1 cannot be deferred before merge."
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
  "P0/P1 require a fix or an independently corroborated rejection."
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
  #"(?im)(?:^|[.!]\s*)(?:✅\s*)?review thread resolved[.!]?[ \t]*$|(?:^|[.!]\s*)verified(?: the fix)?[.!]?[ \t]*$|(?:^|[.!]\s*)this addresses (?:the|my) (?:finding|issue|comment)[.!]?[ \t]*$")

(declare verdict-prose valid-head? default-reviewer-identities)

(defn- remove-matches [text pattern]
  ;; SCI/CLJS string replacement can discard inline regex flags. Match with
  ;; re-find, then remove the literal match, as verdict-prose does for fences.
  (loop [text text]
    (if-let [match (re-find pattern text)]
      (recur (str/replace-first text (if (string? match) match (first match)) ""))
      text)))

(defn reviewer-prose
  "Evaluate the actual reply, excluding quoted/fenced examples and known
   CodeRabbit analysis, learning and generated share blocks."
  [body]
  (some-> (str body)
          (remove-matches #"(?s)<!-- This is an auto-generated comment: tweet message by coderabbit\.ai -->.*?<!-- end of auto-generated comment: tweet message by coderabbit\.ai -->")
          (remove-matches #"(?s)<details>\s*<summary>(?:🧩 Analysis chain|✏️ Learnings added)</summary>.*?</details>")
          (str/replace #"<blockquote>[\s\S]*?</blockquote>" "")
          (remove-matches #"(?m)^[ \t]*>[^\n]*(?:\n|$)")
          verdict-prose
          (str/replace #"<!--[\s\S]*?-->" "")
          (remove-matches #"(?m)^_You are interacting with an AI system\._[ \t]*$|^[ \t]*---[ \t]*$")
          (str/replace #"\[([^\]]*)\]\([^\s)]*\)" "$1")))

(defn- confirmed? [body]
  (let [prose (reviewer-prose body)]
    (boolean
     (and prose (re-find confirmation prose)
          ;; A positive quote/acknowledgement cannot erase an actual negative
          ;; verdict in the same reply. Ambiguous prose remains contested.
          (not (re-find #"(?i)still (?:reproduces|fails|broken|wrong)|not (?:fixed|resolved|addressed)|does(?: not|n't) (?:hold|address|fix|resolve)|(?:cannot|can't|unable to) (?:verify|confirm)|resolved incorrectly|(?:fix|settlement) (?:is )?(?:incorrect|rejected)" prose))))))

(defn- comment-login [comment]
  (str/lower-case (str (or (:author comment) (get-in comment [:user :login])))))

(defn- rejection-prose [body]
  ;; Keep evidence links, but never accept quoted examples as live proposals.
  (some-> (str body)
          (str/replace #"<blockquote>[\s\S]*?</blockquote>" "")
          (remove-matches #"(?m)^[ \t]*>[^\n]*(?:\n|$)")
          verdict-prose
          (str/replace #"<!--[\s\S]*?-->" "")
          str/trim))

(defn- evidence-reference? [evidence]
  (boolean (re-find #"https?://[^\s]+|(?:[a-zA-Z0-9_.-]+/)+[a-zA-Z0-9_.-]+|[a-zA-Z0-9_.-]+\.(?:cljc?|cljs|md|edn|json|ya?ml|jsx?|tsx?|py|sh)(?::[0-9]+)?" (str evidence))))

(defn- details [reason evidence]
  (let [reason (str/trim (str reason)) evidence (str/trim (str evidence))]
    (when (and (not (str/blank? reason)) (evidence-reference? evidence))
      {:reason reason :evidence evidence})))

(defn- rejection-details [body]
  (when-let [prose (rejection-prose body)]
    (let [reasons (map second (re-seq #"(?m)^Reason:[ \t]*([^\n]*)$" prose))
          evidence (map second (re-seq #"(?m)^Evidence:[ \t]*([^\n]*)$" prose))]
      (when (and (= 1 (count reasons)) (= 1 (count evidence)))
        (details (first reasons) (first evidence))))))

(defn- native-agent [comment identities]
  ;; Rejection corroboration may use a configured OpenCode/Claude App without
  ;; adding that App to the distinct hosted merge approval quorum.
  (let [login (comment-login comment)
        user (:user comment)
        matches (for [[provider logins] identities
                      :when (contains? (set logins) login)] provider)]
    (when (and (= "Bot" (:type user)) (= login (str/lower-case (str (:login user))))
               (= 1 (count matches)) (not= "coderabbit" (first matches))
               (not (contains? (set (get identities "coderabbit")) login)))
      (first matches))))

(defn- rejection-evidence
  [head identities pr-author comments settlement-index final-details scope]
  (when (and (valid-head? head) (not (str/blank? pr-author)) final-details)
    (let [settler (comment-login (nth comments settlement-index))
          marker (fn [kind] (str "Rejection " kind " for " head ":" (when scope (str " " scope))))
          marker? (fn [kind c] (= (marker kind) (first (str/split-lines (or (rejection-prose (:body c)) "")))))
          proposal-index (last (keep-indexed
                               (fn [i c] (when (and (= settler (comment-login c))
                                                    (not= false (:authorized? c)) (marker? "proposal" c)) i))
                               (subvec comments 0 settlement-index)))
          proposal (when proposal-index (nth comments proposal-index))
          agreement-index (when proposal-index
                            (last (for [i (range (inc proposal-index) settlement-index)
                                        :let [c (nth comments i) login (comment-login c)]
                                        :when (and (not= settler login) (not= (str/lower-case pr-author) login)
                                                   (native-agent c identities) (marker? "agreement" c)
                                                   (rejection-details (:body c))
                                                   (not (str/blank? (or (:url c) (:html_url c)))))] i)))
          agreement (when agreement-index (nth comments agreement-index))
          disputed? (when agreement-index
                      (some (fn [c]
                              (let [prose (reviewer-prose (:body c))]
                                (and (not= settler (comment-login c))
                                     (or (nil? scope) (every? #(str/includes? (str (:body c)) %)
                                                             (str/split scope #" ")))
                                     (not (marker? "agreement" c))
                                     (or (nil? prose) (and (not (str/blank? prose)) (not (confirmed? (:body c))))))))
                            (subvec comments (inc agreement-index))))]
      (when (and (not (str/blank? settler)) agreement (not disputed?)
                 (= final-details (rejection-details (:body proposal))))
        {:reviewer (native-agent agreement identities) :url (or (:url agreement) (:html_url agreement))}))))

(defn classify-thread
  "thread: {:id :resolved? :outdated? :path :line
             :comments [{:author :body :url :created-at}]}
   Returns the thread with :severity, :reviewer, :resolution, :settled?, and
   :contested? — true when a reviewer replied after the last settlement
   without confirming it. A contested thread blocks the gate until it is
   settled again, even if GitHub shows it resolved."
  [{:keys [comments head identities pr-author] :as thread}]
  (let [opener (first comments)
        replies (vec (rest comments))
        settle-idx (->> (map-indexed vector replies)
                        (filter (fn [[_ c]] (and (not= (:author opener) (:author c))
                                                (not (bot? (:author c)))
                                                (not= false (:authorized? c))
                                                (resolution-of (:body c)))))
                        (map first) last)
        settlement (when settle-idx (resolution-of (:body (nth replies settle-idx))))
        later (when settle-idx (subvec replies (inc settle-idx)))
        settler (when settle-idx (:author (nth replies settle-idx)))
        contested? (boolean
                    (some (fn [c]
                            (let [prose (reviewer-prose (:body c))]
                              (and (not= settler (:author c))
                                   (or (nil? prose)
                                       (and (not (str/blank? prose)) (not (confirmed? (:body c)))))))) later))
        rejection (when (and (= :rejected settlement) (not contested?))
                    (let [reply (nth replies settle-idx)]
                      (when (str/starts-with? (or (rejection-prose (:body reply)) "") "Rejected:")
                        (rejection-evidence head (or identities default-reviewer-identities) pr-author
                                            (vec comments) (inc settle-idx) (rejection-details (:body reply)) nil))))]
    (assoc thread
           :reviewer (:author opener)
           :severity (severity (:body opener))
           :resolution settlement
           :settled? (and (some? settlement) (not contested?) (or (not= :rejected settlement) (some? rejection)))
           :contested? contested?
           :rejection-approved? (boolean rejection)
           :rejection-reviewer (:reviewer rejection)
           :rejection-url (:url rejection))))

(defn- legacy-body-findings [body]
  ;; Old CodeRabbit items put the banner immediately inside a file's
  ;; summary/blockquote, with the ID after nested generated prompts. Remove
  ;; fenced examples first, including the outer Markdown quote used for
  ;; outside-diff sections. An unfinished fence cannot justify a downgrade.
  (let [text (remove-matches (str body)
                            #"(?ms)^([ \t]*(?:>[ \t]?)*)(`{3,}|~{3,})[^\n]*\n.*?^\1\2[ \t]*$")]
    (when-not (re-find #"(?m)^[ \t]*(?:>[ \t]?)*(?:`{3,}|~{3,})" text)
      (mapv
       (fn [[_ _prefix start end banner prose id]]
         (let [number #( #?(:clj Long/parseLong :cljs js/parseInt) %)
               valid-range? (or (nil? end) (<= (number start) (number end)))]
           {:id id
            :severity (if valid-range?
                        (get {"🔴 Critical" :p0 "🟠 Major" :p1
                              "🟡 Minor" :p2 "🔵 Trivial" :p3} banner :p1)
                        :p1)
            :title (title-of prose)}))
       ;; Same quote prefix on the header, banner, ID and closing boundary;
       ;; never cross a sibling/nested blockquote or another finding marker.
       (re-seq #"(?m)^([ \t]*(?:>[ \t]?)*)(?:<summary>[^<\n]+</summary><blockquote>)[ \t]*\n(?:\1[ \t]*\n)*\1`([1-9][0-9]{0,8})(?:-([1-9][0-9]{0,8}))?`: _[^_\n]+_ \| _([^_\n]+)_ \| _[^_\n]+_[ \t]*\n((?:(?!</?blockquote>|<!-- cr-comment:v1:)[\s\S])*?)^\1<!-- cr-comment:v1:([a-z0-9]+) -->[ \t]*\n(?:(?!</?blockquote>|<!-- cr-comment:v1:)[\s\S])*?^\1</blockquote></details>" text)))))

(defn review-body-findings
  "Extract each outside-diff or nitpick finding and its stable CodeRabbit ID."
  [body]
  (let [modern (->> (re-seq #"<summary><em>([^<]*)</em> · ([\s\S]*?) · <code>[^<]*</code></summary>[\s\S]*?<!-- cr-comment:v1:([a-z0-9]+) -->" (str body))
                   (mapv (fn [[_ banner title id]]
                           {:id id :severity (severity banner) :title title})))
        modern-ids (set (map :id modern))
        identified (into modern (remove #(modern-ids (:id %)) (legacy-body-findings body)))
        known (set (map :id identified))
        other (for [[_ id] (re-seq #"<!-- cr-comment:v1:([a-z0-9]+) -->" (str body))
                    :when (not (known id))]
                {:id id :severity :p1 :title "Unclassified review-body finding; fix before merge"})]
    (into identified other)))

(defn- body-finding? [body]
  (boolean
   (or (re-find #"(?i)(?:nitpick comments|outside diff range comments|duplicate comments)\s*\([1-9][0-9]*\)" (str body))
       (re-find #"(?im)^Confirmed findings:\s*\n\s*[1-9][0-9]*\." (str body)))))

(defn review-findings
  "Stable CR item IDs remain individual findings. Body-only change requests
   and legacy positive-count sections use one conservative P1 whole-body ID,
   which has an explicit Fixed settlement path rather than a phantom item."
  [review]
  (let [items (review-body-findings (:body review))
        body (:body review)]
    (if (seq items) items
        (if (or (= "CHANGES_REQUESTED" (:state review))
                (body-finding? body))
          [{:id (str (:id review)) :kind :review-body :severity :p1 :title "Whole review body requires a verified fix"}]
          []))))

(declare incomplete-review-reason)

(defn outstanding-review-bodies
  "Discover findings from every provider and human. Body-only change requests
   stay active across pushes until settled or superseded by the same author's
   later approval/dismissal on that commit or the current head. Historical
   identified and explicitly listed findings retain their settlement IDs."
  [head reviews]
  (let [superseded? (fn [r]
                      (some #(and (= (get-in r [:user :login]) (get-in % [:user :login]))
                                  (#{"APPROVED" "DISMISSED"} (:state %))
                                  (or (= "DISMISSED" (:state %))
                                      (nil? (incomplete-review-reason (:body %))))
                                  (or (= head (:commit_id %)) (= (:commit_id r) (:commit_id %)))
                                  (pos? (compare [(:submitted_at %) (:id %)] [(:submitted_at r) (:id r)]))) reviews))]
    (filter #(or (seq (review-body-findings (:body %)))
                 (body-finding? (:body %))
                 (and (= "CHANGES_REQUESTED" (:state %)) (not (superseded? %)))) reviews)))

(defn- inline-rejection-details [text]
  (when-let [[_ reason evidence] (re-find #"^:[ \t]*Reason:[ \t]*(.+?);[ \t]*Evidence:[ \t]*(.+)$" text)]
    (details reason evidence)))

(defn- answered-finding? [review comments {:keys [id kind severity]} context]
  (let [opener (get-in review [:user :login])]
    (some (fn [[settlement-index {:keys [body created_at user authorized?]}]]
          (and (not (str/blank? opener))
               (not (str/blank? (:login user)))
               (not= (str/lower-case opener) (str/lower-case (:login user)))
               (not= false authorized?)
               (not (bot? (:login user)))
               created_at
               (not (neg? (compare created_at (:submitted_at review))))
               (re-find (re-pattern (str "(?i)review-id:" (:id review) "\\b")) (str body))
               (some (fn [[_ verb marker-type marker tail]]
                       (and (= marker-type (if (= :review-body kind) "review-body" "cr-comment:v1"))
                            (= marker id)
                            (case (str/lower-case verb)
                              "fixed" true
                              "handled" (not (blocking? severity))
                              "deferred" (and (not (blocking? severity))
                                              (integer? (:rounds context)) (> (:rounds context) 5))
                              "rejected" (some? (rejection-evidence (:head context)
                                                                   (or (:identities context) default-reviewer-identities)
                                                                   (:pr-author context) (vec comments) settlement-index
                                                                   (inline-rejection-details tail)
                                                                   (str "review-id:" (:id review) " " marker-type ":" marker)))
                              false)))
                     (re-seq #"(?mi)^\s*[-*]\s*(Fixed|Deferred|Rejected|Handled)\b[^\n]*?(cr-comment:v1|review-body):([a-z0-9]+)\b([^\n]*)" (str body)))))
        (map-indexed vector comments))))

(defn unanswered-review-count
  "A flagged review clears only when each identified item has an authorized,
   later settlement from a known author other than the opener. P0/P1 items
   require Fixed or independent head/item-bound rejection agreement. Context
   is required for rejection and post-fifth-round deferral; absence fails closed."
  ([reviews comments] (unanswered-review-count reviews comments {}))
  ([reviews comments context]
   (let [comments (vec (sort-by :created_at comments))]
     (reduce +
             (for [review reviews
                   :let [findings (review-findings review)]]
               (count (remove #(answered-finding? review comments % context) findings)))))))

(defn- stage-reviews
  [reviews stage-comments stage]
  (let [markers (sort-by :created_at stage-comments)
        latest (last markers)]
    (if (and latest (not= stage (:stage latest)))
      []
      (let [current (reverse (take-while #(= stage (:stage %)) (reverse markers)))
            start (:created_at (first current))]
        (filter #(or (nil? start)
                     (not (neg? (compare (:submitted_at %) start)))) reviews)))))

(defn stage-review-rounds
  "A completed round includes one full review from every participant at one
   head. Trusted round markers bind modern cohorts; legacy same-head passes
   are paired by each provider's chronological pass index. Three-arity is a
   compatibility timestamp counter; production supplies the participant set."
  ([reviews stage-comments stage] (count (stage-reviews reviews stage-comments stage)))
  ([reviews stage-comments stage participants]
   (if-not (seq participants) 0
     (let [reviews (filter #(and (contains? (set participants) (:reviewer %))
                                 (or (nil? (:stage %)) (= stage (:stage %)))
                                 (valid-head? (:commit_id %)))
                           (stage-reviews reviews stage-comments stage))
           explicit (filter :round-id reviews)
           explicit-complete (for [[[round-id _] group] (group-by (juxt :round-id :commit_id) explicit)
                                   :when (every? (set (map :reviewer group)) participants)] round-id)
           legacy (remove :round-id reviews)
           indexed (mapcat (fn [[_ group]]
                             (map-indexed #(assoc %2 :cohort-index %1)
                                          (sort-by (juxt :submitted_at :id) group)))
                           (group-by (juxt :commit_id :reviewer) legacy))
           legacy-complete (filter (fn [[_ group]] (every? (set (map :reviewer group)) participants))
                                   (group-by (juxt :commit_id :cohort-index) indexed))]
       (+ (count (set explicit-complete)) (count legacy-complete))))))

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
  (loop [prose (str body)]
    (if-let [[block] (re-find #"(?ms)^[ \t]*(`{3,}|~{3,})[^\n]*\n.*?^[ \t]*\1[ \t]*$" prose)]
      ;; Replace the matched literal, preserving multiline/dotall matching
      ;; across JVM and SCI/CLJS string implementations.
      (recur (str/replace-first prose block ""))
      (when-not (re-find #"(?m)^[ \t]*(?:`{3,}|~{3,})" prose) prose))))

(defn incomplete-review-reason
  "Commit binding is distinct from completed review scope. Reject an actual
   admission of unfinished/omitted input, including formal approvals. Quotes
   and generated examples are not the current verdict; truncation alone does
   not disqualify a reviewer who retrieved and reviewed the omitted input."
  [body]
  (let [prose (reviewer-prose body)]
    (cond
      (nil? prose) :ambiguous-prose
      (re-find #"(?i)\breview (?:is |was |remains )?(?:incomplete|rate limited)\b|\b(?:partial|incomplete) review\b|\b(?:unable to|could not|couldn't|cannot|can't) complete (?:the )?review\b" prose)
      :incomplete-review
      (or (re-find #"(?im)(?:^|[.;]\s*)(?:some |the |these )?unreviewed (?:files|changes|input) (?:still )?remain\b" prose)
          (and (re-find #"(?i)\btruncat(?:ed|ion)\b" prose)
               (re-find #"(?i)\b(?:rather than|instead of) (?:being )?(?:fully |exhaustively )?(?:read|reviewed)\b|\b(?:tail|omitted (?:files|changes|input)) (?:was |were |is |are )?not (?:read|reviewed)\b" prose))
          (some (fn [[_ read total]]
                  (neg? (compare #?(:clj (Long/parseLong read) :cljs (js/parseInt read 10))
                                 #?(:clj (Long/parseLong total) :cljs (js/parseInt total 10)))))
                (re-seq #"(?i)\breviewed (?:only )?([0-9]+) of ([0-9]+) (?:changed )?files\b" prose)))
      :unreviewed-input)))

(defn review-evidence
  "Trust completed explicit verdicts as well as formal approvals, while
   retaining their distinct channels and immutable commit binding. Formal
   APPROVED state cannot override an admitted incomplete review scope."
  [head reviews comments identities]
  (let [trusted (->> reviews
                     (keep (fn [r]
                             (when-let [p (trusted-reviewer r identities)]
                               (when (and (valid-head? head) (= head (:commit_id r))
                                          (string? (:submitted_at r)))
                                 (assoc r :reviewer p)))))
                     (sort-by (juxt :submitted_at :id)))
        review-verdicts (keep (fn [r]
                               (let [incomplete (incomplete-review-reason (:body r))]
                                 (cond
                                 (#{"CHANGES_REQUESTED" "DISMISSED"} (:state r))
                                 (assoc r :positive? false :channel :github-review)
                                 (and incomplete (#{"APPROVED" "COMMENTED"} (:state r)))
                                 (assoc r :positive? false :channel :incomplete-review :incomplete-reason incomplete)
                                 (= "APPROVED" (:state r))
                                 (assoc r :positive? true :channel :github-approved)
                                 (and (= "COMMENTED" (:state r))
                                      (re-find #"(?im)^\s*(?:\*\*)?(?:Confirmed findings:\s*none|No confirmed findings|No issues found)(?:\*\*)?\s*(?:[.—-]|$)" (or (reviewer-prose (:body r)) "")))
                                 (assoc r :positive? true :channel :explicit-review-verdict)))) trusted)
        comment-verdicts
        (keep (fn [c]
                (let [p (trusted-reviewer c identities)
                      body (str (:body c))
                      incomplete (incomplete-review-reason body)
                      recent (second (re-find #"(?s)<!--\s*recent_review_start\s*-->(.*?)<!--\s*recent_review_end\s*-->" body))
                      passing? (case p
                                 "coderabbit" (and ((coderabbit-covered-heads body) head)
                                                   recent (str/includes? recent head)
                                                   (re-find #"(?m)^No actionable comments were generated in the recent review\." (or (reviewer-prose recent) "")))
                                 "codex" (and (= head (:resolved-commit-id c))
                                              (re-find #"^Codex Review: Didn't find any major issues\." (or (reviewer-prose body) "")))
                                 false)]
                  (when (and (valid-head? head) passing?
                             (string? (or (:updated_at c) (:created_at c))))
                    (cond-> (assoc c :reviewer p :positive? (nil? incomplete) :channel :explicit-issue-verdict
                                   :submitted_at (or (:updated_at c) (:created_at c)))
                      incomplete (assoc :incomplete-reason incomplete))))) comments)
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
     :incomplete-evidence (into {} (for [[p r] decisive :when (:incomplete-reason r)]
                                    [p {:head head :channel (:channel r) :id (:id r) :reason (:incomplete-reason r)}]))
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
  (when-let [[_ duration] (re-find #"(?i)(?:wait|retry(?: again)?(?: in| after)?|try again in|cooldown:?|next included review (?:will be )?available in)[^0-9]*([^\n.<]+)" (str body))]
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

(declare latest-checks coderabbit-state)

(defn full-review?
  "Formal verdicts and recognizable full-review outputs are completions;
   requests, acknowledgements, quoted examples and incomplete scope are not."
  [review identities]
  (let [prose (reviewer-prose (:body review))
        provider (trusted-reviewer review identities)]
    (and provider (nil? (incomplete-review-reason (:body review)))
         (not (re-find #"(?im)^(?:#+\s*)?Review (?:queued|requested|triggered|in progress)[.!]?\s*$|^(?:Acknowledged|Working on it)[.!]?\s*$" (or prose "")))
         (or (#{"APPROVED" "CHANGES_REQUESTED"} (:state review))
             (and (= "COMMENTED" (:state review))
                  (if (= "coderabbit" provider)
                    (re-find #"(?i)actionable comments posted:\s*[0-9]+|no actionable comments (?:were generated|posted|found)" (or prose ""))
                    (re-find #"(?im)^Full review finished\.[ \t]*$|^Review complete(?:d)?[.!]?[ \t]*$|^Confirmed findings:[ \t]*(?:none(?:[.—-]|$)|\n[ \t]*[1-9][0-9]*\.)|^No confirmed findings(?:[.—-]|$)|^No issues found(?:[.—-]|$)|^Here are some automated review suggestions for this pull request" (or prose ""))))))))

(defn completed-review-rounds
  "Combine full REST reviews and native no-findings completion replies.
   Dedupe representations of the same trusted request; a completed request
   counts once even without a REST review. Stage attribution uses completion."
  [reviews comments identities]
  (let [requests (sort-by :created_at (filter :trusted? comments))
        provider-request? (fn [c p]
                            (or (str/includes? (str (:body c)) (str "pr-flow-reviewer:" p " -->"))
                                (and (= "coderabbit" p)
                                     (not (str/includes? (str (:body c)) "pr-flow-reviewer:"))
                                     (re-find #"@coderabbitai (?:full )?review" (str (:body c))))))
        issue-rounds (keep (fn [c]
                             (let [provider (trusted-reviewer c identities)
                                   completed (assoc c :submitted_at (or (:updated_at c) (:created_at c)) :round-source :issue)]
                               (case provider
                                 "coderabbit"
                                 (let [coverage (coderabbit-covered-heads (:body c))]
                                   (when (or (re-find #"(?m)^Full review finished\.$" (str (:body c)))
                                             (= 1 (count coverage)))
                                     (cond-> completed (= 1 (count coverage)) (assoc :commit_id (first coverage)))))
                                 "codex"
                                 (when (and (valid-head? (:resolved-commit-id c))
                                            (re-find #"^Codex Review: Didn't find any major issues\." (or (reviewer-prose (:body c)) "")))
                                   (assoc completed :commit_id (:resolved-commit-id c)))
                                 nil))) comments)
        rounds (keep (fn [r]
                       (when-let [p (trusted-reviewer r identities)]
                         (let [at (:submitted_at r)
                               request (last (filter #(and (provider-request? % p)
                                                           (string? at) (string? (:created_at %))
                                                           (not (pos? (compare (:created_at %) at)))
                                                           (or (nil? (:commit_id r))
                                                               (str/includes? (str (:body %)) (str "pr-flow-review:" (:commit_id r) " -->")))) requests))]
                           (when (and (string? at) (nil? (incomplete-review-reason (:body r)))
                                      (or (not= :issue (:round-source r)) request)
                                      (or (= :issue (:round-source r)) (full-review? r identities)))
                             (cond-> (assoc r :reviewer p
                                            :round-key (if request [p :request (:id request) (:created_at request)]
                                                            [p :review (:id r)]))
                               request (assoc :round-id (some-> (second (re-find #"<!--\s*pr-flow-round:([1-9][0-9]*)\s*-->" (str (:body request))))
                                                              #?(:clj Long/parseLong :cljs js/parseInt))
                                              :stage (second (re-find #"<!--\s*pr-flow-stage:(planning|code)\s*-->" (str (:body request)))))
                               (and request (nil? (:commit_id r)))
                               (assoc :commit_id (second (re-find #"<!--\s*pr-flow-review:([0-9a-f]{40})\s*-->" (str (:body request)))))))))) (concat reviews issue-rounds))]
    (mapv #(first (sort-by :submitted_at %)) (vals (group-by :round-key rounds)))))

(defn request-verdict
  "Manual requests reuse pending work and observe actual reviewer cooldowns.
   Completed review rounds are a soft minimum, never a request cap."
  [{:keys [head reviewer comments checks now-ms identities round]
    :or {identities default-reviewer-identities}}]
  (let [checks (latest-checks head checks)
        requests (filter #(and (:trusted? %)
                               (str/includes? (str (:body %)) (str "pr-flow-review:" head " -->"))
                               (or (nil? round) (not (str/includes? (str (:body %)) "pr-flow-round:"))
                                   (str/includes? (str (:body %)) (str "pr-flow-round:" round " -->")))
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
        covered? (some #(and (= "coderabbit" reviewer) (or (nil? round) request)
                             ((coderabbit-covered-heads (:body %)) head)
                             (or (nil? request)
                                 (not (neg? (compare (or (:updated_at %) (:created_at %) "") (:created_at request)))))) replies)
        legacy-completed? (and round request covered? (not (str/includes? (str (:body request)) "pr-flow-round:")))
        pending? (some #(and (= reviewer (reviewer-check %))
                            (#{"PENDING" "QUEUED" "IN_PROGRESS"} (str/upper-case (str (:state %))))) checks)
        ended? (some #(and request (= head (:headSha %)) (= reviewer (reviewer-check %))
                           (#{:failed :skipped} (coderabbit-state [(assoc % :name "CodeRabbit")]))
                           (instant-ms (:completedAt %)) (instant-ms (:created_at request))
                           (> (instant-ms (:completedAt %)) (instant-ms (:created_at request)))) checks)]
    (cond
      (or (not (valid-head? head)) (not (eligible-reviewers reviewer))) {:status :invalid}
      (and limit (or (nil? retry-at) (nil? now-ms))) {:status :rate-limited}
      (and retry-at (< now-ms retry-at)) {:status :cooldown :retry-at-ms retry-at}
      pending? {:status :pending}
      legacy-completed? {:status :request}
      covered? {:status :completed}
      ended? {:status :request}
      ;; An expired quota reply ended that attempt. A later operator may make
      ;; one new manual request; this law never schedules a retry itself.
      (and request (or (nil? limit-at)
                       (<= limit-at (or (instant-ms (:created_at request)) 0)))) {:status :pending}
      :else {:status :request})))

(defn required-reviewers-for
  "Repository-specific reviewer requirements cannot be removed by a CLI flag."
  [defaults repo requested]
  (let [repo-name (str/lower-case (last (str/split repo #"/")))]
    (into (into (:review/required defaults) (or requested #{}))
          (get-in defaults [:review/by-repo-name repo-name] #{}))))

(defn unsettled-blockers
  "P0/P1 require a fix or a detailed independently corroborated rejection.
   Deferral and Handled never clear a blocker."
  [threads]
  (filter (fn [{:keys [severity resolution rejection-approved? settled?]}]
            (and (blocking? severity) (not= :fixed resolution)
                 (not (and (= :rejected resolution) rejection-approved? settled?))))
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

(def default-min-review-rounds 5)

(defn loop-verdict
  "Completed cohort rounds are a soft minimum. Findings always require
   another iteration; unanimous current-head approval permits an early exit."
  [{:keys [rounds open-findings unanimous-approval? min-review-rounds]
    :or {min-review-rounds default-min-review-rounds}}]
  (if (and (integer? rounds) (not (neg? rounds))
           (integer? open-findings) (zero? open-findings)
           (integer? min-review-rounds) (pos? min-review-rounds)
           (or (>= rounds min-review-rounds) (and (pos? rounds) (true? unanimous-approval?))))
    :converged :iterate))

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
           required-reviewers approved-heads incomplete? approval-quorum
           rounds review-participants min-review-rounds]
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
        early-deferrals (when (or (not (integer? rounds)) (<= rounds default-min-review-rounds))
                          (filter #(= :deferred (:resolution %)) threads))
        unanimous? (and (seq review-participants) (every? approved review-participants))
        open-findings (+ (count (filter #(or (not (:settled? %)) (not (:resolved? %))
                                             (:contested? %) (some #{%} blockers)
                                             (some #{%} early-deferrals)) threads))
                         (or review-bodies-unanswered 0))
        loop-state (loop-verdict {:rounds rounds :open-findings open-findings
                                 :unanimous-approval? (boolean unanimous?)
                                 :min-review-rounds (or min-review-rounds default-min-review-rounds)})
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
                  (seq early-deferrals) (conj "Verified findings cannot be deferred during the first five review rounds")
                  (seq contested) (conj (str (count contested) " disputed settlement(s)"))
                  (seq unsettled) (conj (str (count unsettled) " thread(s) without a settlement reply"))
                  (seq unresolved) (conj (str (count unresolved) " unresolved thread(s)"))
                  (pos? (or review-bodies-unanswered 0)) (conj (str review-bodies-unanswered " unanswered review summary item(s)"))
                  (not= :converged loop-state) (conj "Review loop requires five completed rounds or unanimous current-head approval, with every finding settled"))]
    {:pass? (empty? reasons) :head head :reasons reasons :coderabbit cr :checks sums
     :approving-reviewers approved :approval-quorum approval-quorum
     :review-rounds rounds :unanimous-approval? (boolean unanimous?) :loop-verdict loop-state}))
