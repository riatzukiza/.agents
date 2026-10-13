#!/usr/bin/env nbb
;; pr-flow CLI: thin effectful shell over pr-flow.law.
;;
;;   nbb -cp <skill>/scripts <skill>/scripts/pr.cljs <command> <owner/repo> <pr> [...]
;;
;; Commands
;;   status  REPO PR [--reviewers coderabbit,codex]
;;                              gate summary: draft, checks, CodeRabbit, threads by severity
;;   threads REPO PR [--all]    review threads (unresolved by default) with id, severity, settlement
;;   wait    REPO PR [--timeout S] [--interval S]
;;                              poll until CodeRabbit is no longer pending; exit 0 done, 3 rate-limited, 4 timeout
;;   request REPO PR planning|code [--note TEXT] [--reviewer coderabbit|codex]
;;                              post an @coderabbitai review request with the matching brief
;;   settle  REPO PR THREAD-ID BODY
;;                              reply to a thread, then resolve it; BODY must open with
;;                              Fixed|Deferred|Rejected|Handled (or "-" to read stdin)
;;   resolve-informational REPO PR THREAD EXPECTED [--apply]
;;                              fixed-auth native User + canonical independent Bot evidence;
;;                              one resolve POST, no reply, fresh readback/assessment required
;;   flow [STATE]               validate flow.edn; print the states, or one state's skill, exit and next states
;;   gate    REPO PR [--apply] [--method merge|squash|rebase] [--reviewers coderabbit,codex]   (default merge: the user wants merge commits)
;;                              evaluate the merge gate; with --apply and a pass, mark ready + enable auto-merge
;;
;; gh auth: runs gh as-is first; if GitHub answers "Resource not accessible by
;; personal access token" (a fine-grained GH_TOKEN without PR write), it retries
;; once with GH_TOKEN/GITHUB_TOKEN removed so gh falls back to its keyring login.

(ns pr
  (:require ["child_process" :as cp]
            ["fs" :as fs]
            ["path" :as path]
            [clojure.edn :as edn]
            [pr-flow.actionability-history :as actionability-history]
            [pr-flow.actionability :as actionability]
            [pr-flow.positive-thread :as positive-thread]
            [nbb.core :refer [*file*]]
            [clojure.string :as str]
            [pr-flow.flow :as flow]
            [pr-flow.native-context :as native-context]
            [pr-flow.native-context-law :as native-context-law]
            [pr-flow.permissions :as permissions]
            [pr-flow.kimi-native :as kimi-native]
            [pr-flow.law :as law]))

(def here
  "This script's directory, captured while nbb loads the file."
  (path/dirname *file*))

;; --- gh boundary ----------------------------------------------------------

(defn- env-without-tokens []
  (let [env (js/Object.assign #js {} js/process.env)]
    (js-delete env "GH_TOKEN")
    (js-delete env "GITHUB_TOKEN")
    env))

(defn- run-gh
  ([args env] (run-gh args env nil))
  ([args env input]
   (let [r (cp/spawnSync "gh" (clj->js args) #js {:encoding "utf8" :input input :env env :maxBuffer (* 64 1024 1024)})]
     {:exit (.-status r) :out (str (.-stdout r)) :err (str (.-stderr r))})))

(defn- gh-with-input! [args input]
  (let [r (run-gh args js/process.env input)
        r (if (and (not= 0 (:exit r))
                   (re-find #"Resource not accessible by (personal access|integration) token" (:err r)))
            (run-gh args (env-without-tokens) input)
            r)]
    (when-not (= 0 (:exit r))
      (throw (ex-info (str "gh " (str/join " " (take 3 args)) " failed: " (str/trim (:err r))) r)))
    (:out r)))

(defn gh!
  "Run gh; return stdout. Throws with stderr on failure."
  [& args]
  (gh-with-input! args nil))

(defn gh-json [& args]
  (js->clj (js/JSON.parse (apply gh! args)) :keywordize-keys true))

(defn gh-pages [endpoint]
  (mapcat identity (gh-json "api" endpoint "--paginate" "--slurp")))

;; --- reads ----------------------------------------------------------------

(defn- actionability-ledger [] (path/resolve here ".." ".." ".." ".ημ" "receipts.edn"))
(defn- actionability-observations [repo-id]
  ;; Preserve the module-relative complete read and unavailable/empty boundary.
  (try
    (when-not (and (string? repo-id) (not (str/blank? repo-id)))
      (throw (ex-info "Native repository identity unavailable" {})))
    (let [file (actionability-ledger)]
      (actionability-history/observations
        (when (fs/existsSync file) (fs/readFileSync file "utf8")) repo-id))
    (catch :default _ nil)))
(defn- append-actionability-observations! [repo observations]
  (when (seq observations)
    (let [file (actionability-ledger)]
      (when-not (fs/existsSync file)
        (throw (ex-info "Canonical actionability receipt ledger unavailable; no disposition admitted" {:path file})))
      (doseq [o observations]
        (fs/appendFileSync file
          (str (pr-str {:ts (.toISOString (js/Date.)) :kind :observation :repo (str/lower-case repo)
                        :origin "pr-flow-actionability-observation" :owner "pr-flow-cli"
                        :dod "Preserve native actionability admission/revocation append-only"
                        :pi "pr-flow/actionability" :host "local-cli-native-github"
                        :manifest [] :refs [(:repo-id o) (:pr-id o) (:thread-id o) (:head o)]
                        :decisions [o] :note "Observed disposition is not settlement, approval, coverage, or round credit."}) "\n"))))))

(defn- fetch-issue-comments [repo n]
  (native-context/fetch-issue-comments gh-json repo n))

(declare authorized-author? reviewer-identities load-flow)

(defn fetch-threads
  "Collect native inputs, then retain canonical classification and observations.
   Incomplete nested comment pages still fail the gate closed. Native issue
   records may be supplied by the same approval snapshot."
  ([repo n] (fetch-threads repo n (fetch-issue-comments repo n)))
  ([repo n issue-comments]
   (let [collected (native-context/collect-context
                    {:read-json gh-json :authorized-author? authorized-author?
                     :read-history actionability-observations
                     :preservation-policy (get-in (load-flow) [:flow/defaults :review/positive-bot-thread])} repo n issue-comments)
         history (:history collected)
         policy (get-in (load-flow) [:flow/defaults :review/actionability])
         identities (reviewer-identities)
         threads (mapv #(law/classify-thread
                          (assoc % :actionability-policy policy
                                   :actionability-observations history :identities identities))
                       (:threads collected))
         observations (vec (distinct (mapcat #(concat (get-in % [:actionability :observations]) (get-in % [:preservation :observations])) threads)))
         _ (when (some? history)
             (append-actionability-observations! repo observations))
         history (when (some? history) (into history observations))]
     (assoc (select-keys collected [:draft? :head :pr-author :incomplete?])
            :actionability-history-available? (some? history)
            ;; Normalize our own append before snapshot comparison. Native raw
            ;; records remain unchanged; a subsequent actual mutation still blocks.
            :threads (mapv #(law/classify-thread (assoc % :actionability-observations history)) threads)))))

(declare load-flow)

(defn- reviewer-identities []
  (merge law/default-reviewer-identities
         (get-in (load-flow) [:flow/defaults :review/identities])))

(defn reviewer-key [login]
  (law/trusted-reviewer {:user {:login login :type "Bot"}} (reviewer-identities)))

(defn- full-review? [review]
  (law/full-review? review (reviewer-identities)))

(declare fetch-checks authorized-author?)

(defn- fetch-historical-coderabbit-checks
  "Read the observed CodeRabbit commit-status channel by immutable SHA.
   Only the configured native Bot creator and exact context can corroborate
   historical issue completion; public request prose is not fetch authority."
  [repo head]
  (let [commit (gh-json "api" (str "repos/" repo "/commits/" head))
        _ (when-not (= head (:sha commit))
            (throw (ex-info "Historical CodeRabbit check commit binding could not be verified" {:head head})))
        pages (gh-json "api" (str "repos/" repo "/commits/" head "/statuses") "--paginate" "--slurp")]
    (when-not (and (vector? pages) (every? vector? pages) (every? map? (mapcat identity pages)))
      (throw (ex-info "Historical CodeRabbit check history unavailable or incomplete" {:head head})))
    (mapv (fn [s]
            {:name "CodeRabbit" :state (str/upper-case (str (:state s))) :headSha (:sha commit)
             :workflow "" :description (:description s) :startedAt (:created_at s)
             :completedAt (when-not (= "pending" (:state s)) (:updated_at s))
             :native-id (:id s) :source-channel :github-commit-status :source-url (:url s)})
          (filter #(and (= "CodeRabbit" (:context %))
                        (= "coderabbit" (law/trusted-reviewer {:user (:creator %)} (reviewer-identities)))
                        (integer? (:id %)) (pos? (:id %))
                        (string? (:created_at %)) (string? (:updated_at %))
                        (not (js/isNaN (js/Date.parse (:created_at %))))
                        (not (js/isNaN (js/Date.parse (:updated_at %)))))
                  (mapcat identity pages)))))

(defn- publication-pages [endpoint key]
  (let [pages (gh-json "api" endpoint "--paginate" "--slurp")
        rows (when (and (vector? pages) (seq pages))
               (if key (mapcat key pages) (mapcat identity pages)))]
    (when-not (and rows (every? map? rows)
                   (if key (and (every? #(vector? (get % key)) pages)
                                (every? #(= (count rows) (:total_count %)) pages))
                       (every? vector? pages)))
      (throw (ex-info "Incomplete Kimi publication metadata" {})))
    (vec rows)))

(defn- publication-archive [endpoint]
  (let [run (fn [env] (cp/spawnSync "gh" #js ["api" endpoint]
                                   #js {:env env :timeout 30000 :maxBuffer (* 2 1024 1024)}))
        r (run js/process.env)
        r (if (and (not= 0 (.-status r))
                   (re-find #"Resource not accessible|Bad credentials|HTTP 40[13]" (str (.-stderr r))))
            (run (env-without-tokens)) r)]
    (when-not (= 0 (.-status r)) (throw (ex-info "Kimi publication archive unavailable" {})))
    (.-stdout r)))

(defn hydrate-positive-review-update
  "Bind the latest native body edit to the same REST positive review. Missing
   or changed source custody cannot fall back to submission-only settlement."
  [review]
  (if (and (= "coderabbit" (law/trusted-reviewer review (reviewer-identities)))
           (= "COMMENTED" (:state review))
           (some :positive-body-item (law/review-findings review)))
    (let [id (:node_id review)
          response (when (and (string? id) (not (str/blank? id)))
                     (gh-json "api" "graphql" "-f"
                              "query=query($id:ID!){node(id:$id){... on PullRequestReview{id databaseId state body updatedAt submittedAt commit{oid} author{__typename ... on Bot{id}}}}}"
                              "-f" (str "id=" id)))
          native (get-in response [:data :node])
          matches? (and (not (seq (:errors response))) (map? native)
                        (= id (:id native)) (= (:id review) (:databaseId native))
                        (= (:body review) (:body native)) (= (:state review) (:state native))
                        (= (:commit_id review) (get-in native [:commit :oid]))
                        (= (:submitted_at review) (:submittedAt native))
                        (string? (get-in review [:user :node_id]))
                        (= (get-in review [:user :node_id]) (get-in native [:author :id]))
                        (= "Bot" (get-in native [:author :__typename]))
                        (string? (:updatedAt native)))]
      (cond-> (assoc review :updated_at (when matches? (:updatedAt native)))
        (not matches?) (assoc :positive-review-update-error :native-source-mismatch)))
    review))

(defn fetch-heads
  "Collect hosted approval, commit binding and incomplete scope separately."
  [repo n]
  (let [head (str/trim (gh! "pr" "view" (str n) "-R" repo "--json" "headRefOid" "-q" ".headRefOid"))
        reviews (kimi-native/hydrate repo (js/parseInt n 10) head
                                    (mapv hydrate-positive-review-update
                                          (gh-pages (str "repos/" repo "/pulls/" n "/reviews")))
                                    (get-in (load-flow) [:flow/defaults :review/kimi-publication])
                                    {:get-json #(gh-json "api" %) :get-pages publication-pages :get-archive publication-archive})
        raw-comments (fetch-issue-comments repo n)
        comments (mapv
                  (fn [c]
                    (if (and (= "codex" (law/trusted-reviewer c (reviewer-identities)))
                             (re-find #"^Codex Review: Didn't find any major issues\." (str (:body c))))
                      (let [markers (map second (re-seq #"(?m)^\*\*Reviewed commit:\*\* `([0-9a-f]{10,40})`\s*$" (str (:body c))))]
                        (if (= 1 (count markers))
                          (assoc c :resolved-commit-id
                                 (:sha (gh-json "api" (str "repos/" repo "/commits/" (first markers)))))
                          c))
                      c))
                  raw-comments)
        checks (fetch-checks repo n)
        authorized? (memoize (partial authorized-author? repo))
        round-comments (mapv #(assoc % :trusted? (and (str/includes? (str (:body %)) "pr-flow-review:")
                                                     (authorized? (get-in % [:user :login])))) comments)
        historical (sort (disj (law/coderabbit-issue-completion-heads round-comments (reviewer-identities)) head))
        completion-checks (into checks (mapcat #(fetch-historical-coderabbit-checks repo %) historical))]
    (assoc (law/review-evidence head reviews comments (reviewer-identities) checks) :head head
           :reviews reviews :comments comments :raw-comments raw-comments :checks checks
           :completion-checks completion-checks)))

(defn fetch-checks
  "PR check rows. `gh pr checks` exits 8 while checks are pending; that is data,
   not failure. Tries the normal environment first (token-only CI needs it) and
   falls back to the keyring only on a token-permission refusal. Any other
   failure throws, so a blank result never reads as 'no CodeRabbit'."
  [repo n]
  (let [head (str/trim (gh! "pr" "view" (str n) "-R" repo "--json" "headRefOid" "-q" ".headRefOid"))
        args ["pr" "checks" (str n) "-R" repo "--json" "name,state,description,startedAt,completedAt,workflow,link"]
        ok? (fn [r] (and (#{0 8} (:exit r)) (not (str/blank? (:out r)))))
        r (run-gh args js/process.env)
        r (if (and (not (ok? r)) (re-find #"Resource not accessible|Bad credentials|HTTP 40[13]" (:err r)))
            (run-gh args (env-without-tokens))
            r)
        required-args (conj args "--required")
        required (run-gh required-args js/process.env)
        required (if (and (not (ok? required)) (re-find #"Resource not accessible|Bad credentials|HTTP 40[13]" (:err required)))
                   (run-gh required-args (env-without-tokens)) required)
        required-names (cond
                         (ok? required) (set (map :name (js->clj (js/JSON.parse (:out required)) :keywordize-keys true)))
                         (re-find #"no (?:required )?checks reported" (str (:err required) (:out required))) #{}
                         :else (throw (ex-info "Cannot identify required checks" required)))
        final-head (str/trim (gh! "pr" "view" (str n) "-R" repo "--json" "headRefOid" "-q" ".headRefOid"))]
    ;; gh exports normalized context/state rows, not headSha. Bind those rows
    ;; to an unchanged PR head across both all/required check reads.
    (when-not (= head final-head)
      (throw (ex-info "PR head changed while collecting native checks" {:head head :snapshot-head final-head})))
    (cond
      (ok? r) (mapv #(assoc % :headSha head :required? (contains? required-names (:name %)))
                    (js->clj (js/JSON.parse (:out r)) :keywordize-keys true))
      (re-find #"no checks reported" (:err r)) []
      :else (throw (ex-info (str "gh pr checks failed: " (str/trim (:err r))) r)))))

(defn- authorized-author? [repo login]
  (when (and login (not (law/bot? login)))
    (try
      (permissions/writer-role? (:permission (gh-json "api" (str "repos/" repo "/collaborators/" login "/permission"))))
      (catch :default e
        (if (re-find #"HTTP 404|Not Found" (ex-message e)) false (throw e))))))

(defn review-bodies-unanswered
  "Every provider's identified or body-only findings need authorized,
   itemized settlements, using the same review snapshot as approval evidence."
  [repo {:keys [head reviews comments]} context]
  (let [flagged (law/outstanding-review-bodies head reviews)
        authorized? (memoize (partial authorized-author? repo))
        comments (mapv #(assoc % :authorized? (boolean (authorized? (get-in % [:user :login])))) comments)]
    (law/unanswered-review-count flagged comments context)))

(defn review-progress
  "Completed rounds and authenticated current-head agent availability."
  [repo {:keys [head reviews comments completion-checks checks]} mandatory]
  (let [identities (reviewer-identities)
        authorized? (memoize (partial authorized-author? repo))
        comments (mapv #(assoc % :trusted? (and (str/includes? (str (:body %)) "pr-flow-review:")
                                              (authorized? (get-in % [:user :login])))) comments)
        markers (keep (fn [c]
                        (or (when (:trusted? c)
                              (when-let [[_ stage] (re-find #"<!-- pr-flow-stage:(planning|code) -->" (str (:body c)))]
                                {:stage stage :created_at (:created_at c)}))
                            (when (str/includes? (str (:body c)) "pr-flow-head:")
                              (law/legacy-stage-marker
                               (assoc c :authorized? (boolean (authorized? (get-in c [:user :login])))))))) comments)
        stage (or (:stage (last (sort-by :created_at markers))) "code")
        configured (into (set mandatory)
                         (for [[provider logins] identities
                               :when (and (law/eligible-reviewers provider) (seq logins))] provider))
        availability (law/reviewer-availability {:head head :comments comments :reviews (filter full-review? reviews)
                                                :checks (or completion-checks checks) :mandatory (set mandatory)
                                                :repository repo :publication-profile (get-in (load-flow) [:flow/defaults :review/kimi-publication])
                                                :identities identities :now-ms (js/Date.now)})
        participants (:review-participants availability)
        completed (law/completed-review-rounds (filter full-review? reviews) comments identities completion-checks)
        proven-issue-heads (set (keep #(when (and (= "coderabbit" (:reviewer %)) (= :issue (:round-source %)))
                                        (:commit_id %))
                                     (law/completed-review-rounds [] comments identities completion-checks)))]
    {:rounds (law/available-review-rounds completed markers stage configured participants head)
     :review-participants participants :stage stage
     :review-availability (:review-availability availability)
     :unverified-issue-completion-heads (remove proven-issue-heads (law/coderabbit-issue-completion-heads comments identities))
     :min-review-rounds (get-in (load-flow) [:flow/defaults :review/min-rounds] 5)}))

;; --- output ---------------------------------------------------------------

(defn print-threads [threads]
  (doseq [{:keys [id path line severity resolved? resolution reviewer comments
                 rejection-channel rejection-source-id rejection-url actionability opener-withdrawal preservation]} threads]
    (println (str (name severity) "  " (if resolved? "resolved  " "OPEN      ")
                  (if resolution (name resolution) "-unsettled-") "  " reviewer "  " path ":" line))
    (println (str "    id=" id))
    (println (str "    " (law/title-of (:body (first comments)))))
    (println (str "    " (:url (first comments))))
    (when rejection-channel
      (println (str "    rejection evidence: " (name rejection-channel) " id=" rejection-source-id " " rejection-url)))
    (when opener-withdrawal
      (println (str "    native opener withdrawal: id=" (:source-id opener-withdrawal) " " (:url opener-withdrawal))))
    (when (#{:qualified :revoked} (:status preservation))
      (println (str "    preservation: " (name (:status preservation))
                    " assessment=" (:assessment-id preservation) " " (:assessment-url preservation)
                    " writer=" (:writer-id preservation) " settled=" (:settled? preservation))))
    (when (not= :absent (:status actionability))
      (println (str "    actionability: " (name (:kind actionability)) " " (name (:status actionability))
                    " native-source=" (:assessment-id actionability) " " (:url actionability))))))

(defn status [repo n reviewers]
  (let [heads (fetch-heads repo n)
        thread-snapshot (fetch-threads repo n (:raw-comments heads))
        {:keys [draft? threads incomplete?]} thread-snapshot
        checks (:checks heads)
        progress (review-progress repo heads reviewers)
        summary-items (review-bodies-unanswered repo heads
                                               (merge progress {:head (:head heads) :pr-author (:pr-author thread-snapshot)
                                                                :identities (reviewer-identities)}))
        final-threads (fetch-threads repo n)
        final-snapshot (fetch-heads repo n)
        final-progress (review-progress repo final-snapshot reviewers)
        changed? (or (not= thread-snapshot final-threads)
                     (not= (select-keys progress [:rounds :stage :review-participants :review-availability])
                           (select-keys final-progress [:rounds :stage :review-participants :review-availability]))
                     (not= (select-keys heads [:head :reviews :comments :checks :completion-checks])
                           (select-keys final-snapshot [:head :reviews :comments :checks :completion-checks])))
        gate (assoc (law/merge-gate (merge heads progress
                                    {:threads threads :checks checks :incomplete? incomplete?
                                     :required-reviewers reviewers
                                     :snapshot-head (:head final-snapshot)
                                     :approval-quorum (get-in (load-flow) [:flow/defaults :review/approval-quorum] 1)
                                     :review-bodies-unanswered summary-items})) :draft? draft?)
        gate (if changed? (-> gate (assoc :pass? false)
                              (update :reasons conj "Review evidence changed while collecting conversations; re-evaluate")) gate)]
    (println (str repo "#" n (when draft? "  [draft]") "  head " (subs (:head heads) 0 7)))
    (when-not (:actionability-history-available? thread-snapshot)
      (println "  actionability history: UNAVAILABLE; threads retain finding obligations"))
    (println (str "  coderabbit: " (name (:coderabbit gate)) "   checks: " (pr-str (:checks gate))))
    (println (str "  threads: " (count threads) " total, " (count (remove :resolved? threads)) " unresolved; by severity "
                  (pr-str (frequencies (map :severity (filter law/finding-obligation? (remove :resolved? threads)))))
                  "; informational " (count (remove law/finding-obligation? threads))))
    (println (str "  exact-head approvals: " (pr-str (:approving-reviewers gate)) "  observed commit binding: " (pr-str (:reviewed-heads heads))))
    (println (str "  completed " (:stage progress) " rounds: " (:rounds progress)
                  " / soft minimum " (:min-review-rounds progress)
                  "  participants: " (pr-str (:review-participants progress))))
    (doseq [[provider observation] (sort-by key (:review-availability progress))
            :when (not (:available? observation))]
      (println (str "  " provider " quota-unavailable: native source " (:source-id observation)
                    " request " (:request-id observation) " scope " (name (:scope observation)) " observed " (:observed-at observation)
                    " retry-at-ms " (or (:retry-at-ms observation) "UNKNOWN")
                    (when (:mandatory? observation) " [mandatory; still required]"))))
    (when (seq (:unverified-issue-completion-heads progress))
      (println (str "  CodeRabbit issue completions without successful exact-commit check evidence: "
                    (pr-str (sort (:unverified-issue-completion-heads progress))))))
    (when (seq (:incomplete-evidence heads))
      (println (str "  incomplete review scope: " (pr-str (:incomplete-evidence heads)))))
    (doseq [r (:reviews heads) :when (:kimi-publication-error r)]
      (println (str "  Kimi publication proof: " (name (:kimi-publication-error r)) " native review " (:id r))))
    (doseq [r (:reviews heads) :when (:positive-review-update-error r)]
      (println (str "  positive review edit custody: " (name (:positive-review-update-error r)) " native review " (:id r))))
    (println (str "  gate: " (if (:pass? gate) "PASS" "BLOCKED")))
    (doseq [r (:reasons gate)] (println (str "    - " r)))
    gate))

;; --- writes ---------------------------------------------------------------

(def briefs
  {"planning" (str "@coderabbitai full review\n\n"
                   "This PR carries agile artifacts. Review it like sprint planning: "
                   "(1) is the outcome and scope clear, (2) are acceptance criteria testable, "
                   "(3) is each estimate fair or should a card split into substories, "
                   "(4) are epic/parent/blocked_by links correct and complete, "
                   "(5) what risks or non-goals are missing. Label each finding P0-P3.")
   "code" (str "@coderabbitai full review\n\n"
               "Review the implementation against the card's laws and acceptance criteria. "
               "Label each finding P0-P3. During the first five rounds we prefer verified fixes of every priority. "
               "An outright rejection requires detailed reasoning and independent agreement by another agent besides CodeRabbit.")})

(declare load-flow)

(defn request [repo n kind note reviewer]
  (when-not (#{"coderabbit" "codex"} reviewer)
    (throw (ex-info "MiMo/Kimi must use their configured hosted workflows; CLI evidence cannot impersonate a GitHub review" {:reviewer reviewer})))
  (let [heads (fetch-heads repo n)
        head (:head heads)
        comments (mapv (fn [c]
                         (assoc c :trusted? (and (str/includes? (str (:body c)) "pr-flow-review:")
                                                  (authorized-author? repo (get-in c [:user :login])))))
                       (:comments heads))
        progress (review-progress repo (assoc heads :comments comments)
                                  (law/required-reviewers-for (:flow/defaults (load-flow)) repo nil))
        rounds (if (= kind (:stage progress)) (:rounds progress) 0)
        verdict (law/request-verdict {:head head :reviewer reviewer :comments comments :reviews (:reviews heads)
                                      :checks (:checks heads)
                                      :round (inc rounds) :stage kind
                                      :now-ms (js/Date.now) :identities (reviewer-identities)})
        brief (or (get briefs kind) (throw (ex-info "kind must be planning|code" {:kind kind})))
        brief (if (= reviewer "codex") (str/replace brief "@coderabbitai full review" "@codex review") brief)
        body (str brief (when note (str "\n\n" note))
                  "\n\n<!-- pr-flow-stage:" kind " --> <!-- pr-flow-round:" (inc rounds)
                  " --> <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:" reviewer " -->")]
    (if (= :request (:status verdict))
      (do
        (when-not (= head (str/trim (gh! "pr" "view" (str n) "-R" repo "--json" "headRefOid" "-q" ".headRefOid")))
          (throw (ex-info "PR head changed before review request; no request sent" {:head head})))
        (println (str/trim (gh-with-input! ["pr" "comment" (str n) "-R" repo "--body-file" "-"] body))))
      (do
        (println (str "No request sent: " (name (:status verdict))
                      (when-let [ms (:retry-at-ms verdict)] (str "; retry after " (.toISOString (js/Date. ms))))))
        (when (#{:invalid :rate-limited} (:status verdict))
          (throw (ex-info "Review request needs operator attention; no retry scheduled" verdict)))))))

(defn- settle-generic [repo n thread-id body]
  (let [body (if (= body "-") (str (fs/readFileSync 0 "utf8")) body)]
    (when-not (law/resolution-of body)
      (throw (ex-info "BODY must open with Fixed|Deferred|Rejected|Handled" {:body (subs body 0 (min 60 (count body)))})))
    (let [snapshot (fetch-threads repo n)
          thread (some #(when (= thread-id (:id %)) %) (:threads snapshot))]
      (when-not thread
        (throw (ex-info "Thread does not belong to the requested PR" {:repo repo :pr n :thread-id thread-id})))
      (when (:incomplete? snapshot)
        (throw (ex-info "Thread comments were truncated; no settlement sent" {})))
      (when (= :deferred (law/resolution-of body))
        (let [heads (fetch-heads repo n)
              progress (review-progress repo heads
                                        (law/required-reviewers-for (:flow/defaults (load-flow)) repo nil))]
          (when (<= (:rounds progress) (:min-review-rounds progress))
            (throw (ex-info (str "During the first " (:min-review-rounds progress)
                                 " rounds verified findings should be fixed or independently rejected; no deferral sent") {})))))
      (when (= :rejected (law/resolution-of body))
        (let [author (str/trim (gh! "api" "user" "--jq" ".login"))
              now (.toISOString (js/Date.))
              candidate (law/classify-thread
                         (update thread :comments conj
                                 {:author author :user {:login author :type "User"}
                                  :authorized? (boolean (authorized-author? repo author)) :body body
                                  :created-at now :updated-at now}))]
          (when-not (and (:settled? candidate) (:rejection-approved? candidate)
                         (empty? (law/unsettled-blockers [candidate])))
            (throw (ex-info "Rejection needs detailed reasoning/evidence and current-head independent non-CodeRabbit agreement; no reply or resolution sent" {})))
          (when-not (= (:head thread)
                       (str/trim (gh! "pr" "view" (str n) "-R" repo "--json" "headRefOid" "-q" ".headRefOid")))
            (throw (ex-info "PR head changed before rejection; no settlement sent" {})))
          (when-not (= snapshot (fetch-threads repo n))
            (throw (ex-info "Review evidence changed before rejection; no settlement sent" {}))))))
    (gh! "api" "graphql" "-f" "query=mutation($t:ID!,$b:String!){addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId:$t,body:$b}){comment{url}}}"
         "-F" (str "t=" thread-id) "-f" (str "b=" body))
    (gh! "api" "graphql" "-f" "query=mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{isResolved}}}"
         "-F" (str "t=" thread-id))
    (println (str "settled " thread-id " as " (name (law/resolution-of body))))))

(defn gate [repo n apply? method reviewers]
  (when-not (#{"merge" "squash" "rebase"} method)
    (throw (ex-info "--method must be merge|squash|rebase" {:method method})))
  (let [g (status repo n reviewers)]
    (when (and apply? (:pass? g))
      (when (:draft? g) (gh! "pr" "ready" (str n) "-R" repo))
      (let [post-ready (status repo n reviewers)
            head (:head post-ready)]
        (when-not (and (:pass? post-ready) (= head (:head g)))
          (println "Fresh gate failed or head changed; no merge requested")
          (js/process.exit 2))
      (try
        (gh! "pr" "merge" (str n) "-R" repo "--auto" (str "--" method) "--match-head-commit" head)
        (println (str "ready + auto-merge (" method ") enabled on " head))
        (catch :default e
          ;; Repos without allow_auto_merge or without required checks refuse
          ;; --auto. The gate above already verified checks, threads and
          ;; review on this exact head, so merge now, pinned to that head.
          (if (re-find #"(?i)auto merge is not allowed|clean status|not allowed for this repository" (ex-message e))
            (do
                (let [fresh (status repo n reviewers)]
                  (when-not (and (:pass? fresh) (= head (:head fresh)))
                    (println "Fresh gate failed before direct-merge fallback")
                    (js/process.exit 2)))
                (gh! "pr" "merge" (str n) "-R" repo (str "--" method) "--match-head-commit" head)
                (println (str "auto-merge unavailable; merged (" method ") at gated head " head)))
            (throw e))))))
    (when (and apply? (not (:pass? g)))
      (js/process.exit 2))))

(defn wait [repo n timeout interval]
  (let [deadline (+ (js/Date.now) (* 1000 timeout))]
    (letfn [(tick []
              (let [checks (fetch-checks repo n)
                    st (law/coderabbit-state (law/latest-checks (:headSha (first checks)) checks))]
                (cond
                  (#{:completed :skipped :failed} st) (do (println (str "coderabbit " (name st))) (js/process.exit 0))
                  (= st :rate-limited) (do (println "coderabbit rate-limited") (js/process.exit 3))
                  (> (js/Date.now) deadline) (do (println (str "timeout; coderabbit " (name st))) (js/process.exit 4))
                  :else (js/setTimeout tick (* 1000 interval)))))]
      (tick))))

;; --- flow -----------------------------------------------------------------

(defn load-flow []
  (edn/read-string (str (fs/readFileSync (path/join here ".." "flow.edn") "utf8"))))

(defn- missing-skills [f]
  (let [skills-dir (path/join here ".." "..")]
    (->> (flow/skills f)
         (remove #(fs/existsSync (path/join skills-dir % "SKILL.md")))
         (map (fn [skill] {:problem :missing-skill :skill skill}))
         vec)))

(defn show-flow [state]
  (let [f (load-flow)
        ps (into (flow/problems f) (missing-skills f))]
    (when (seq ps)
      (doseq [p ps] (println "PROBLEM" (pr-str p)))
      (js/process.exit 1))
    (if state
      (let [k (keyword (str/replace state #"^:" ""))
            st (get-in f [:flow/states k])]
        (println (str k "  skill=" (:skill st) (when (:uses st) (str " uses=" (str/join "," (:uses st))))))
        (println (str "  " (:doc st)))
        (when (:exit st) (println (str "  exit: " (:exit st))))
        (doseq [c (:cli st)] (println (str "  cli: " c)))
        (println (str "  next: " (str/join " " (flow/next-states f k)))))
      (doseq [k (keys (:flow/states f))]
        (println (str k " -> " (str/join " " (flow/next-states f k)) "   [" (get-in f [:flow/states k :skill]) "]"))))))

;; --- main -----------------------------------------------------------------

(defn- flag [args f default]
  (let [prefix (str f "=")
        hits (keep-indexed #(when (or (= f %2) (str/starts-with? (str %2) prefix)) %1) args)]
    (when (> (count hits) 1)
      (throw (ex-info (str f " must be supplied only once") {:flag f})))
    (if-let [i (first hits)]
      (let [opt (nth args i)
            v (if (= f opt) (nth args (inc i) nil) (subs opt (count prefix)))]
        (when (or (nil? v) (str/blank? v) (str/starts-with? v "--"))
          (throw (ex-info (str f " requires a value") {:flag f})))
        v)
      default)))

(defn- timing-flag [args f default]
  (let [value (flag args f default)
        seconds (js/Number value)]
    (when-not (and (string? value) (re-matches #"[0-9]+" value)
                   (js/Number.isSafeInteger seconds) (pos? seconds))
      (throw (ex-info (str f " must be a positive finite integer in seconds") {:flag f})))
    seconds))

(defn- reviewers-flag
  "--reviewers coderabbit,codex → #{\"coderabbit\" \"codex\"}; default from flow.edn."
  [repo args]
  (let [positions (keep-indexed #(when (or (= "--reviewers" %2) (str/starts-with? %2 "--reviewers=")) %1) args)
        _ (when (> (count positions) 1)
            (throw (ex-info "--reviewers must be supplied only once" {})))
        requested (when-let [i (first positions)]
                    (let [option (nth args i)
                          v (if (= "--reviewers" option) (nth args (inc i) nil)
                                (subs option (count "--reviewers=")))]
                      (when (or (str/blank? v) (str/starts-with? v "--"))
                        (throw (ex-info "--reviewers requires a comma-separated value" {})))
                      (let [reviewers (set (str/split v #"," -1))]
                        (when-not (and (seq reviewers) (every? law/eligible-reviewers reviewers))
                          (throw (ex-info "--reviewers requires known mandatory reviewer names" {:reviewers reviewers})))
                        reviewers)))]
    (law/required-reviewers-for (:flow/defaults (load-flow)) repo requested)))

;; --- independently qualified informational resolution ---------------------
;; This route never changes the pure law or accepts supplied eligibility. One
;; GitHub.com token-stripped/keyring gh environment is used for every read
;; and the single possible POST. It has no auth fallback, reply, merge, or retry.

(defn- resolution-environment []
  (let [env (env-without-tokens)]
    (js-delete env "GH_ENTERPRISE_TOKEN")
    (js-delete env "GITHUB_ENTERPRISE_TOKEN")
    (aset env "GH_HOST" "github.com")
    env))

(defn- resolution-value [r]
  (when-not (= 0 (:exit r))
    (throw (ex-info "Resolve-only native command failed" {:command-result r})))
  (let [value (try (js->clj (js/JSON.parse (:out r)) :keywordize-keys true)
                   (catch :default _
                     (throw (ex-info "Resolve-only native JSON unavailable" {:command-result r}))))]
    (when (seq (:errors value))
      (throw (ex-info "Resolve-only native GraphQL errors" {:command-result r})))
    value))

(defn- resolution-json [env & args]
  (resolution-value (run-gh args env)))

(defn- resolution-writer-role [env repo login]
  (when (and (string? login) (not (str/blank? login)) (not (law/bot? login)))
    ;; Unavailable permissions are refused, including 404; never converted to
    ;; a healthy collection or a second authenticated identity.
    (permissions/writer-role?
      (:permission (resolution-json env "api" (str "repos/" repo "/collaborators/" login "/permission"))))))

(defn- resolution-writer [env repo]
  (let [user (resolution-json env "api" "user")
        actor (select-keys user [:login :id :node_id :type])
        role (resolution-writer-role env repo (:login actor))]
    (when-not (and (= "User" (:type actor))
                   (string? (:login actor)) (not (str/blank? (:login actor)))
                   (not (law/bot? (:login actor)))
                   (js/Number.isSafeInteger (:id actor)) (pos? (:id actor))
                   (string? (:node_id actor)) (not (str/blank? (:node_id actor))) role)
      (throw (ex-info "Resolve-only requires an authenticated native User writer" {})))
    (assoc actor :permission role)))

(defn- resolution-history-source []
  ;; Hold exact bytes independently of the existing complete text decoder.
  ;; Unavailable source is nil, never synthesized empty history.
  (try (when (fs/existsSync (actionability-ledger))
         (let [bytes (fs/readFileSync (actionability-ledger))]
           {:text (.toString bytes "utf8") :base64 (.toString bytes "base64")}))
       (catch :default _ nil)))

(defn- resolution-history-prefix? [before after]
  (let [a (js/Buffer.from (:base64 before) "base64")
        b (js/Buffer.from (:base64 after) "base64")]
    (and (<= (.-length a) (.-length b)) (.equals a (.subarray b 0 (.-length a))))))

(defn- collect-resolution [env repo n expected]
  (let [source (resolution-history-source) text (:text source)
        collected (native-context/collect-context
                    {:read-json (partial resolution-json env)
                     :authorized-author? (partial resolution-writer-role env)
                     :read-history #(actionability-history/observations text %)} repo n)
        selection (native-context-law/select-fresh-context collected expected)]
    (when-let [reason (:refusal selection)]
      (throw (ex-info "Resolve-only fresh context refused" {:reason reason})))
    (let [thread (law/classify-thread
                   (assoc (:selected selection)
                          :actionability-policy (get-in (load-flow) [:flow/defaults :review/actionability])
                          :actionability-observations (:history collected)
                          :identities (reviewer-identities)))]
      {:collected collected :history-text text :history-source source :thread thread
       :authorization (actionability/resolution-authorization thread)})))

(defn- require-resolution-binding! [input expected]
  (let [a (:authorization input)]
    (when-not (and (true? (:allowed? a))
                   (= (select-keys expected [:head :context-binding :proposal-id :assessment-id :channel])
                      (select-keys a [:head :context-binding :proposal-id :assessment-id :channel]))
                   (= (:root-comment-id expected) (get-in input [:thread :root-comment-id])))
      (throw (ex-info "Resolve-only lacks the exact independently qualified unresolved binding" {})))))

(defn- retain-resolution-observations!
  ([repo input] (retain-resolution-observations! repo input (vec (get-in input [:thread :actionability :observations]))))
  ([repo input observations]
  (let [before (:history-text input) source (:history-source input)
        history (get-in input [:collected :history])
        repo-id (get-in input [:collected :repository :id])]
    (when-not (and (string? before) (vector? history)
                   (or (str/blank? before) (str/ends-with? before "\n"))
                   (= source (resolution-history-source)))
      (throw (ex-info "Resolve-only complete history changed before observation append" {})))
    ;; Reuse the existing canonical append boundary and decoder, never a
    ;; resolver-specific ledger schema. A concurrent append is detected by the
    ;; complete result comparison, not overwritten or relabeled as ours.
    (append-actionability-observations! repo observations)
    (let [after-source (resolution-history-source) after (:text after-source)
          decoded (actionability-history/observations after repo-id)]
      (when-not (and (string? after) (resolution-history-prefix? source after-source)
                     (= (+ (count (remove str/blank? (str/split-lines before))) (count observations))
                        (count (remove str/blank? (str/split-lines after))))
                     (= (into history observations) decoded))
        (throw (ex-info "Resolve-only observation append custody unavailable" {})))
      {:text after :source after-source :history decoded}))))

(defn- resolution-native-facts [input]
  (select-keys (:collected input)
               [:repository :pr :raw-threads :raw-issue-comments :draft? :head :pr-author :incomplete?]))

(defn- expected-resolved-facts [input thread-id]
  ;; Only a comparison of the permitted native delta. This is never fed into
  ;; hydration/classification/authorization or called an observed snapshot.
  (update (resolution-native-facts input) :raw-threads
          #(mapv (fn [t] (if (= thread-id (:id t)) (assoc t :isResolved true) t)) %)))

(defn resolve-informational [repo n thread-id expected-text flags]
  (let [expected-source (str/trim (if (= "-" expected-text) (fs/readFileSync 0 "utf8") expected-text))
        expected (edn/read-string expected-source)
        keys-required #{:repo :repository-database-id :pr-number :head :thread-id
                        :root-comment-id :context-binding :proposal-id :assessment-id :channel}
        number (js/Number n)
        apply? (= ["--apply"] (vec flags))]
    (when-not (and (or (empty? flags) apply?)
                   (string? repo) (re-matches #"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+" repo)
                   (string? n) (re-matches #"[1-9][0-9]*" n)
                   (js/Number.isSafeInteger number) (<= number 2147483647)
                   (map? expected) (= expected-source (pr-str expected))
                   (= keys-required (set (keys expected)))
                   (= repo (:repo expected)) (= number (:pr-number expected))
                   (= thread-id (:thread-id expected))
                   (string? thread-id) (not (str/blank? thread-id))
                   (string? (:head expected)) (re-matches #"[0-9a-f]{40}" (:head expected))
                   (every? #(and (js/Number.isSafeInteger %) (pos? %))
                           (map expected [:repository-database-id :root-comment-id :proposal-id :assessment-id]))
                   (vector? (:context-binding expected)) (= 7 (count (:context-binding expected)))
                   (= :github-issue-comment (:channel expected)))
      (throw (ex-info "Resolve-only requires one exact native target/binding map and optional --apply" {})))
    (let [env (resolution-environment)
          actor (resolution-writer env repo)
          initial (collect-resolution env repo n expected)
          _ (require-resolution-binding! initial expected)
          result {:repo repo :pr-number number :thread-id thread-id :actor actor
                  :authorization (:authorization initial) :mutation-attempt-count 0}]
      (if-not apply?
        (assoc result :status :eligible-preview :observations-appended? false)
        (let [retained (retain-resolution-observations! repo initial)
              fresh (collect-resolution env repo n expected)
              _ (require-resolution-binding! fresh expected)
              _ (when-not (and (= (dissoc (:collected initial) :history)
                                  (dissoc (:collected fresh) :history))
                               (= (:source retained) (:history-source fresh) (resolution-history-source))
                               (= (:history retained) (get-in fresh [:collected :history]))
                               (= actor (resolution-writer env repo)))
                  (throw (ex-info "Resolve-only source, complete history, actor or permission changed before POST" {})))
              _ (when-not (= (:source retained) (resolution-history-source))
                  (throw (ex-info "Resolve-only complete history changed during final permission refresh" {})))
              ;; There is no GitHub head/context CAS for this mutation. Recollect
              ;; afterwards and preserve uncertainty; never retry an uncertain POST.
              post-command (run-gh ["api" "graphql" "-f"
                              "query=mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{id isResolved}}}"
                              "-f" (str "t=" thread-id)] env)
              posted (try
                       (let [r (resolution-value post-command)]
                         {:command-result post-command
                          :confirmed? (= {:id thread-id :isResolved true}
                                         (get-in r [:data :resolveReviewThread :thread]))})
                       (catch :default _ {:confirmed? false :error :post-outcome-unavailable
                                          :command-result post-command}))
              observed (try
                         (let [post (collect-resolution env repo n expected)
                               only-resolution? (= (expected-resolved-facts fresh thread-id)
                                                   (resolution-native-facts post))
                               resolved? (true? (get-in post [:thread :resolved?]))
                               _ (retain-resolution-observations! repo post)]
                           {:readback? true :resolved? resolved? :only-resolution? only-resolution?
                            :context-binding (actionability/context-binding (:thread post))
                            :disposition (select-keys (get-in post [:thread :actionability]) [:kind :status])})
                         (catch :default e {:readback? false :error :readback-or-observation-custody-unavailable
                                            :cause (ex-message e) :refusal (:reason (ex-data e))
                                            :exit (get-in (ex-data e) [:command-result :exit])}))
              confirmed? (and (:confirmed? posted) (:readback? observed)
                              (:resolved? observed) (:only-resolution? observed))]
          (assoc result :status (if confirmed? :resolved-needs-fresh-assessment :uncertain-native-outcome)
                 :mutation-attempt-count 1 :post posted :readback observed
                 :requires-fresh-assessment? true :settlement-credit? false
                 :approval-credit? false :round-credit? false))))))

;; --- independently assessed positive Bot-thread preservation ---------------

(defn- collect-preservation [env repo n thread-id head]
  (let [source (resolution-history-source) text (:text source)
        profile (get-in (load-flow) [:flow/defaults :review/positive-bot-thread])
        collected (native-context/collect-context
                    {:read-json (partial resolution-json env)
                     :authorized-author? (partial resolution-writer-role env)
                     :read-history #(actionability-history/observations text %)
                     :preservation-policy profile} repo n)
        expected {:repo repo :repository-database-id (second (:repository profile))
                  :pr-number (js/Number n) :head head :thread-id thread-id}
        selection (native-context-law/select-fresh-context collected expected)]
    (when-let [reason (:refusal selection)]
      (throw (ex-info "Preservation fresh context refused" {:reason reason})))
    (let [thread (law/classify-thread (assoc (:selected selection)
                                           :actionability-policy (get-in (load-flow) [:flow/defaults :review/actionability])
                                           :actionability-observations (:history collected) :identities (reviewer-identities)))]
      {:collected collected :history-text text :history-source source :thread thread})))

(defn- preservation-authorization! [input body actor]
  (let [a (positive-thread/settlement-authorization (:thread input) body (assoc actor :authorized? true))]
    (when-not (:allowed? a)
      (throw (ex-info "Preservation lacks exact independent assessment, body or writer binding; no native write" a)))
    a))

(defn- append-preservation-observations! [repo input]
  (retain-resolution-observations! repo input (vec (:observations (positive-thread/proof (:thread input))))))

(defn- actual-reply [input response actor body]
  (let [c (get-in response [:data :addPullRequestReviewThreadReply :comment])
        observed (last (get-in input [:thread :native-context :thread :comments :nodes]))
        raw-thread (some #(when (= (get-in input [:thread :id]) (:id %)) %) (get-in input [:collected :raw-threads]))
        raw-writer (last (get-in raw-thread [:comments :nodes]))
        selected [:id :databaseId :url :body :author :createdAt :updatedAt]]
    (when (and (map? c) (= (select-keys c selected) (select-keys observed selected) (select-keys raw-writer selected))
               (= body (:body c))
               (= [(:login actor) (:id actor) (:node_id actor) "User"] (positive-thread/native-author (:author c)))
               (= (:databaseId c) (:writer-id (positive-thread/proof (:thread input)))))
      raw-writer)))

(defn- expected-reply-facts [before thread-id actual-native-comment]
  ;; Compare only an actual verified native append. No hypothetical comment or
  ;; generated native ID/time is ever classified or admitted as a settlement.
  (update (resolution-native-facts before) :raw-threads
          #(mapv (fn [t] (if (= thread-id (:id t))
                           (-> t (update-in [:comments :nodes] conj actual-native-comment)
                               (update-in [:comments :totalCount] inc)) t)) %)))

(defn- settle-preservation [repo n thread-id body]
  (let [details (positive-thread/settlement-details body)
        _ (when-not (and details (string? repo) (re-matches #"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+" repo)
                         (string? n) (re-matches #"[1-9][0-9]*" n)
                         (js/Number.isSafeInteger (js/Number n)) (<= (js/Number n) 2147483647))
            (throw (ex-info "Malformed preservation settlement; no native write" {})))
        env (resolution-environment) actor (resolution-writer env repo)
        initial (collect-preservation env repo n thread-id (:head details))
        ;; Retain actual qualified/revoked observations even if the prospective
        ;; body later refuses. No new native publication is invented.
        retained (append-preservation-observations! repo initial)
        authorization (preservation-authorization! initial body actor)
        fresh (collect-preservation env repo n thread-id (:head details))
        _ (when-not (and (= authorization (preservation-authorization! fresh body actor))
                         (= (dissoc (:collected initial) :history) (dissoc (:collected fresh) :history))
                         (= (:source retained) (:history-source fresh) (resolution-history-source))
                         (= (:history retained) (get-in fresh [:collected :history]))
                         (= actor (resolution-writer env repo))
                         (= (:source retained) (resolution-history-source)))
            (throw (ex-info "Preservation context, complete history, actor or permission changed before reply; no native write" {})))
        result {:repo repo :pr-number (js/Number n) :thread-id thread-id :authorization authorization :actor actor
                :approval-credit? false :coverage-credit? false :round-credit? false}
        reply-command (run-gh ["api" "graphql" "-f"
                        "query=mutation($t:ID!,$b:String!){addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId:$t,body:$b}){comment{id databaseId url body author{login __typename ... on User{id databaseId}} createdAt updatedAt}}}"
                        "-f" (str "t=" thread-id) "-f" (str "b=" body)] env)
        reply-response (try (resolution-value reply-command) (catch :default _ nil))
        written (try
                  (let [input (collect-preservation env repo n thread-id (:head details))
                        c (actual-reply input reply-response actor body)
                        proof (positive-thread/proof (:thread input))
                        retained (append-preservation-observations! repo input)]
                    (when-not (and c (:qualified? proof) (false? (get-in input [:thread :resolved?]))
                                   (= (expected-reply-facts fresh thread-id c) (resolution-native-facts input)))
                      (throw (ex-info "Preservation reply append/readback is not exact; no resolution attempted" {})))
                    {:input input :comment c :retained retained})
                  (catch :default e {:error (ex-message e)}))]
    (if (:error written)
      (assoc result :status :uncertain-native-outcome :mutation-attempt-count 1
             :reply-command reply-command :readback written :settlement-credit? false)
      (let [ready (try
                    (let [input (collect-preservation env repo n thread-id (:head details))
                          source (get-in written [:retained :source])]
                      (when-not (and (= (dissoc (get-in written [:input :collected]) :history)
                                        (dissoc (:collected input) :history))
                                     (= source (:history-source input) (resolution-history-source))
                                     (= (get-in written [:retained :history]) (get-in input [:collected :history]))
                                     (= actor (resolution-writer env repo)) (= source (resolution-history-source)))
                        (throw (ex-info "Preservation changed before resolution; no resolution attempted" {})))
                      {:input input})
                    (catch :default e {:error (ex-message e)}))]
        (if (:error ready)
          (assoc result :status :uncertain-native-outcome :mutation-attempt-count 1
                 :reply-command reply-command :readback ready :settlement-credit? false)
          (let [resolve-command (run-gh ["api" "graphql" "-f"
                                  "query=mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{id isResolved}}}"
                                  "-f" (str "t=" thread-id)] env)
                response (try (resolution-value resolve-command) (catch :default _ nil))
                readback (try
                           (let [input (collect-preservation env repo n thread-id (:head details))
                                 proof (positive-thread/proof (:thread input))
                                 retained (append-preservation-observations! repo input)]
                             {:exact-transition? (= (expected-resolved-facts (:input ready) thread-id)
                                                    (resolution-native-facts input))
                              :proof (dissoc proof :observations) :history-source (:source retained)})
                           (catch :default e {:error (ex-message e)}))
                confirmed? (and (= {:id thread-id :isResolved true} (get-in response [:data :resolveReviewThread :thread]))
                                (:exact-transition? readback) (get-in readback [:proof :settled?])
                                (= (get-in written [:comment :databaseId]) (get-in readback [:proof :writer-id])))]
            (assoc result :status (if confirmed? :handled-preservation :uncertain-native-outcome)
                   :mutation-attempt-count 2 :reply-command reply-command :resolve-command resolve-command
                   :readback readback :settlement-credit? (boolean confirmed?))))))))

(defn settle [repo n thread-id body]
  (let [body (if (= "-" body) (str (fs/readFileSync 0 "utf8")) body)]
    (if (positive-thread/settlement-attempt? body)
      (let [r (settle-preservation repo n thread-id body)]
        (prn r)
        (when (= :uncertain-native-outcome (:status r)) (set! (.-exitCode js/process) 3)))
      (settle-generic repo n thread-id body))))

(defn -main [& args]
  (let [[cmd repo n & more] args]
    (try
      (case cmd
        "flow" (show-flow repo)
        "status" (status repo n (reviewers-flag repo more))
        "threads" (let [{:keys [threads]} (fetch-threads repo n)]
                    (print-threads (if (some #{"--all"} more) threads (remove :resolved? threads))))
        "wait" (wait repo n (timing-flag more "--timeout" "1800") (timing-flag more "--interval" "30"))
        "request" (request repo n (first more) (flag more "--note" nil) (flag more "--reviewer" "coderabbit"))
        "settle" (settle repo n (first more) (second more))
        "resolve-informational" (let [r (resolve-informational repo n (first more) (second more) (drop 2 more))]
                                  (prn r)
                                  (when (= :uncertain-native-outcome (:status r)) (js/process.exit 3)))
        "gate" (gate repo n (some #{"--apply"} more) (flag more "--method" "merge") (reviewers-flag repo more))
        (do (println "usage: pr.cljs flow [STATE] | status|threads|wait|request|settle|resolve-informational|gate REPO PR ...") (js/process.exit 1)))
      (catch :default e
        (binding [*print-fn* *print-err-fn*] (println (ex-message e)))
        (js/process.exit 1)))))

(apply -main *command-line-args*)
