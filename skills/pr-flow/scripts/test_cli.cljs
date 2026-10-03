#!/usr/bin/env nbb
(ns test-cli
  (:require ["fs" :as fs] ["os" :as os] ["path" :as path] ["child_process" :as cp]
            [cljs.test :refer [deftest is run-tests]] [clojure.string :as str]
            [clojure.edn :as edn]
            [test-issue-agreement :as native]
            [test-informational :as informational]
            [pr-flow.actionability :as actionability]
            [nbb.core :refer [*file*]]))
(def here (path/dirname *file*))
(def the-flow (edn/read-string (fs/readFileSync (path/join here ".." "flow.edn") "utf8")))
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
        scripts (if (:flow-data config) (path/join tmp "skills" "pr-flow" "scripts") here)
        rounds (:prior-stage-rounds config 0)
        history (completed-stage-reviews rounds other)
        config (cond-> (-> config
                           (assoc :comments (into (completed-stage-comments rounds other (:prior-stage config "code")) (:comments config)))
                           (update :reviews #(into history %))
                           (dissoc :prior-stage-rounds :prior-stage))
                 (:reviewsSequence config) (update :reviewsSequence #(mapv (fn [reviews] (into history reviews)) %))
                 (:commentsSequence config) (update :commentsSequence #(mapv (fn [comments] (into (completed-stage-comments rounds other (:prior-stage config "code")) comments)) %)))
        env (js/Object.assign #js {} js/process.env #js {:PR_FLOW_TEST_DATA data :PATH (str tmp ":" (.-PATH js/process.env))})]
    (try
      (when-let [flow-data (:flow-data config)]
        ;; Exercise real file configuration without touching the owned checkout.
        (fs/cpSync here scripts #js {:recursive true})
        (fs/writeFileSync (path/join scripts ".." "flow.edn") (pr-str flow-data))
        (fs/mkdirSync (path/join tmp ".ημ") #js {:recursive true})
        (fs/writeFileSync (path/join tmp ".ημ" "receipts.edn")
                          (if (seq (:receipt-history config))
                            (str (str/join "\n" (map pr-str (:receipt-history config))) "\n") ""))
        (when (:no-receipt-ledger config) (fs/unlinkSync (path/join tmp ".ημ" "receipts.edn")))
        (when (:baseline-source config)
          (doseq [relative ["pr.cljs" "pr_flow/law.cljc" "pr_flow/flow.cljc"]]
            (let [source (cp/spawnSync "git" #js ["show" (str "9ee8831ffe001b025d425a239a2d30e95378ea6a:skills/pr-flow/scripts/" relative)]
                                      #js {:cwd (path/resolve here ".." ".." "..") :encoding "utf8"})]
              (when-not (= 0 (.-status source)) (throw (ex-info "Immutable baseline unavailable" {})))
              (fs/writeFileSync (path/join scripts relative) (.-stdout source))))))
      (when (:testToken config) (aset env "GH_TOKEN" "fixture-token") (aset env "GITHUB_TOKEN" "fixture-token"))
      (fs/writeFileSync data (js/JSON.stringify (clj->js (dissoc config :flow-data))))
      (fs/copyFileSync (path/join here "fixture-gh.cjs") gh) (fs/chmodSync gh 493)
      (let [r (cp/spawnSync "nbb" (clj->js (into ["-cp" scripts (path/join scripts "pr.cljs")] args)) #js {:env env :encoding "utf8" :timeout 5000})
            calls-path (str data ".calls")
            calls (if (fs/existsSync calls-path)
                    (mapv #(js->clj (js/JSON.parse %) :keywordize-keys true)
                          (remove str/blank? (str/split-lines (fs/readFileSync calls-path "utf8")))) [])]
        {:exit (.-status r) :out (str (.-stdout r)) :err (str (.-stderr r)) :calls calls
         :receipts (when (and (:flow-data config) (fs/existsSync (path/join tmp ".ημ" "receipts.edn")))
                     (fs/readFileSync (path/join tmp ".ημ" "receipts.edn") "utf8"))})
      (finally (fs/rmSync tmp #js {:recursive true :force true})))))
(defn writes [result verb]
  (filter #(= ["pr" verb] (vec (take 2 (:args %)))) (:calls result)))
(defn mutations [result]
  (filter #(and (= ["api" "graphql"] (vec (take 2 (:args %))))
                (some (fn [arg] (str/starts-with? arg "query=mutation")) (:args %))) (:calls result)))

(deftest configured-flow-minimum-aligns-settle-and-gate
  (let [body "Deferred to issue https://github.com/owner/repo/issues/9: scoped follow-up"
        opener {:author {:login "human-reviewer"} :body "P3: docstring correction"}
        thread {:id "deferred-thread" :isResolved false
                :comments {:pageInfo {:hasNextPage false} :nodes [opener]}}
        settled (-> thread (assoc :isResolved true)
                    (update-in [:comments :nodes] conj {:author {:login "riatzukiza"} :body body}))
        finding {:id 20 :user {:login "human-reviewer" :type "User"} :state "COMMENTED"
                 :commit_id head :submitted_at "2026-10-03T01:01:00Z"
                 :body "<summary><em>🟡 Minor</em> · Follow-up · <code>x:1</code></summary><!-- cr-comment:v1:abc123 -->"}
        answer {:user {:login "riatzukiza" :type "User"} :created_at "2026-10-03T01:02:00Z"
                :body "Handled: review-id:20\n- Deferred cr-comment:v1:abc123: issue https://github.com/owner/repo/issues/9"}]
    (doseq [[minimum rounds] [[2 2] [2 3] [7 6] [7 7] [7 8]]]
      (let [allowed? (> rounds minimum)
            config (assoc base :prior-stage-rounds rounds :allowMutations true
                          :flow-data (assoc-in the-flow [:flow/defaults :review/min-rounds] minimum))
            settle (execute (assoc config :threads [thread]) "settle" "riatzukiza/.agents" "8" "deferred-thread" body)
            thread-gate (execute (assoc config :threads [settled]) "gate" "riatzukiza/.agents" "8" "--apply")
            body-gate (execute (assoc config :reviews [approval finding] :comments [answer])
                               "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= (if allowed? 0 1) (:exit settle)) (:err settle))
        (is (= (if allowed? 2 0) (count (mutations settle))))
        (doseq [gate [thread-gate body-gate]]
          (is (= (if allowed? 0 2) (:exit gate)) (str (:err gate) (:out gate)))
          (is (= (if allowed? 1 0) (count (writes gate "merge")))))))))

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

(deftest cli-native-check-tuples-preserve-requiredness-and-approval-authority
  (let [native (js->clj (js/JSON.parse (fs/readFileSync
                                      (path/join here "fixtures" "native-reviewer-checks.json") "utf8"))
                       :keywordize-keys true)
        checks (vec (concat (map :check (:reviewer_outputs native))
                            (:deterministic_checks native) (:required_checks native)))
        config (assoc base :checks checks)
        optional (execute config "gate" "owner/repo" "450" "--apply")]
    (is (= 0 (:exit optional)) (str (:out optional) (:err optional)))
    (is (= 1 (count (writes optional "merge"))))
    (is (some #(some #{"--required"} (:args %)) (:calls optional)))
    (doseq [i [0 1]]
      (let [required (execute (assoc-in config [:checks i :required] true)
                              "gate" "owner/repo" "450" "--apply")]
        (is (= 2 (:exit required)) (str (:out required) (:err required)))
        (is (str/includes? (:out required) "A required reviewer/check"))
        (is (empty? (writes required "merge")))))
    (let [unknown (execute (assoc-in config [:checks 0 :workflow] "Different OpenCode workflow")
                           "gate" "owner/repo" "450" "--apply")
          spoof (execute (assoc config :reviews [(assoc-in approval [:user :login] "github-actions[bot]")])
                         "gate" "owner/repo" "450" "--apply")]
      (doseq [blocked [unknown spoof]]
        (is (= 2 (:exit blocked)))
        (is (empty? (writes blocked "merge")))))))

(deftest cli-native-required-review-gate-counts-every-nonpassing-outcome
  (let [native (js->clj (js/JSON.parse (fs/readFileSync
                                      (path/join here "fixtures" "native-reviewer-checks.json") "utf8"))
                       :keywordize-keys true)
        config (assoc base :checks (vec (:required_checks native)))
        gate-index (first (keep-indexed #(when (= "coderabbit-review-gate" (:name %2)) %1) (:checks config)))
        passed (execute config "gate" "owner/repo" "450" "--apply")]
    (is (= 0 (:exit passed)))
    (is (= 1 (count (writes passed "merge"))))
    (doseq [[state bucket] [["FAILURE" "fail"] ["PENDING" "pending"]
                            ["SKIPPED" "skip"] ["CANCELLED" "skip"]]]
      (let [blocked (execute (assoc-in config [:checks gate-index :state] state)
                             "gate" "owner/repo" "450" "--apply")]
        (is (= 2 (:exit blocked)) (str (:out blocked) (:err blocked)))
        (is (str/includes? (:out blocked) (str ":" bucket " 1")))
        (is (empty? (writes blocked "merge")))))
    (doseq [[name required?] [["CodeRabbit" true] ["Required / CodeRabbit" false]
                              ["CodeRabbit / laws" false]]]
      (let [blocked (execute (assoc config :checks [{:name name :workflow "eta-mu-review-gate"
                                                     :state "FAILURE" :required required?}])
                             "gate" "owner/repo" "450" "--apply")]
        (is (= 2 (:exit blocked)))
        (is (str/includes? (:out blocked) ":fail 1"))
        (is (empty? (writes blocked "merge")))))))

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

(deftest configured-opencode-corroborates-without-granting-merge-approval
  (let [thread (assoc-in (rejection-thread head) [:comments :nodes 2 :author :login] "opencode-agent[bot]")
        accepted (execute (assoc base :threads [thread] :allowMutations true)
                          "settle" "riatzukiza/.agents" "8" "rejection-thread" rejection-body)
        approval-only (execute (assoc base :reviews [(assoc-in approval [:user :login] "opencode-agent[bot]")])
                               "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 0 (:exit accepted)) (:err accepted))
    (is (= 2 (count (mutations accepted))))
    (is (= 2 (:exit approval-only)))
    (is (empty? (writes approval-only "merge")))))

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

(def empty-thread-page
  {:data {:repository {:pullRequest {:isDraft false :headRefOid head :author {:login "original-author"}
          :reviewThreads {:pageInfo {:hasNextPage false :endCursor nil} :nodes []}}}}})

(deftest successful-gh-with-invalid-thread-page-cannot-enable-merge
  (let [pr-path [:data :repository :pullRequest]
        conn-path (conj pr-path :reviewThreads)
        thread {:id "t" :isResolved true :comments {:pageInfo {:hasNextPage false} :nodes []}}
        thread-page (assoc-in empty-thread-page (conj conn-path :nodes) [thread])]
    (doseq [response [nil {:data nil}
                       (assoc-in empty-thread-page [:data :repository] nil)
                       (assoc-in empty-thread-page pr-path nil)
                       (assoc-in empty-thread-page conn-path nil)
                       (assoc-in empty-thread-page (conj conn-path :nodes) nil)
                       (assoc-in empty-thread-page (conj conn-path :pageInfo) nil)
                       (assoc-in empty-thread-page (conj conn-path :pageInfo :hasNextPage) nil)
                       (assoc-in empty-thread-page (conj conn-path :pageInfo :hasNextPage) true)
                       (assoc-in thread-page (conj conn-path :nodes 0 :comments) nil)
                       (assoc-in thread-page (conj conn-path :nodes 0 :comments :nodes) nil)
                       (assoc-in thread-page (conj conn-path :nodes 0 :comments :pageInfo) nil)
                       (assoc empty-thread-page :errors [{:message "partial GraphQL error"}])]]
      (let [r (execute (assoc base :threadPageResponses [response]) "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= 1 (:exit r)) (:err r))
        (is (str/includes? (:err r) "GraphQL review-thread"))
        (is (empty? (writes r "merge")))))))

(deftest every-required-thread-page-is-validated
  (let [more (assoc-in empty-thread-page [:data :repository :pullRequest :reviewThreads :pageInfo]
                       {:hasNextPage true :endCursor "cursor1"})]
    (doseq [responses [[more nil] [empty-thread-page nil]]]
      (let [r (execute (assoc base :threadPageResponses responses) "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= 1 (:exit r)) (:err r))
        (is (empty? (writes r "merge"))))))
  (let [bad (execute (assoc base :threadPageResponses [nil]) "threads" "riatzukiza/.agents" "8" "--all")
        empty (execute (assoc base :threadPageResponses [empty-thread-page]) "gate" "riatzukiza/.agents" "8" "--apply")
        gh-error (execute (assoc base :threadPageResponses [(assoc empty-thread-page :errors [{:message "GraphQL error"}])]
                                :threadPageExitCode 1) "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 1 (:exit bad)))
    (is (= 0 (:exit empty)) (:err empty))
    (is (= 1 (count (writes empty "merge"))))
    (is (= 1 (:exit gh-error)))
    (is (str/includes? (:err gh-error) "fixture GraphQL error reported by gh"))
    (is (empty? (writes gh-error "merge")))))

(def native-thread
  {:id (:thread_id native/fixture) :isResolved false :isOutdated false
   :path ".github/workflows/opencode-code-review.yml" :line 95
   :comments {:pageInfo {:hasNextPage false}
              :nodes [(:root native/fixture) (:proposal native/fixture)]}})
(def native-final
  {:databaseId 4172979880 :author {:login "riatzukiza" :__typename "User"}
   :body native/final-body :createdAt "2026-10-03T11:38:00Z" :updatedAt "2026-10-03T11:38:00Z"})
(def native-settled-thread
  (-> native-thread (assoc :isResolved true) (update-in [:comments :nodes] conj native-final)))
(def native-config
  (assoc base :head (:head native/fixture) :prAuthor "riatzukiza"
         :reviews [(assoc approval :commit_id (:head native/fixture))]
         :comments [(:agreement native/fixture)] :threads [native-thread] :allowMutations true))
(def native-codex
  (js->clj (js/JSON.parse (fs/readFileSync (path/join here "fixtures/native-codex-completion.json") "utf8"))
           :keywordize-keys true))

(deftest native-off-thread-agreement-is-hydrated-in-every-cli-path
  (let [settle (execute native-config "settle" "open-hax/proxx" "445" (:id native-thread) native/final-body)
        settled (assoc native-config :threads [native-settled-thread])
        gate (execute settled "gate" "open-hax/proxx" "445" "--apply")
        threads (execute settled "threads" "open-hax/proxx" "445" "--all")]
    (is (= 0 (:exit settle)) (:err settle))
    (is (= 2 (count (mutations settle))))
    (is (= 0 (:exit gate)) (str (:out gate) (:err gate)))
    (is (= 1 (count (writes gate "merge"))))
    (is (str/includes? (:out threads) "github-issue-comment"))
    (is (str/includes? (:out threads) "5968785159"))
    (is (str/includes? (:out threads) (:html_url native/agreement)))
    (doseq [r [settle gate threads]]
      (is (some #(= ["api" "repos/open-hax/proxx/issues/445/comments" "--paginate" "--slurp"] (:args %)) (:calls r)))
      (is (some #(some (fn [arg] (and (str/includes? arg "query(") (str/includes? arg "databaseId"))) (:args %)) (:calls r))))))

(deftest invalid-native-issue-evidence-has-no-settlement-or-merge-effects
  (doseq [config [(assoc native-config :comments [])
                  (assoc-in native-config [:comments 0 :body] "I agree with the author.")
                  (update-in native-config [:comments 0 :body] str/replace (:head native/fixture) other)
                  (update-in native-config [:comments 0 :body] str/replace (:id native-thread) "PRRT_another")
                  (assoc-in native-config [:comments 0 :user :type] "User")
                  (assoc-in native-config [:comments 0 :user :login] "fake-opencode[bot]")
                  (assoc-in native-config [:threads 0 :comments :nodes 0 :databaseId] 4172695095)
                  (assoc native-config :head other :reviews [(assoc approval :commit_id other)])
                  (assoc native-config :authorized false)]]
    (let [settle (execute config "settle" "open-hax/proxx" "445" (:id native-thread) native/final-body)
          gate (execute (assoc config :threads [(-> native-settled-thread
                                                   (assoc-in [:comments :nodes 0 :databaseId]
                                                             (get-in config [:threads 0 :comments :nodes 0 :databaseId])))])
                        "gate" "open-hax/proxx" "445" "--apply")]
      (is (= 1 (:exit settle)))
      (is (empty? (mutations settle)))
      (is (= 2 (:exit gate)))
      (is (empty? (writes gate "merge"))))))

(deftest issue-assessment-is-not-approval-round-credit-or-a-required-check-waiver
  (let [config (assoc native-config :threads [native-settled-thread])
        no-approval (execute (assoc config :reviews []) "gate" "open-hax/proxx" "445" "--apply")
        no-rounds (execute (dissoc config :prior-stage-rounds) "gate" "open-hax/proxx" "445" "--apply")
        cancelled (execute (assoc-in config [:checks 0 :state] "CANCELLED") "gate" "open-hax/proxx" "445" "--apply")]
    (doseq [r [no-approval no-rounds cancelled]]
      (is (= 2 (:exit r)))
      (is (empty? (writes r "merge"))))
    (is (str/includes? (:out no-rounds) "completed code rounds: 0"))))

(deftest native-withdrawal-during-collection-blocks-the-fresh-gate
  (let [withdrawal (assoc (:agreement native/fixture) :id 5968785160
                          :created_at "2026-10-03T11:39:00Z" :updated_at "2026-10-03T11:39:00Z"
                          :body (str "Finding: " (:id native-thread) " comment4172695094\nI withdraw my agreement; this still reproduces."))
        r (execute (assoc native-config :threads [native-settled-thread]
                          :commentsSequence [[(:agreement native/fixture)] [(:agreement native/fixture) withdrawal]])
                   "gate" "open-hax/proxx" "445" "--apply")]
    (is (= 2 (:exit r)))
    (is (str/includes? (:out r) "Review evidence changed while collecting"))
    (is (empty? (writes r "merge")))))

(deftest native-withdrawal-before-settlement-effects-blocks-the-write
  (let [withdrawal (assoc (:agreement native/fixture) :id 5968785160
                          :created_at "2026-10-03T11:39:00Z" :updated_at "2026-10-03T11:39:00Z"
                          :body (str "Finding: " (:id native-thread) " comment4172695094\nI withdraw my agreement; this still reproduces."))
        r (execute (assoc native-config :commentsSequence [[(:agreement native/fixture)] [(:agreement native/fixture) withdrawal]])
                   "settle" "open-hax/proxx" "445" (:id native-thread) native/final-body)]
    (is (= 1 (:exit r)))
    (is (str/includes? (:err r) "Review evidence changed before rejection"))
    (is (empty? (mutations r)))))

(deftest incomplete-issue-context-cannot-be-a-zero-comment-pass
  (doseq [pages [nil [] [nil] [[(:agreement native/fixture)] nil] [[(:agreement native/fixture) nil]]]
          args [["threads" "open-hax/proxx" "445" "--all"]
                ["settle" "open-hax/proxx" "445" (:id native-thread) native/final-body]
                ["gate" "open-hax/proxx" "445" "--apply"]]]
    (let [r (apply execute (assoc native-config :threads [native-settled-thread] :commentPageResponses [pages]) args)]
      (is (= 1 (:exit r)))
      (is (str/includes? (:err r) "Invalid PR issue-comment page"))
      (is (empty? (mutations r)))
      (is (empty? (writes r "merge"))))))

(deftest graphql-repository-identities-remain-strings
  (doseq [repo ["123/456" "owner/123" "123/repo" "owner/true" "owner/null"]]
    (let [r (execute base "status" repo "8")]
      (is (= 0 (:exit r)) (:err r))
      (is (str/includes? (:out r) "gate: PASS")))))

(deftest malformed-reviewer-options-fail-before-evidence-or-merge-effects
  (doseq [options [["--reviewers"] ["--reviewers" ""] ["--reviewers" "  "]
                   ["--reviewers="] ["--reviewers=  "] ["--reviewers" "--apply"]
                   ["--reviewers=unknown"] ["--reviewers" "codex,"]
                   ["--reviewers" "codex" "--reviewers="] ["--reviewers=codex" "--reviewers=mimo"]]
          cmd ["status" "gate"]]
    (let [r (apply execute base cmd "riatzukiza/.agents" "8" (concat ["--apply"] options))]
      (is (= 1 (:exit r)))
      (is (empty? (:calls r))))))

(deftest both-reviewer-syntaxes-preserve-the-explicit-mandatory-set
  (doseq [options [["--reviewers" "coderabbit,codex"] ["--reviewers=coderabbit,codex"]]]
    (let [r (apply execute base "gate" "riatzukiza/.agents" "8" (concat ["--apply"] options))]
      (is (= 2 (:exit r)))
      (is (str/includes? (:out r) "gate: BLOCKED"))
      (is (empty? (writes r "merge"))))))

(deftest codex-hydration-does-not-change-an-unchanged-native-thread-snapshot
  (doseq [sha [(:head native/fixture) (:resolved_commit_id native-codex)]]
    (let [completion (update (:comment native-codex) :body str/replace "730924f8ed" (subs sha 0 10))
          config (assoc native-config :threads [native-settled-thread] :resolvedCommit sha
                        :comments [(:agreement native/fixture) completion])
          status (execute config "status" "open-hax/proxx" "445")
          gate (execute config "gate" "open-hax/proxx" "445" "--apply")]
      (is (= 0 (:exit status)) (:err status))
      (is (str/includes? (:out status) "gate: PASS"))
      (is (= 0 (:exit gate)) (str (:out gate) (:err gate)))
      (is (= 1 (count (writes gate "merge")))))))

(deftest native-source-id-or-url-withdrawal-blocks-effectful-paths
  (doseq [body ["I withdraw my agreement in issuecomment-5968785159; this still reproduces."
                (str "I withdraw my agreement at " (:html_url native/agreement) "; this still reproduces.")]]
    (let [withdrawal (assoc (:agreement native/fixture) :id 5968785160
                            :created_at "2026-10-03T11:39:00Z" :updated_at "2026-10-03T11:39:00Z" :body body)
          config (assoc native-config :comments [(:agreement native/fixture) withdrawal])
          gate (execute (assoc config :threads [native-settled-thread]) "gate" "open-hax/proxx" "445" "--apply")
          settle (execute config "settle" "open-hax/proxx" "445" (:id native-thread) native/final-body)]
      (is (= 2 (:exit gate)))
      (is (empty? (writes gate "merge")))
      (is (= 1 (:exit settle)))
      (is (empty? (mutations settle))))))

(defn informational-response [s]
  {:data {:repository (assoc (:repository s) :pullRequest
                            (assoc (:pr s) :reviewThreads {:pageInfo {:hasNextPage false} :nodes [(:thread s)]}))}})
(defn informational-config [s]
  (let [t (informational/evidence (informational/input s))]
    (assoc base :flow-data the-flow :head (:head t) :prAuthor (:pr-author t)
           :reviews [(assoc approval :commit_id (:head t))]
           :comments (:issue-comments t) :threadPageResponses [(informational-response s)])))
(defn observation-receipts [r]
  (map edn/read-string (remove str/blank? (str/split-lines (:receipts r "")))))

(deftest informational-native-hydration-red-green-and-receipt-proof
  (doseq [s informational/captures]
    (let [config (informational-config s) repo (get-in s [:repository :nameWithOwner]) n (str (get-in s [:pr :number]))
          ;; Optional local historical RED proof. Hosted shallow checkouts need
          ;; only current source; GREEN/negative assertions always execute.
          red (when (= "1" (.-PR_FLOW_BASELINE_PROOF js/process.env))
                (execute (assoc config :baseline-source true) "gate" repo n "--apply"))
          green (execute config "gate" repo n "--apply")
          displayed (execute config "threads" repo n "--all")]
      (when red
        (is (= 2 (:exit red)))
        (is (str/includes? (:out red) "without a settlement reply"))
        (is (empty? (writes red "merge"))))
      (is (= 0 (:exit green)) (str (:err green) (:out green)))
      (is (= 1 (count (writes green "merge"))))
      (is (empty? (mutations green)))
      (is (str/includes? (:out green) "informational 1"))
      (is (str/includes? (:out displayed) "-unsettled-"))
      (is (str/includes? (:out displayed) "actionability: informational qualified"))
      (is (str/includes? (:out displayed) "native-source=7002"))
      (let [receipts (observation-receipts green)]
        (is (= 1 (count receipts)))
        (is (= :qualified (get-in (first receipts) [:decisions 0 :status])))
        (is (every? #(contains? (first receipts) %) [:ts :kind :origin :owner :dod :pi :host :manifest :refs]))))))

(deftest informational-cli-retains-all-other-obligations-and-separate-admission
  (let [s (first informational/captures) config (informational-config s)]
    (doseq [c [(assoc config :comments []) (assoc config :reviews [])
               (assoc config :prior-stage-rounds 0)
               (assoc config :checks [{:name "laws" :state "FAILURE" :required true}])
               (assoc config :checks [{:name "laws" :state "CANCELLED" :required true}])
               (assoc config :no-receipt-ledger true)
               (update config :flow-data update :flow/defaults dissoc :review/actionability)
               (assoc config :threadPageResponses [(informational-response (assoc-in s [:thread :isResolved] false))])]]
      (let [r (execute c "gate" "open-hax/proxx" "445" "--apply")]
        (is (not= 0 (:exit r)) (str (:err r) (:out r)))
        (is (empty? (writes r "merge")))
        (is (empty? (mutations r)))))
    (let [r (execute (update config :comments update 1 assoc :run-conclusion "cancelled")
                     "status" "open-hax/proxx" "445")]
      (is (= 0 (:exit r)))
      (is (str/includes? (:out r) "gate: PASS"))
      (is (str/includes? (:out r) "completed code rounds: 5"))
      (is (str/includes? (:out r) "#{\"mimo\"}")))))

(deftest changed-native-context-or-assessment-revokes-and-blocks-effects
  (let [s (first informational/captures) config (informational-config s)
        changed (assoc-in s [:thread :comments :nodes 0 :updatedAt] "2026-10-03T14:03:00Z")
        first-comments (:comments config)]
    (doseq [c [(assoc config :threadPageResponses [(informational-response s) (informational-response changed)])
               (assoc config :commentsSequence [first-comments [(first first-comments)] first-comments])
               (assoc config :commentsSequence [first-comments
                                                (update first-comments 1 assoc :updated_at "2026-10-03T14:03:00Z")])]]
      (let [r (execute c "gate" "open-hax/proxx" "445" "--apply")]
        (is (= 2 (:exit r)) (str (:err r) (:out r)))
        (is (empty? (writes r "merge")))
        (is (empty? (mutations r)))
        (is (some #(= :revoked (get-in % [:decisions 0 :status])) (observation-receipts r)))))))

(deftest persisted-revocation-prevents-reused-source-admission
  (let [s (first informational/captures) config (informational-config s)
        t (informational/evidence (informational/input s))
        accepted (first (:observations (actionability/disposition t)))
        prefix {:ts "2026-10-03T14:04:00Z" :kind :observation :origin "pr-flow-actionability-observation"
                :owner "fixture" :dod "fixture" :pi "fixture" :host "isolated-fixture" :manifest [] :refs []
                :decisions [accepted (assoc accepted :status :revoked :reason :withdrawn)]}
        r (execute (assoc config :receipt-history [prefix]) "gate" "open-hax/proxx" "445" "--apply")]
    (is (= 2 (:exit r)))
    (is (empty? (writes r "merge")))
    (is (str/starts-with? (:receipts r) (str (pr-str prefix) "\n")))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests)
