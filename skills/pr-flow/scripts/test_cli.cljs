#!/usr/bin/env nbb
(ns test-cli
  (:require ["fs" :as fs] ["os" :as os] ["path" :as path] ["child_process" :as cp]
            [cljs.test :refer [deftest is run-tests]] [clojure.string :as str]
            [nbb.core :refer [*file*]]))
(def here (path/dirname *file*))
(def head (apply str (repeat 40 "a")))
(def other (apply str (repeat 40 "b")))
(def approval {:id 1 :user {:login "eta-mu-ai[bot]" :type "Bot"} :state "APPROVED"
               :commit_id head :submitted_at "2026-10-03T01:00:00Z" :body "Approved"})
(defn completed-stage-comments [rounds sha stage]
  (vec (mapcat (fn [i]
                 (conj (mapv (fn [[offset provider mention]]
                               {:id (+ 100 (* 10 i) offset) :user {:login "riatzukiza" :type "User"}
                                :created_at (str "2026-10-03T00:0" i ":0" offset "Z")
                                :body (str mention " <!-- pr-flow-stage:" stage " --> <!-- pr-flow-review:" sha
                                           " --> <!-- pr-flow-reviewer:" provider " --> <!-- pr-flow-round:" (inc i) " -->")})
                             [[0 "coderabbit" "@coderabbitai full review"] [1 "codex" "@codex review"] [2 "mimo" "MiMo hosted review"]])
                       {:id (+ 103 (* 10 i)) :user {:login "coderabbitai[bot]" :type "Bot"}
                        :created_at (str "2026-10-03T00:0" i ":10Z") :body "Full review finished."}))
               (range rounds))))
(defn completed-stage-reviews [rounds sha]
  ;; CodeRabbit uses its native no-REST completion; the other configured
  ;; participants complete each shared cohort through their hosted records.
  (vec (mapcat (fn [i]
                 (mapv (fn [[offset login]]
                         {:id (+ 1000 (* 10 i) offset) :user {:login login :type "Bot"} :state "COMMENTED"
                          :commit_id sha :submitted_at (str "2026-10-03T00:0" i ":2" offset "Z")
                          :body "Confirmed findings: none."})
                       [[0 "chatgpt-codex-connector[bot]"] [1 "eta-mu-ai[bot]"]])) (range rounds))))
(def base {:head head :reviews [approval] :comments []
           ;; Old-head completions meet the stage floor without manufacturing
           ;; a current-head approval or suppressing a current-head request.
           :prior-stage-rounds 5
           :checks [{:name "laws" :state "SUCCESS" :required true}
                    {:name "CodeRabbit" :state "SKIPPED" :description "Review skipped"}]})
(defn execute [config & args]
  (let [tmp (fs/mkdtempSync (path/join (os/tmpdir) "pr-flow-cli-"))
        data (path/join tmp "data.json") gh (path/join tmp "gh")
        rounds (:prior-stage-rounds config 0)
        history (completed-stage-reviews rounds other)
        config (cond-> (-> config
                           (assoc :comments (into (completed-stage-comments rounds other (:prior-stage config "code")) (:comments config)))
                           (update :reviews #(into history %))
                           (dissoc :prior-stage-rounds :prior-stage))
                 (:reviewsSequence config) (update :reviewsSequence #(mapv (fn [reviews] (into history reviews)) %)))
        env (js/Object.assign #js {} js/process.env #js {:PR_FLOW_TEST_DATA data :PATH (str tmp ":" (.-PATH js/process.env))})]
    (try
      (when (:testToken config) (aset env "GH_TOKEN" "fixture-token") (aset env "GITHUB_TOKEN" "fixture-token"))
      (fs/writeFileSync data (js/JSON.stringify (clj->js config)))
      (fs/copyFileSync (path/join here "fixture-gh.cjs") gh) (fs/chmodSync gh 493)
      (let [r (cp/spawnSync "nbb" (clj->js (into ["-cp" here (path/join here "pr.cljs")] args)) #js {:env env :encoding "utf8" :timeout 5000})
            calls-path (str data ".calls")
            calls (if (fs/existsSync calls-path)
                    (mapv #(js->clj (js/JSON.parse %) :keywordize-keys true)
                          (remove str/blank? (str/split-lines (fs/readFileSync calls-path "utf8")))) [])]
        {:exit (.-status r) :out (str (.-stdout r)) :err (str (.-stderr r)) :calls calls})
      (finally (fs/rmSync tmp #js {:recursive true :force true})))))
(defn writes [result verb]
  (filter #(= ["pr" verb] (vec (take 2 (:args %)))) (:calls result)))
(defn mutations [result]
  (filter #(and (= ["api" "graphql"] (vec (take 2 (:args %))))
                (some (fn [arg] (str/starts-with? arg "query=mutation")) (:args %))) (:calls result)))

(deftest hosted-approvals-wired-through-cli
  (let [r (execute base "status" "riatzukiza/.agents" "8")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "gate: PASS"))
    (is (str/includes? (:out r) "completed code rounds: 5"))
    (is (str/includes? (:out r) "coderabbit: skipped")))
  (doseq [config [(assoc-in base [:reviews 0 :state] "COMMENTED")
                  (assoc-in base [:reviews 0 :commit_id] other)
                  (assoc-in base [:reviews 0 :user :login] "fake-eta-mu-ai[bot]")
                  (assoc base :heads [head other])
                  (assoc-in base [:checks 0 :state] "FAILURE")]]
    (is (str/includes? (:out (execute config "status" "riatzukiza/.agents" "8")) "gate: BLOCKED")))
  (is (str/includes? (:out (execute base "status" "riatzukiza/.agents" "8" "--reviewers" "codex")) "gate: BLOCKED")))

(deftest cli-formal-approval-with-admitted-partial-scope-stays-blocked
  (let [partial (assoc approval :body (str "The staged diff was truncated at 31 of 85 files; "
                                          "the truncated tail was bound to deterministic gates rather than exhaustively read.\nConfirmed findings: none."))
        r (execute (assoc base :reviews [partial]) "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 2 (:exit r)))
    (is (str/includes? (:out r) "gate: BLOCKED"))
    (is (str/includes? (:out r) "incomplete review scope:"))
    (is (str/includes? (:out r) ":unreviewed-input"))
    (is (empty? (writes r "merge")))))

(deftest cli-head-guard-and-no-bypass
  (let [ok (execute base "gate" "riatzukiza/.agents" "8" "--apply")
        stale (execute (assoc base :heads [head other]) "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 0 (:exit ok)) (:err ok))
    (is (= 1 (count (writes ok "merge"))))
    (is (some #(= ["--match-head-commit" head] (vec %))
              (partition 2 1 (:args (first (writes ok "merge"))))))
    (is (= 2 (:exit stale)))
    (is (empty? (writes stale "merge")))))

(deftest github-no-required-checks-is-an-explicit-empty-set
  (let [r (execute (assoc base :checks [{:name "laws" :state "SUCCESS"}]
                         :requiredMessage "no required checks reported on the 'feat/example' branch")
                   "status" "riatzukiza/.agents" "8")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "gate: PASS"))))

(deftest codex-verdict-resolves-the-provider-commit
  (let [comment {:user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
                 :updated_at "2026-10-03T01:00:00Z"
                 :body (str "Codex Review: Didn't find any major issues. :tada:\n\n**Reviewed commit:** `" (subs head 0 10) "`")}
        config (assoc base :reviews [] :comments [comment])
        ok (execute config "status" "open-hax/knoxx" "382")]
    ;; The user's default quorum one applies to Knoxx too.
    (is (str/includes? (:out ok) "gate: PASS"))
    (is (some #(str/ends-with? (second (:args %)) (str "/commits/" (subs head 0 10))) (:calls ok)))
    (is (str/includes? (:out (execute config "status" "riatzukiza/.agents" "8")) "gate: PASS"))
    (is (str/includes? (:out (execute (assoc config :resolvedCommit other) "status" "riatzukiza/.agents" "8")) "gate: BLOCKED"))))

(deftest requests-are-full-literal-and-deduplicated
  (let [note "Literal $() and `backticks`\nsecond line"
        r (execute (assoc base :checks [{:name "laws" :state "SUCCESS" :required true}])
                   "request" "riatzukiza/.agents" "8" "code" "--note" note)
        posted (first (writes r "comment"))]
    (is (= 0 (:exit r)) (:err r))
    (is (= 1 (count (writes r "comment"))))
    (is (str/starts-with? (:input posted "") "@coderabbitai full review"))
    (is (str/includes? (:input posted "") note))
    (is (str/includes? (:input posted "") (str "pr-flow-review:" head)))
    (is (= ["--body-file" "-"] (vec (take-last 2 (:args posted))))))
  (let [config (assoc base :comments [{:user {:login "riatzukiza" :type "User"}
                                      :created_at "2026-10-03T01:00:00Z"
                                      :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->")}])
        r (execute config "request" "riatzukiza/.agents" "8" "code")]
    (is (str/includes? (:out r) "pending"))
    (is (empty? (writes r "comment")))))

(deftest seventh-round-can-post-when-no-request-is-pending
  (let [reviews (mapv #(assoc approval :id % :user {:login "coderabbitai[bot]" :type "Bot"}
                             :body "Actionable comments posted: 1") (range 1 7))
        r (execute (assoc base :prior-stage-rounds 6 :reviews reviews) "request" "riatzukiza/.agents" "8" "code")]
    (is (= 0 (:exit r)) (:err r))
    (is (= 1 (count (writes r "comment"))))
    (is (str/includes? (:input (first (writes r "comment")) "") "pr-flow-round:7"))))

(deftest later-rounds-preserve-pending-and-cooldown-guards
  (let [request {:id 1000 :user {:login "riatzukiza" :type "User"}
                 :created_at "2026-10-03T01:02:00Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-stage:code --> <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:coderabbit -->")}
        pending (execute (assoc base :comments [request]) "request" "riatzukiza/.agents" "8" "code")
        limit {:user {:login "coderabbitai[bot]" :type "Bot"} :updated_at (.toISOString (js/Date.))
               :body "Review limit reached.\nNext included review available in 57 minutes."}
        cooldown (execute (assoc base :comments [request limit]) "request" "riatzukiza/.agents" "8" "code")]
    (is (str/includes? (:out pending) "pending"))
    (is (empty? (writes pending "comment")))
    (is (str/includes? (:out cooldown) "cooldown"))
    (is (empty? (writes cooldown "comment")))))

(deftest one-pass-unanimous-native-approval-can-merge
  (let [reviews [approval
                 (assoc approval :id 2 :user {:login "coderabbitai[bot]" :type "Bot"}
                        :body "No actionable comments were generated.")
                 (assoc approval :id 3 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
                        :body "No confirmed findings.")]
        config (dissoc (assoc base :reviews reviews) :prior-stage-rounds)
        r (execute config "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "gate: PASS"))
    (is (str/includes? (:out r) "completed code rounds: 1"))
    (is (= 1 (count (writes r "merge"))))
    (doseq [incomplete [(assoc config :reviews [approval (second reviews)])
                        (-> config (assoc-in [:reviews 2 :state] "COMMENTED")
                            (assoc-in [:reviews 2 :body] "Acknowledged."))
                        (assoc-in config [:reviews 2 :commit_id] other)
                        (assoc-in config [:reviews 2 :user :type] "User")]]
      (let [blocked (execute incomplete "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= 2 (:exit blocked)))
        (is (str/includes? (:out blocked) "gate: BLOCKED"))
        (is (empty? (writes blocked "merge")))))))

(deftest fewer-than-five-nonunanimous-rounds-do-not-merge
  (doseq [prior-rounds [0 1 4]]
    (let [r (execute (assoc base :prior-stage-rounds prior-rounds)
                     "gate" "riatzukiza/.agents" "8" "--apply")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:out r) "gate: BLOCKED"))
      (is (empty? (writes r "merge")))))
  (let [code-request {:user {:login "riatzukiza" :type "User"} :created_at "2026-10-03T00:59:00Z"
                      :body (str "@coderabbitai full review <!-- pr-flow-stage:code --> <!-- pr-flow-review:" head
                                 " --> <!-- pr-flow-reviewer:coderabbit --> <!-- pr-flow-round:1 -->")}
        planning-only (execute (assoc base :prior-stage "planning" :comments [code-request])
                               "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 2 (:exit planning-only)))
    (is (str/includes? (:out planning-only) "completed code rounds: 0"))
    (is (empty? (writes planning-only "merge")))))

(deftest missing-and-acknowledging-participants-do-not-complete-cohorts
  (let [history (completed-stage-reviews 5 other)
        mimo (filter #(= "eta-mu-ai[bot]" (get-in % [:user :login])) history)
        acknowledgements (map #(assoc % :body "Queued for review.")
                              (filter #(= "chatgpt-codex-connector[bot]" (get-in % [:user :login])) history))]
    (doseq [reviews [(into [approval] mimo) (into (into [approval] mimo) acknowledgements)]]
      (let [config (-> base (dissoc :prior-stage-rounds)
                       (assoc :comments (completed-stage-comments 5 other "code") :reviews reviews))
            r (execute config "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= 2 (:exit r)))
        (is (str/includes? (:out r) "completed code rounds: 0"))
        (is (empty? (writes r "merge")))))))

(deftest body-only-human-changes-request-stays-blocked
  (let [human {:id 10 :user {:login "human-reviewer" :type "User"} :state "CHANGES_REQUESTED"
               :commit_id head :submitted_at "2026-10-03T01:01:00Z" :body "P1: authorization is bypassed"}
        config (assoc base :reviews [approval human])
        fixed {:user {:login "riatzukiza" :type "User"} :created_at "2026-10-03T01:02:00Z"
               :body "Handled: review-id:10\n- Fixed review-body:10: authorization regression now passes"}]
    (is (str/includes? (:out (execute config "status" "riatzukiza/.agents" "8")) "gate: BLOCKED"))
    (is (str/includes? (:out (execute (assoc config :comments [fixed]) "status" "riatzukiza/.agents" "8")) "gate: PASS"))
    (is (str/includes? (:out (execute (assoc config :comments [fixed] :authorized false) "status" "riatzukiza/.agents" "8")) "gate: BLOCKED"))
    (is (str/includes? (:out (execute (assoc config :reviews [approval (assoc human :commit_id other)])
                                    "status" "riatzukiza/.agents" "8")) "gate: BLOCKED"))
    (is (str/includes? (:out (execute (assoc config :reviews [approval human (assoc human :id 11 :state "APPROVED" :submitted_at "2026-10-03T01:03:00Z")])
                                    "status" "riatzukiza/.agents" "8")) "gate: PASS"))))

(deftest writer-opener-cannot-clear-body-findings-through-comment-settlement
  (doseq [[state body marker]
          [["CHANGES_REQUESTED" "P1: authorization is bypassed" "review-body:20"]
           ["COMMENTED" "Outside diff range comments (1): legacy finding" "review-body:20"]
           ["COMMENTED" "<summary><em>🟠 Major</em> · Bug · <code>x:1</code></summary><!-- cr-comment:v1:abc123 -->" "cr-comment:v1:abc123"]
           ["COMMENTED" "Nitpick comments (1)<!-- cr-comment:v1:abc123 -->" "cr-comment:v1:abc123"]]]
    (let [human {:id 20 :user {:login "human-reviewer" :type "User"} :state state
                 :body body :commit_id head :submitted_at "2026-10-03T01:01:00Z"}
          self {:user {:login "human-reviewer" :type "User"} :created_at "2026-10-03T01:02:00Z"
                :body (str "Handled: review-id:20\n- Fixed " marker ": verified regression")}
          config (assoc base :reviews [approval human] :comments [self] :authorized true)
          blocked (execute config "gate" "riatzukiza/.agents" "8" "--apply")
          repaired (execute (assoc config :comments [(assoc-in self [:user :login] "different-writer")])
                            "gate" "riatzukiza/.agents" "8" "--apply")]
      (is (= 2 (:exit blocked)))
      (is (str/includes? (:out blocked) "1 unanswered review summary item(s)"))
      (is (empty? (writes blocked "merge")))
      (is (= 0 (:exit repaired)) (:err repaired))
      (is (= 1 (count (writes repaired "merge"))))))
  (let [human {:id 20 :user {:login "human-reviewer" :type "User"} :state "CHANGES_REQUESTED"
               :body "P1: authorization is bypassed" :commit_id head :submitted_at "2026-10-03T01:01:00Z"}
        approved (assoc human :id 21 :state "APPROVED" :body "Approved after verifying the repaired boundary."
                        :submitted_at "2026-10-03T01:03:00Z")
        r (execute (assoc base :reviews [approval human approved]) "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 0 (:exit r)) (:err r))
    (is (= 1 (count (writes r "merge"))))))

(deftest public-thread-replies-do-not-settle-a-resolved-blocker
  (let [thread {:id "t" :isResolved true :comments {:pageInfo {:hasNextPage false}
                :nodes [{:author {:login "human-reviewer"} :body "P1: bug"}
                        {:author {:login "drive-by"} :body "Fixed in abc: trust me"}]}}
        r (execute (assoc base :authorized false :threads [thread]) "status" "riatzukiza/.agents" "8")]
    (is (str/includes? (:out r) "gate: BLOCKED"))
    (is (str/includes? (:out r) "P0/P1 thread(s) not fixed"))))

(deftest approvals-and-conversations-are-a-consistent-snapshot
  (let [finding {:id "human-thread" :isResolved false :comments {:pageInfo {:hasNextPage false}
                  :nodes [{:author {:login "human-reviewer"} :body "P1: missed finding"}]}}
        race (execute (assoc base :newThreadsAfterReviews [finding]) "gate" "riatzukiza/.agents" "8" "--apply")
        newer (assoc approval :id 2 :state "CHANGES_REQUESTED" :body "P1: new issue" :submitted_at "2026-10-03T01:02:00Z")
        changed (execute (assoc base :reviewsSequence [[approval] [approval newer]]) "gate" "riatzukiza/.agents" "8" "--apply")
        thread-changed (execute (assoc base :threadsSequence [[] [finding]]) "gate" "riatzukiza/.agents" "8" "--apply")
        snapshot (execute (assoc base :threadsSequence [[] [finding]]) "status" "riatzukiza/.agents" "8")
        ready-but-changed (execute (assoc base :checksSequence [[{:name "laws" :state "SUCCESS" :required true}]
                                                               [{:name "laws" :state "PENDING" :required true}]])
                                  "gate" "riatzukiza/.agents" "8" "--apply")]
    (doseq [r [race changed thread-changed ready-but-changed]]
      (is (= 2 (:exit r)) (:err r))
      (is (empty? (writes r "merge"))))
    (is (str/includes? (:out snapshot) "gate: BLOCKED"))))

(deftest request-retries-permission-refusal-with-identical-stdin
  (let [r (execute (assoc base :testToken true :rejectCommentToken true
                         :checks [{:name "laws" :state "SUCCESS" :required true}])
                   "request" "riatzukiza/.agents" "8" "code" "--note" "literal $() `text`")
        attempts (writes r "comment")]
    (is (= 0 (:exit r)) (:err r))
    (is (= [true false] (mapv :hasToken attempts)))
    (is (= 1 (count (set (map :input attempts)))))))

(deftest no-rest-coderabbit-completions-count-without-capping-requests
  (let [config (assoc base :reviews [])
        request (execute config "request" "riatzukiza/.agents" "8" "code")
        verdict {:user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
                 :created_at "2026-10-03T01:00:00Z"
                 :body (str "Codex Review: Didn't find any major issues. :tada:\n\n**Reviewed commit:** `" (subs head 0 10) "`")}
        accepted (execute (assoc config :comments [verdict]) "gate" "riatzukiza/.agents" "8" "--apply")
        short (execute (assoc config :prior-stage-rounds 4 :comments [verdict])
                       "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 0 (:exit request)) (:err request))
    (is (= 1 (count (writes request "comment"))))
    (is (= 0 (:exit accepted)) (:err accepted))
    (is (str/includes? (:out accepted) "completed code rounds: 5"))
    (is (= 1 (count (writes accepted "merge"))))
    (is (= 2 (:exit short)))
    (is (str/includes? (:out short) "completed code rounds: 4"))
    (is (empty? (writes short "merge")))))

(def rejection-reason "The requested second validation duplicates the pure boundary check, which already rejects malformed identity before any adapter effect.")
(def rejection-evidence "skills/pr-flow/scripts/pr_flow/law.cljc:122; https://example.invalid/checks/123")
(def rejection-body (str "Rejected:\nReason: " rejection-reason "\nEvidence: " rejection-evidence))
(defn thread-comment [login kind body]
  {:author {:login login :__typename kind} :body body :url "https://example.invalid/review#comment"})
(defn rejection-thread [sha]
  {:id "rejection-thread" :isResolved false :comments {:pageInfo {:hasNextPage false}
   :nodes [(thread-comment "coderabbitai[bot]" "Bot" "_🟡 Minor_ Add a second identity validation in the adapter.")
           (thread-comment "riatzukiza" "User" (str "Rejection proposal for " sha ":\nReason: " rejection-reason "\nEvidence: " rejection-evidence))
           (thread-comment "chatgpt-codex-connector[bot]" "Bot"
                           (str "Rejection agreement for " sha ":\nReason: I checked the pure boundary and its malformed-identity law; the proposed second validation adds no missing protection.\nEvidence: " rejection-evidence))]}})

(deftest rejected-thread-needs-independent-native-agreement-before-effects
  (let [thread (rejection-thread head)
        valid (assoc base :threads [thread] :allowMutations true)
        accepted (execute valid "settle" "riatzukiza/.agents" "8" "rejection-thread" rejection-body)]
    (is (= 0 (:exit accepted)) (:err accepted))
    (is (= 2 (count (mutations accepted))))
    (is (str/includes? (str (:args (first (mutations accepted)))) "addPullRequestReviewThreadReply"))
    (is (str/includes? (str (:args (second (mutations accepted)))) "resolveReviewThread"))
    (doseq [invalid [(assoc-in thread [:comments :nodes] [(first (get-in thread [:comments :nodes]))])
                     (update-in thread [:comments :nodes] pop)
                     (assoc-in thread [:comments :nodes 2 :author :login] "coderabbitai[bot]")
                     (assoc-in thread [:comments :nodes 2 :author :login] "fake-codex[bot]")
                     (assoc-in thread [:comments :nodes 2 :author :__typename] "User")
                     (assoc-in thread [:comments :nodes 2 :author :login] "riatzukiza")
                     (rejection-thread other)
                     (update-in thread [:comments :nodes 2 :body] #(str "> " (str/replace % "\n" "\n> ")))
                     (update-in thread [:comments :nodes] #(vec [(first %) (nth % 2) (second %)]))]]
      (let [blocked (execute (assoc valid :threads [invalid]) "settle" "riatzukiza/.agents" "8" "rejection-thread" rejection-body)]
        (is (= 1 (:exit blocked)))
        (is (empty? (mutations blocked)))))
    (doseq [[config body] [[(assoc valid :prAuthor "chatgpt-codex-connector[bot]") rejection-body]
                           [(assoc valid :heads [other]) rejection-body]
                           [valid "Rejected: not needed"]
                           [valid (str/replace rejection-body rejection-reason "The user should handle this in another project, without any further validation work.")]]]
      (let [blocked (execute config "settle" "riatzukiza/.agents" "8" "rejection-thread" body)]
        (is (= 1 (:exit blocked)))
        (is (empty? (mutations blocked)))))))

(deftest review-body-rejection-needs-independent-native-agreement
  (let [scope "review-id:20 cr-comment:v1:abc123"
        finding (assoc approval :id 20 :state "COMMENTED" :user {:login "coderabbitai[bot]" :type "Bot"}
                       :submitted_at "2026-10-03T01:00:10Z"
                       :body "<summary><em>🟡 Minor</em> · Bug · <code>src/auth.cljc:12</code></summary><!-- cr-comment:v1:abc123 -->")
        proposal {:user {:login "riatzukiza" :type "User"} :created_at "2026-10-03T01:01:00Z"
                  :body (str "Rejection proposal for " head ": " scope "\nReason: " rejection-reason "\nEvidence: " rejection-evidence)}
        agreement {:user {:login "chatgpt-codex-connector[bot]" :type "Bot"} :created_at "2026-10-03T01:02:00Z"
                   :html_url "https://example.invalid/review#independent-agreement"
                   :body (str "Rejection agreement for " head ": " scope "\nReason: I independently checked the pure validation boundary and its malformed-identity law; an adapter duplicate supplies no missing check.\nEvidence: " rejection-evidence)}
        settlement {:user {:login "riatzukiza" :type "User"} :created_at "2026-10-03T01:03:00Z"
                    :body (str "Handled: review-id:20\n- Rejected cr-comment:v1:abc123: Reason: " rejection-reason "; Evidence: " rejection-evidence)}
        config (assoc base :reviews [approval finding] :comments [proposal agreement settlement])]
    (doseq [severity ["🟡 Minor" "🟠 Major"]]
      (let [r (execute (assoc-in config [:reviews 1 :body] (str/replace (:body finding) "🟡 Minor" severity))
                       "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= 0 (:exit r)) (:err r))
        (is (= 1 (count (writes r "merge"))))))
    (doseq [comments [[proposal settlement]
                      [agreement settlement]
                      [proposal (assoc-in agreement [:user :login] "coderabbitai[bot]") settlement]
                      [proposal (assoc-in agreement [:user :login] "fake-codex[bot]") settlement]
                      [proposal (assoc-in agreement [:user :type] "User") settlement]
                      [proposal (assoc agreement :body "I agree with the author.") settlement]
                      [proposal (update agreement :body #(str/replace % head other)) settlement]
                      [proposal agreement (update settlement :body #(str/replace % rejection-reason "This belongs in another project and should be left for later without a concrete fix."))]]]
      (let [r (execute (assoc config :comments comments) "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= 2 (:exit r)))
        (is (str/includes? (:out r) "1 unanswered review summary item(s)"))
        (is (empty? (writes r "merge")))))
    (let [r (execute (assoc config :prAuthor "chatgpt-codex-connector[bot]") "gate" "riatzukiza/.agents" "8" "--apply")]
      (is (= 2 (:exit r)))
      (is (empty? (writes r "merge"))))))

(deftest timing-and-merge-options-are-validated-before-effects
  (doseq [method ["help" "delete-branch" "admin" "--merge" ""]]
    (let [r (execute base "gate" "riatzukiza/.agents" "8" "--apply" "--method" method)]
      (is (= 1 (:exit r)))
      (is (empty? (:calls r)))))
  (doseq [flag ["--timeout" "--interval"] value ["NaN" "0" "-1" "Infinity" "1oops"]]
    (let [r (execute base "wait" "riatzukiza/.agents" "8" flag value)]
      (is (= 1 (:exit r)))
      (is (empty? (:calls r))))))

(deftest wait-observes-latest-rerun-instead-of-old-success
  (let [old {:name "CodeRabbit" :state "SUCCESS" :workflow "review" :startedAt "2026-10-03T01:00:00Z"}
        active (assoc old :state "PENDING" :startedAt "2026-10-03T01:01:00Z")
        r (execute (assoc base :checksSequence [[old active] [old (assoc active :state "SUCCESS")]])
                   "wait" "riatzukiza/.agents" "8" "--timeout" "3" "--interval" "1")
        polls (filter #(and (= ["pr" "checks"] (vec (take 2 (:args %))))
                             (not (some #{"--required"} (:args %)))) (:calls r))]
    (is (= 0 (:exit r)) (:err r))
    (is (= 2 (count polls)))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests)
