#!/usr/bin/env nbb
;; pr-flow CLI: thin effectful shell over pr-flow.law.
;;
;;   nbb -cp <skill>/scripts <skill>/scripts/pr.cljs <command> <owner/repo> <pr> [...]
;;
;; Commands
;;   status  REPO PR            gate summary: draft, checks, CodeRabbit, threads by severity
;;   threads REPO PR [--all]    review threads (unresolved by default) with id, severity, settlement
;;   wait    REPO PR [--timeout S] [--interval S]
;;                              poll until CodeRabbit is no longer pending; exit 0 done, 3 rate-limited, 4 timeout
;;   request REPO PR planning|code [--note TEXT]
;;                              post an @coderabbitai review request with the matching brief
;;   settle  REPO PR THREAD-ID BODY
;;                              reply to a thread, then resolve it; BODY must open with
;;                              Fixed|Deferred|Rejected|Handled (or "-" to read stdin)
;;   flow [STATE]               validate flow.edn; print the states, or one state's skill, exit and next states
;;   gate    REPO PR [--apply] [--method merge|squash|rebase]   (default merge: the user wants merge commits)
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

(defn- split-repo [repo]
  (let [[owner name] (str/split repo #"/")]
    (when-not (and owner name) (throw (ex-info "REPO must be owner/name" {:repo repo})))
    [owner name]))

;; --- reads ----------------------------------------------------------------

(def ^:private threads-query
  "query($owner:String!,$name:String!,$n:Int!){repository(owner:$owner,name:$name){pullRequest(number:$n){
     isDraft reviewThreads(first:100){nodes{id isResolved isOutdated path line
       comments(first:50){nodes{author{login} body url createdAt}}}}}}}")

(defn fetch-threads [repo n]
  (let [[owner name] (split-repo repo)
        data (gh-json "api" "graphql" "-f" (str "query=" threads-query)
                      "-F" (str "owner=" owner) "-F" (str "name=" name) "-F" (str "n=" n))
        pr (get-in data [:data :repository :pullRequest])]
    {:draft? (:isDraft pr)
     :threads (->> (get-in pr [:reviewThreads :nodes])
                   (mapv (fn [t]
                           (law/classify-thread
                            {:id (:id t) :resolved? (:isResolved t) :outdated? (:isOutdated t)
                             :path (:path t) :line (:line t)
                             :comments (mapv (fn [c] {:author (get-in c [:author :login]) :body (:body c)
                                                      :url (:url c) :created-at (:createdAt c)})
                                             (get-in t [:comments :nodes]))}))))}))

(defn fetch-heads
  "Current head SHA and the set of commit SHAs CodeRabbit reviews were posted on."
  [repo n]
  (let [head (str/trim (gh! "pr" "view" (str n) "-R" repo "--json" "headRefOid" "-q" ".headRefOid"))
        reviews (gh-json "api" (str "repos/" repo "/pulls/" n "/reviews") "--paginate")]
    {:head head
     :reviewed-heads (->> reviews
                          (filter #(re-find #"(?i)coderabbit" (str (get-in % [:user :login]))))
                          ;; Thread replies are also recorded as reviews; only a
                          ;; full pass carries the "Actionable comments posted" header.
                          (filter #(re-find #"(?i)actionable comments posted|no actionable comments" (str (:body %))))
                          (map :commit_id) set)}))

(defn fetch-checks [repo n]
  ;; `gh pr checks` exits 8 while checks are pending; that is data, not failure.
  (let [r (run-gh ["pr" "checks" (str n) "-R" repo "--json" "name,state,description"] (env-without-tokens))]
    (if (str/blank? (:out r))
      []
      (js->clj (js/JSON.parse (:out r)) :keywordize-keys true))))

(defn review-bodies-unanswered
  "CodeRabbit puts nitpicks and outside-diff findings in the review body, where
   they cannot be thread-resolved. Count reviews with such sections that were
   posted after our last settlement PR comment."
  [repo n]
  (let [reviews (gh-json "api" (str "repos/" repo "/pulls/" n "/reviews") "--paginate")
        flagged (->> reviews
                     (filter #(law/bot? (get-in % [:user :login])))
                     (filter #(re-find #"(?i)nitpick comments|outside diff range|duplicate comments" (str (:body %)))))
        comments (gh-json "api" (str "repos/" repo "/issues/" n "/comments") "--paginate")
        last-answer (->> comments
                         (remove #(law/bot? (get-in % [:user :login])))
                         (filter #(law/resolution-of (str/replace (str (:body %)) #"^\s*#+[^\n]*\n" "")))
                         (map :created_at) sort last)]
    (count (filter #(or (nil? last-answer) (pos? (compare (:submitted_at %) last-answer))) flagged))))

;; --- output ---------------------------------------------------------------

(defn print-threads [threads]
  (doseq [{:keys [id path line severity resolved? resolution reviewer comments]} threads]
    (println (str (name severity) "  " (if resolved? "resolved  " "OPEN      ")
                  (if resolution (name resolution) "-unsettled-") "  " reviewer "  " path ":" line))
    (println (str "    id=" id))
    (println (str "    " (law/title-of (:body (first comments)))))
    (println (str "    " (:url (first comments))))))

(defn status [repo n]
  (let [{:keys [draft? threads]} (fetch-threads repo n)
        checks (fetch-checks repo n)
        heads (fetch-heads repo n)
        gate (law/merge-gate (merge heads
                                    {:threads threads :checks checks
                                     :review-bodies-unanswered (review-bodies-unanswered repo n)}))]
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

(defn request [repo n kind note]
  (let [body (cond-> (or (get briefs kind) (throw (ex-info "kind must be planning|code" {:kind kind})))
               note (str "\n\n" note))]
    (println (str/trim (gh! "pr" "comment" (str n) "-R" repo "--body" body)))))

(defn settle [_repo _n thread-id body]
  (let [body (if (= body "-") (str (fs/readFileSync 0 "utf8")) body)]
    (when-not (law/resolution-of body)
      (throw (ex-info "BODY must open with Fixed|Deferred|Rejected|Handled" {:body (subs body 0 (min 60 (count body)))})))
    (gh! "api" "graphql" "-f" "query=mutation($t:ID!,$b:String!){addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId:$t,body:$b}){comment{url}}}"
         "-F" (str "t=" thread-id) "-f" (str "b=" body))
    (gh! "api" "graphql" "-f" "query=mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{isResolved}}}"
         "-F" (str "t=" thread-id))
    (println (str "settled " thread-id " as " (name (law/resolution-of body))))))

(defn gate [repo n apply? method]
  (let [g (status repo n)]
    (when (and apply? (:pass? g))
      (let [head (:head (fetch-heads repo n))]
        (gh! "pr" "ready" (str n) "-R" repo)
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

(defn show-flow [state]
  (let [f (load-flow)
        ps (flow/problems f)]
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

(defn -main [& args]
  (let [[cmd repo n & more] args]
    (try
      (case cmd
        "flow" (show-flow repo)
        "status" (status repo n)
        "threads" (let [{:keys [threads]} (fetch-threads repo n)]
                    (print-threads (if (some #{"--all"} more) threads (remove :resolved? threads))))
        "wait" (wait repo n (js/parseInt (flag more "--timeout" "1800")) (js/parseInt (flag more "--interval" "30")))
        "request" (request repo n (first more) (flag more "--note" nil))
        "settle" (settle repo n (first more) (second more))
        "gate" (gate repo n (some #{"--apply"} more) (flag more "--method" "merge"))
        (do (println "usage: pr.cljs flow [STATE] | status|threads|wait|request|settle|gate REPO PR ...") (js/process.exit 1)))
      (catch :default e
        (binding [*print-fn* *print-err-fn*] (println (ex-message e)))
        (js/process.exit 1)))))

(apply -main *command-line-args*)
