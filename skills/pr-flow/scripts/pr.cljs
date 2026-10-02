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
;;   request REPO PR planning|code [--note TEXT]
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
            [clojure.edn :as edn]
            [nbb.core :refer [*file*]]
            [clojure.string :as str]
            [pr-flow.flow :as flow]
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

(defn- run-gh [args env]
  (let [r (cp/spawnSync "gh" (clj->js args) #js {:encoding "utf8" :env env :maxBuffer (* 64 1024 1024)})]
    {:exit (.-status r) :out (str (.-stdout r)) :err (str (.-stderr r))}))

(defn gh!
  "Run gh; return stdout. Throws with stderr on failure."
  [& args]
  (let [r (run-gh args js/process.env)
        r (if (and (not= 0 (:exit r))
                   (re-find #"Resource not accessible by (personal access|integration) token" (:err r)))
            (run-gh args (env-without-tokens))
            r)]
    (when-not (= 0 (:exit r))
      (throw (ex-info (str "gh " (str/join " " (take 3 args)) " failed: " (str/trim (:err r))) r)))
    (:out r)))

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
  "query($owner:String!,$name:String!,$n:Int!,$after:String){repository(owner:$owner,name:$name){pullRequest(number:$n){
     isDraft reviewThreads(first:100,after:$after){pageInfo{hasNextPage endCursor}
       nodes{id isResolved isOutdated path line
         comments(first:100){pageInfo{hasNextPage} nodes{author{login} body url createdAt}}}}}}}")

(defn- threads-page [owner name n after]
  (let [args (cond-> ["api" "graphql" "-f" (str "query=" threads-query)
                      "-F" (str "owner=" owner) "-F" (str "name=" name) "-F" (str "n=" n)]
               after (into ["-f" (str "after=" after)]))]
    (get-in (apply gh-json args) [:data :repository :pullRequest])))

(defn fetch-threads
  "All review threads, following reviewThreads pagination. A thread whose
   comments exceed one page marks the result :incomplete? so the gate fails
   closed instead of judging a partial conversation."
  [repo n]
  (let [[owner name] (split-repo repo)]
    (loop [after nil acc [] draft? nil]
      (let [pr (threads-page owner name n after)
            conn (:reviewThreads pr)
            acc (into acc (:nodes conn))]
        (if (get-in conn [:pageInfo :hasNextPage])
          (recur (get-in conn [:pageInfo :endCursor]) acc (:isDraft pr))
          {:draft? (if (nil? draft?) (:isDraft pr) draft?)
           :incomplete? (boolean (some #(get-in % [:comments :pageInfo :hasNextPage]) acc))
           :threads (mapv (fn [t]
                            (law/classify-thread
                             {:id (:id t) :resolved? (:isResolved t) :outdated? (:isOutdated t)
                              :path (:path t) :line (:line t)
                              :comments (mapv (fn [c] {:author (get-in c [:author :login]) :body (:body c)
                                                       :url (:url c) :created-at (:createdAt c)})
                                              (get-in t [:comments :nodes]))}))
                          acc)})))))

(defn reviewer-key
  "Normalise a review author login to a reviewer name used in --reviewers."
  [login]
  (let [l (str/lower-case (str login))]
    (cond (re-find #"coderabbit" l) "coderabbit"
          (re-find #"codex" l) "codex"
          :else (str/replace l #"\[bot\]$" ""))))

(defn- full-review?
  "Thread replies are also recorded as reviews. CodeRabbit's full pass carries
   an 'Actionable comments posted' header; other reviewers count when the
   review has a body or a decisive state."
  [{:keys [body state user]}]
  (if (= "coderabbit" (reviewer-key (:login user)))
    (boolean (re-find #"(?i)actionable comments posted|no actionable comments" (str body)))
    (or (not (str/blank? body)) (#{"APPROVED" "CHANGES_REQUESTED"} state))))

(declare authorized-author?)

(defn fetch-heads
  "Current head SHA and per-reviewer evidence, including CodeRabbit's
   no-findings completion reply when no REST review record was created."
  [repo n]
  (let [head (str/trim (gh! "pr" "view" (str n) "-R" repo "--json" "headRefOid" "-q" ".headRefOid"))
        reviews (gh-pages (str "repos/" repo "/pulls/" n "/reviews"))
        comments (->> (gh-pages (str "repos/" repo "/issues/" n "/comments"))
                      (mapv (fn [c]
                              (let [login (get-in c [:user :login])]
                                {:author login :body (:body c) :created_at (:created_at c)
                                 :trusted? (and (str/includes? (str (:body c)) (str "pr-flow-review:" head))
                                                (authorized-author? repo login))}))))
        acknowledged? (law/completed-no-findings-review? head comments)]
    {:head head
     :reviewed-heads (cond-> (->> reviews
                                  (filter full-review?)
                                  (reduce (fn [m r] (update m (reviewer-key (get-in r [:user :login])) (fnil conj #{}) (:commit_id r)))
                                          {}))
                       acknowledged? (update "coderabbit" (fnil conj #{}) head))}))

(defn fetch-checks
  "PR check rows. `gh pr checks` exits 8 while checks are pending; that is data,
   not failure. Tries the normal environment first (token-only CI needs it) and
   falls back to the keyring only on a token-permission refusal. Any other
   failure throws, so a blank result never reads as 'no CodeRabbit'."
  [repo n]
  (let [args ["pr" "checks" (str n) "-R" repo "--json" "name,state,description"]
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
                         (re-find #"no checks reported" (:err required)) #{}
                         :else (throw (ex-info "Cannot identify required checks" required)))]
    (cond
      (ok? r) (mapv #(assoc % :required? (contains? required-names (:name %)))
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
  "CodeRabbit puts nitpicks and outside-diff findings in the review body, where
   they cannot be thread-resolved. Count reviews with such sections that were
   lack an itemized PR comment naming that review's ID."
  [repo n]
  (let [reviews (gh-pages (str "repos/" repo "/pulls/" n "/reviews"))
        flagged (->> reviews
                     (filter #(law/bot? (get-in % [:user :login])))
                     (filter #(re-find #"(?i)nitpick comments|outside diff range|duplicate comments" (str (:body %)))))
        comments (->> (gh-pages (str "repos/" repo "/issues/" n "/comments"))
                      (filter #(and (re-find #"(?i)review-id:" (str (:body %)))
                                    (authorized-author? repo (get-in % [:user :login])))))]
    (law/unanswered-review-count flagged comments)))

;; --- output ---------------------------------------------------------------

(defn print-threads [threads]
  (doseq [{:keys [id path line severity resolved? resolution reviewer comments]} threads]
    (println (str (name severity) "  " (if resolved? "resolved  " "OPEN      ")
                  (if resolution (name resolution) "-unsettled-") "  " reviewer "  " path ":" line))
    (println (str "    id=" id))
    (println (str "    " (law/title-of (:body (first comments)))))
    (println (str "    " (:url (first comments))))))

(defn status [repo n reviewers]
  (let [{:keys [draft? threads incomplete?]} (fetch-threads repo n)
        checks (fetch-checks repo n)
        heads (fetch-heads repo n)
        gate (assoc (law/merge-gate (merge heads
                                    {:threads threads :checks checks :incomplete? incomplete?
                                     :required-reviewers reviewers
                                     :review-bodies-unanswered (review-bodies-unanswered repo n)})) :draft? draft?)]
    (println (str repo "#" n (when draft? "  [draft]") "  head " (subs (:head heads) 0 7)))
    (println (str "  coderabbit: " (name (:coderabbit gate)) "   checks: " (pr-str (:checks gate))))
    (println (str "  threads: " (count threads) " total, " (count (remove :resolved? threads)) " unresolved; by severity "
                  (pr-str (frequencies (map :severity (remove :resolved? threads))))))
    (println (str "  gate: " (if (:pass? gate) "PASS" "BLOCKED")))
    (doseq [r (:reasons gate)] (println (str "    - " r)))
    gate))

;; --- writes ---------------------------------------------------------------

(def briefs
  {"planning" (str "@coderabbitai review\n\n"
                   "This PR carries agile artifacts. Review it like sprint planning: "
                   "(1) is the outcome and scope clear, (2) are acceptance criteria testable, "
                   "(3) is each estimate fair or should a card split into substories, "
                   "(4) are epic/parent/blocked_by links correct and complete, "
                   "(5) what risks or non-goals are missing. Label each finding P0-P3.")
   "code" (str "@coderabbitai review\n\n"
               "Review the implementation against the card's laws and acceptance criteria. "
               "Label each finding P0-P3; P0/P1 will be fixed before merge, others fixed, deferred or rejected with a reason.")})

(declare load-flow)

(defn request [repo n kind note]
  (let [reviews (gh-pages (str "repos/" repo "/pulls/" n "/reviews"))
        full (filter #(and (= "coderabbit" (reviewer-key (get-in % [:user :login])))
                           (full-review? %)) reviews)
        comments (gh-pages (str "repos/" repo "/issues/" n "/comments"))
        markers (keep (fn [c] (when (authorized-author? repo (get-in c [:user :login]))
                                (when-let [[_ stage] (re-find #"<!-- pr-flow-stage:(planning|code) -->" (str (:body c)))]
                                  {:stage stage :created_at (:created_at c)}))) comments)
        rounds (law/stage-review-rounds full markers kind)
        open-findings (count (remove :resolved? (:threads (fetch-threads repo n))))
        max-loops (get-in (load-flow) [:flow/defaults :review/max-loops])
        verdict (law/loop-verdict {:rounds rounds :open-blockers open-findings :max-loops max-loops})
        body (cond-> (or (get briefs kind) (throw (ex-info "kind must be planning|code" {:kind kind})))
               note (str "\n\n" note))
        head (str/trim (gh! "pr" "view" (str n) "-R" repo "--json" "headRefOid" "-q" ".headRefOid"))
        body (str body "\n\n<!-- pr-flow-stage:" kind " --> <!-- pr-flow-review:" head " -->")]
    (when (= :escalate verdict)
      (throw (ex-info "Review loop budget exhausted; escalate open findings instead of requesting another review"
                      {:rounds rounds :open-findings open-findings :max-loops max-loops})))
    (println (str/trim (gh! "pr" "comment" (str n) "-R" repo "--body" body)))))

(defn settle [repo n thread-id body]
  (let [body (if (= body "-") (str (fs/readFileSync 0 "utf8")) body)]
    (when-not (law/resolution-of body)
      (throw (ex-info "BODY must open with Fixed|Deferred|Rejected|Handled" {:body (subs body 0 (min 60 (count body)))})))
    (when-not (some #(= thread-id (:id %)) (:threads (fetch-threads repo n)))
      (throw (ex-info "Thread does not belong to the requested PR" {:repo repo :pr n :thread-id thread-id})))
    (gh! "api" "graphql" "-f" "query=mutation($t:ID!,$b:String!){addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId:$t,body:$b}){comment{url}}}"
         "-F" (str "t=" thread-id) "-f" (str "b=" body))
    (gh! "api" "graphql" "-f" "query=mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{isResolved}}}"
         "-F" (str "t=" thread-id))
    (println (str "settled " thread-id " as " (name (law/resolution-of body))))))

(defn gate [repo n apply? method reviewers]
  (let [g (status repo n reviewers)]
    (when (and apply? (:pass? g))
      (when (:draft? g) (gh! "pr" "ready" (str n) "-R" repo))
      (let [post-ready (if (:draft? g) (status repo n reviewers) g)
            head (:head post-ready)]
        (when-not (and (:pass? post-ready) (= head (:head g)))
          (throw (ex-info "Ready transition changed head or started new checks; gate blocked" {:before g :after post-ready})))
      (try
        (gh! "pr" "merge" (str n) "-R" repo "--auto" (str "--" method) "--match-head-commit" head)
        (println (str "ready + auto-merge (" method ") enabled on " head))
        (catch :default e
          ;; Repos without allow_auto_merge or without required checks refuse
          ;; --auto. The gate above already verified checks, threads and
          ;; review on this exact head, so merge now, pinned to that head.
          (if (re-find #"(?i)auto merge is not allowed|clean status|not allowed for this repository" (ex-message e))
            (do (gh! "pr" "merge" (str n) "-R" repo (str "--" method) "--match-head-commit" head)
                (println (str "auto-merge unavailable; merged (" method ") at gated head " head)))
            (throw e))))))
    (when (and apply? (not (:pass? g)))
      (js/process.exit 2))))

(defn wait [repo n timeout interval]
  (let [deadline (+ (js/Date.now) (* 1000 timeout))]
    (letfn [(tick []
              (let [st (law/coderabbit-state (fetch-checks repo n))]
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
  (if-let [i (some (fn [[i a]] (when (= a f) i)) (map-indexed vector args))]
    (nth args (inc i) default)
    default))

(defn- reviewers-flag
  "--reviewers coderabbit,codex → #{\"coderabbit\" \"codex\"}; default from flow.edn."
  [repo args]
  (let [defaults (:flow/defaults (load-flow))
        requested (if-let [v (flag args "--reviewers" nil)]
                    (set (remove str/blank? (str/split v #",")))
                    nil)]
    (law/required-reviewers-for defaults repo requested)))

(defn -main [& args]
  (let [[cmd repo n & more] args]
    (try
      (case cmd
        "flow" (show-flow repo)
        "status" (status repo n (reviewers-flag repo more))
        "threads" (let [{:keys [threads]} (fetch-threads repo n)]
                    (print-threads (if (some #{"--all"} more) threads (remove :resolved? threads))))
        "wait" (wait repo n (js/parseInt (flag more "--timeout" "1800")) (js/parseInt (flag more "--interval" "30")))
        "request" (request repo n (first more) (flag more "--note" nil))
        "settle" (settle repo n (first more) (second more))
        "gate" (gate repo n (some #{"--apply"} more) (flag more "--method" "merge") (reviewers-flag repo more))
        (do (println "usage: pr.cljs flow [STATE] | status|threads|wait|request|settle|gate REPO PR ...") (js/process.exit 1)))
      (catch :default e
        (binding [*print-fn* *print-err-fn*] (println (ex-message e)))
        (js/process.exit 1)))))

(apply -main *command-line-args*)
