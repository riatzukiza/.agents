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
(def base {:head head :reviews [approval] :comments []
           :checks [{:name "laws" :state "SUCCESS" :required true}
                    {:name "CodeRabbit" :state "SKIPPED" :description "Review skipped"}]})
(defn execute [config & args]
  (let [tmp (fs/mkdtempSync (path/join (os/tmpdir) "pr-flow-cli-"))
        data (path/join tmp "data.json") gh (path/join tmp "gh")
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

(deftest hosted-approvals-wired-through-cli
  (let [r (execute base "status" "riatzukiza/.agents" "8")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "gate: PASS"))
    (is (str/includes? (:out r) "coderabbit: skipped")))
  (doseq [config [(assoc-in base [:reviews 0 :state] "COMMENTED")
                  (assoc-in base [:reviews 0 :commit_id] other)
                  (assoc-in base [:reviews 0 :user :login] "fake-eta-mu-ai[bot]")
                  (assoc base :heads [head other])
                  (assoc-in base [:checks 0 :state] "FAILURE")]]
    (is (str/includes? (:out (execute config "status" "riatzukiza/.agents" "8")) "gate: BLOCKED")))
  (is (str/includes? (:out (execute base "status" "riatzukiza/.agents" "8" "--reviewers" "codex")) "gate: BLOCKED")))

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
    (is (str/starts-with? (:input posted) "@coderabbitai full review"))
    (is (str/includes? (:input posted) note))
    (is (str/includes? (:input posted) (str "pr-flow-review:" head)))
    (is (= ["--body-file" "-"] (vec (take-last 2 (:args posted))))))
  (let [config (assoc base :comments [{:user {:login "riatzukiza" :type "User"}
                                      :created_at "2026-10-03T01:00:00Z"
                                      :body (str "@coderabbitai full review <!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->")}])
        r (execute config "request" "riatzukiza/.agents" "8" "code")]
    (is (str/includes? (:out r) "pending"))
    (is (empty? (writes r "comment")))))

(deftest seventh-round-never-posts
  (let [reviews (mapv #(assoc approval :id % :user {:login "coderabbitai[bot]" :type "Bot"}
                             :body "Actionable comments posted: 1") (range 1 7))
        r (execute (assoc base :reviews reviews) "request" "riatzukiza/.agents" "8" "code")]
    (is (str/includes? (:out r) "budget-exhausted"))
    (is (empty? (writes r "comment")))))

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

(deftest no-rest-no-findings-reviews-still-exhaust-the-budget
  (let [comments (vec (mapcat (fn [i]
                               [{:id (* 2 i) :user {:login "riatzukiza" :type "User"}
                                 :created_at (str "2026-10-03T01:0" i ":00Z")
                                 :body (str "@coderabbitai full review <!-- pr-flow-stage:code --> <!-- pr-flow-review:" head
                                            " --> <!-- pr-flow-reviewer:coderabbit -->")}
                                {:id (inc (* 2 i)) :user {:login "coderabbitai[bot]" :type "Bot"}
                                 :created_at (str "2026-10-03T01:0" i ":10Z") :body "Full review finished."}]) (range 5)))
        r (execute (assoc base :reviews [] :comments comments) "request" "riatzukiza/.agents" "8" "code")]
    (is (str/includes? (:out r) "budget-exhausted"))
    (is (empty? (writes r "comment")))))

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
