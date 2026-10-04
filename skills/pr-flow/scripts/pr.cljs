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
            ["crypto" :as crypto]
            [clojure.edn :as edn]
            [edamame.core :as edamame]
            [nbb.core :refer [*file*]]
            [clojure.string :as str]
            [pr-flow.flow :as flow]
            [pr-flow.actionability :as actionability]
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

(defn- split-repo [repo]
  (let [[owner name] (str/split repo #"/")]
    (when-not (and owner name) (throw (ex-info "REPO must be owner/name" {:repo repo})))
    [owner name]))

;; --- reads ----------------------------------------------------------------

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

(defn- actionability-ledger [] (path/resolve here ".." ".." ".." ".ημ" "receipts.edn"))
(defn- actionability-observations []
  ;; nil is unavailable, distinct from an existing valid empty ledger ([]).
  ;; Read every complete record before using any history; never skip bad lines.
  (try
    (let [file (actionability-ledger)]
      (when (fs/existsSync file)
        (vec (mapcat (fn [line]
                       (let [forms (edamame/parse-string-all line)
                             receipt (first forms)]
                         (when-not (and (= 1 (count forms)) (map? receipt))
                           (throw (ex-info "Invalid receipt record" {})))
                         (when (= "pr-flow-actionability-observation" (:origin receipt))
                           (when-not (and (vector? (:decisions receipt))
                                          (every? #(and (map? %) (= :thread-actionability (:purpose %))) (:decisions receipt)))
                             (throw (ex-info "Invalid actionability observation record" {})))
                           (:decisions receipt))))
                     (remove str/blank? (str/split-lines (fs/readFileSync file "utf8")))))))
    (catch :default _ nil)))
(defn- append-actionability-observations! [observations]
  (when (seq observations)
    (let [file (actionability-ledger)]
      (when-not (fs/existsSync file)
        (throw (ex-info "Canonical actionability receipt ledger unavailable; no disposition admitted" {:path file})))
      (doseq [o observations]
        (fs/appendFileSync file
          (str (pr-str {:ts (.toISOString (js/Date.)) :kind :observation
                        :origin "pr-flow-actionability-observation" :owner "pr-flow-cli"
                        :dod "Preserve native actionability admission/revocation append-only"
                        :pi "pr-flow/actionability" :host "local-cli-native-github"
                        :manifest [] :refs [(:repo-id o) (:pr-id o) (:thread-id o) (:head o)]
                        :decisions [o] :note "Observed disposition is not settlement, approval, coverage, or round credit."}) "\n"))))))

(defn- fetch-issue-comments [repo n]
  (let [pages (gh-json "api" (str "repos/" repo "/issues/" n "/comments") "--paginate" "--slurp")]
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

(defn- threads-page [owner name n after]
  (let [args (cond-> ["api" "graphql" "-f" (str "query=" threads-query)
                      "-f" (str "owner=" owner) "-f" (str "name=" name) "-F" (str "n=" n)]
               after (into ["-f" (str "after=" after)]))
        response (apply gh-json args)
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

(declare authorized-author? reviewer-identities load-flow)

(defn fetch-threads
  "All review threads, following reviewThreads pagination. A thread whose
   comments exceed one page marks the result :incomplete? so the gate fails
   closed instead of judging a partial conversation. Native PR issue-comment
   context is collected completely or supplied by the same approval snapshot."
  ([repo n] (fetch-threads repo n (fetch-issue-comments repo n)))
  ([repo n issue-comments]
  (let [[owner name] (split-repo repo)
        authorized? (memoize (partial authorized-author? repo))
        issue-comments (mapv #(cond-> (assoc (hash-body %) :source-channel :github-issue-comment)
                               (= "User" (get-in % [:user :type]))
                               (assoc :authorized? (boolean (authorized? (get-in % [:user :login]))))) issue-comments)
        history (actionability-observations)
        policy (get-in (load-flow) [:flow/defaults :review/actionability])]
    (loop [after nil acc [] draft? nil]
      (let [pr (threads-page owner name n after)
            conn (:reviewThreads pr)
            acc (into acc (:nodes conn))]
        (if (get-in conn [:pageInfo :hasNextPage])
          (recur (get-in conn [:pageInfo :endCursor]) acc (:isDraft pr))
          (let [threads (mapv (fn [t]
                               (law/classify-thread
                                (merge (hydrate-actionability-context (:native-repository pr) pr t)
                                 {:id (:id t) :resolved? (:isResolved t) :outdated? (:isOutdated t)
                                  :path (:path t) :line (:line t)
                                  :head (:headRefOid pr) :pr-author (get-in pr [:author :login])
                                  :actionability-policy policy :actionability-observations history
                                  :identities (reviewer-identities)
                                  :root-comment-id (get-in t [:comments :nodes 0 :databaseId])
                                  :issue-comments issue-comments
                                  :comments (mapv (fn [c] {:id (:databaseId c) :author (get-in c [:author :login]) :body (:body c)
                                                           :user {:login (get-in c [:author :login]) :type (get-in c [:author :__typename])}
                                                           :authorized? (boolean (authorized? (get-in c [:author :login])))
                                                           :url (:url c) :created-at (:createdAt c) :updated-at (:updatedAt c)})
                                                  (get-in t [:comments :nodes]))}))) acc)
                observations (vec (distinct (mapcat #(get-in % [:actionability :observations]) threads)))
                _ (append-actionability-observations! observations)
                history (when (some? history) (into history observations))]
           {:draft? (if (nil? draft?) (:isDraft pr) draft?)
           :head (:headRefOid pr) :pr-author (get-in pr [:author :login])
           :actionability-history-available? (some? history)
           :incomplete? (boolean (some #(get-in % [:comments :pageInfo :hasNextPage]) acc))
           ;; Normalize our own append before snapshot comparison. Native raw
           ;; records remain unchanged; a subsequent actual mutation still blocks.
           :threads (mapv #(law/classify-thread (assoc % :actionability-observations history)) threads)})))))))

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

(defn fetch-heads
  "Collect hosted approval, commit binding and incomplete scope separately."
  [repo n]
  (let [head (str/trim (gh! "pr" "view" (str n) "-R" repo "--json" "headRefOid" "-q" ".headRefOid"))
        reviews (gh-pages (str "repos/" repo "/pulls/" n "/reviews"))
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
      (#{"admin" "maintain" "write"} (:permission (gh-json "api" (str "repos/" repo "/collaborators/" login "/permission"))))
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
                 rejection-channel rejection-source-id rejection-url actionability opener-withdrawal]} threads]
    (println (str (name severity) "  " (if resolved? "resolved  " "OPEN      ")
                  (if resolution (name resolution) "-unsettled-") "  " reviewer "  " path ":" line))
    (println (str "    id=" id))
    (println (str "    " (law/title-of (:body (first comments)))))
    (println (str "    " (:url (first comments))))
    (when rejection-channel
      (println (str "    rejection evidence: " (name rejection-channel) " id=" rejection-source-id " " rejection-url)))
    (when opener-withdrawal
      (println (str "    native opener withdrawal: id=" (:source-id opener-withdrawal) " " (:url opener-withdrawal))))
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

(defn settle [repo n thread-id body]
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
        "gate" (gate repo n (some #{"--apply"} more) (flag more "--method" "merge") (reviewers-flag repo more))
        (do (println "usage: pr.cljs flow [STATE] | status|threads|wait|request|settle|gate REPO PR ...") (js/process.exit 1)))
      (catch :default e
        (binding [*print-fn* *print-err-fn*] (println (ex-message e)))
        (js/process.exit 1)))))

(apply -main *command-line-args*)
