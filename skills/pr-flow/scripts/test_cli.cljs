#!/usr/bin/env nbb
(ns test-cli
  (:require ["fs" :as fs] ["os" :as os] ["path" :as path] ["child_process" :as cp] ["crypto" :as crypto]
            [cljs.test :refer [deftest is run-tests]] [clojure.string :as str]
            [clojure.edn :as edn]
            [test-issue-agreement :as native]
            [test-informational :as informational]
            [test-opener-withdrawal :as withdrawal]
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
  ;; Historical native REST completions remain evidence across pushes without
  ;; inventing historical check results from the current-head check snapshot.
  (vec (mapcat (fn [i]
                 (mapv (fn [[offset login]]
                         {:id (+ 1000 (* 10 i) offset) :user {:login login :type "Bot"} :state "COMMENTED"
                          :commit_id sha :submitted_at (str "2026-10-03T00:0" i ":2" offset "Z")
                          :body (if (= login "coderabbitai[bot]")
                                  "No actionable comments were generated." "Confirmed findings: none.")})
                       [[0 "chatgpt-codex-connector[bot]"] [1 "eta-mu-ai[bot]"] [2 "coderabbitai[bot]"]])) (range rounds))))
(def base {:head head :reviews [approval] :comments []
           ;; Old-head completions meet the stage floor without manufacturing
           ;; a current-head approval or suppressing a current-head request.
           :prior-stage-rounds 5
           :checks [{:name "laws" :state "SUCCESS" :required true}
                    {:name "CodeRabbit" :state "SKIPPED" :description "Review skipped"}]})
(defn execute [config & args]
  (let [tmp (fs/mkdtempSync (path/join (os/tmpdir) "pr-flow-cli-"))
        data (path/join tmp "data.json") gh (path/join tmp "gh")
        scripts (path/join tmp "skills" "pr-flow" "scripts")
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
      (let [flow-data (or (:flow-data config) the-flow)]
        ;; Exercise real file configuration without touching the owned checkout.
        (fs/cpSync here scripts #js {:recursive true})
        (fs/writeFileSync (path/join scripts ".." "flow.edn") (pr-str flow-data))
        (fs/mkdirSync (path/join tmp ".ημ") #js {:recursive true})
        (fs/writeFileSync (path/join tmp ".ημ" "receipts.edn")
                          (or (:receipt-text config)
                              (if (seq (:receipt-history config))
                                (str (str/join "\n" (map pr-str (:receipt-history config))) "\n") "")))
        (when (:no-receipt-ledger config) (fs/unlinkSync (path/join tmp ".ημ" "receipts.edn")))
        (when (:receipt-directory config)
          (fs/unlinkSync (path/join tmp ".ημ" "receipts.edn"))
          (fs/mkdirSync (path/join tmp ".ημ" "receipts.edn")))
        (when (or (:baseline-source config) (:baseline-source-ref config))
          (doseq [relative ["pr.cljs" "pr_flow/law.cljc" "pr_flow/flow.cljc"]]
            (let [revision (or (:baseline-source-ref config) "9ee8831ffe001b025d425a239a2d30e95378ea6a")
                  source (cp/spawnSync "git" #js ["show" (str revision ":skills/pr-flow/scripts/" relative)]
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
         :receipts (when (and (fs/existsSync (path/join tmp ".ημ" "receipts.edn"))
                             (.isFile (fs/statSync (path/join tmp ".ημ" "receipts.edn"))))
                     (fs/readFileSync (path/join tmp ".ημ" "receipts.edn") "utf8"))})
      (finally (fs/rmSync tmp #js {:recursive true :force true})))))
(defn writes [result verb]
  (filter #(= ["pr" verb] (vec (take 2 (:args %)))) (:calls result)))
(defn mutations [result]
  (filter #(and (= ["api" "graphql"] (vec (take 2 (:args %))))
                (some (fn [arg] (str/starts-with? arg "query=mutation")) (:args %))) (:calls result)))

(def native-kimi-publication
  (let [raw (js/JSON.parse (fs/readFileSync
                            (path/join here "fixtures/native-proxx-kimi-publication.json") "utf8"))]
    (assoc (js->clj raw :keywordize-keys true) :apiResponses
           (into {} (for [endpoint (js/Object.keys (.-apiResponses raw))]
                      [endpoint (js->clj (aget (.-apiResponses raw) endpoint) :keywordize-keys true)])))))
(defn kimi-replay-config []
  (-> base
      (assoc :head (get-in native-kimi-publication [:review :commit_id])
             :reviews [(:review native-kimi-publication)]
             :apiResponses (:apiResponses native-kimi-publication)
             :flow-data (assoc-in the-flow [:flow/defaults :review/kimi-publication]
                                  (:profile native-kimi-publication)))))

(deftest native-kimi-publication-cli-readback-and-repository-boundary
  (let [r (execute (kimi-replay-config) "status" "open-hax/proxx" "445")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "\"kimi\""))
    (is (str/includes? (:out r) "observed commit binding: {\"kimi\""))
    (is (str/includes? (:out r) "exact-head approvals: #{}"))
    (is (some #(= ["api" "repos/open-hax/proxx/pulls/445/reviews/5406474038"]
                  (vec (take 2 (:args %)))) (:calls r)))
    (is (empty? (mutations r))))
  (let [r (execute (kimi-replay-config) "status" "open-hax/uxx" "16")]
    (is (not (str/includes? (:out r) "\"kimi\"")))
    (is (not-any? #(str/includes? (str (:args %)) "/actions/runs/") (:calls r))))
  (doseq [[endpoint at value]
          [["repos/open-hax/proxx/pulls/445/reviews/5406474038" [:body] "No issues found."]
           ["repos/open-hax/proxx/actions/runs/37202872718/attempts/2/jobs?per_page=100"
            [:jobs 2 :conclusion] "failure"]
           ["repos/open-hax/proxx/pulls/445/reviews/5406474038" [:user :id] 1]
           [(str "repos/open-hax/proxx/commits/" (get-in native-kimi-publication [:proof :provenance :workflowSha]))
            [:parents 0 :sha] other]
           ["repos/open-hax/proxx/compare/2810f4515424a146fe37390fb0baf532cca31236...d4d52a39ff1db65ad36e9a429e03489c1208e32d"
            [:status] "diverged"]]]
    (let [r (execute (assoc-in (kimi-replay-config) (into [:apiResponses endpoint] at) value)
                     "status" "open-hax/proxx" "445")]
      (is (not (str/includes? (:out r) "observed commit binding: {\"kimi\"")))
      (is (str/includes? (str (:out r) (:err r)) "publication"))
      (is (empty? (mutations r)))))
  ;; A matching native digest/length must not turn a malformed archive into
  ;; publication evidence. This exercises actual binary transport and unzip.
  (let [bytes (js/Buffer.from "not a ZIP archive")
        sha (.digest (.update (crypto/createHash "sha256") bytes) "hex")
        artifact-endpoint "repos/open-hax/proxx/actions/runs/37202872718/artifacts?per_page=100"
        archive-endpoint "repos/open-hax/proxx/actions/artifacts/11304499400/zip"
        config (-> (kimi-replay-config)
                   (assoc-in [:apiResponses artifact-endpoint :artifacts 0 :digest] (str "sha256:" sha))
                   (assoc-in [:apiResponses artifact-endpoint :artifacts 0 :size_in_bytes] (.-length bytes))
                   (assoc-in [:apiResponses archive-endpoint :binaryBase64] (.toString bytes "base64")))
        r (execute config "status" "open-hax/proxx" "445")]
    (is (str/includes? (:out r) "Kimi publication proof: proof-unavailable"))
    (is (not (str/includes? (:out r) "observed commit binding: {\"kimi\"")))
    (is (not-any? #(str/includes? (str (:args %)) "/contents/") (:calls r)))
    (is (empty? (mutations r)))))

(def native-kimi-passing
  (let [raw (js/JSON.parse (fs/readFileSync (path/join here "fixtures/native-proxx-kimi-passing.json") "utf8"))]
    (assoc (js->clj raw :keywordize-keys true) :apiResponses
           (into {} (for [endpoint (js/Object.keys (.-apiResponses raw))]
                      [endpoint (js->clj (aget (.-apiResponses raw) endpoint) :keywordize-keys true)])))))
(defn kimi-passing-config []
  (-> base (assoc :head (get-in native-kimi-passing [:review :commit_id])
                  :reviews [(:review native-kimi-passing)] :apiResponses (:apiResponses native-kimi-passing)
                  :flow-data the-flow)))

(deftest native-kimi-current-passing-cli-and-artifact-guards
  (let [r (execute (kimi-passing-config) "status" "open-hax/proxx" "445")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "exact-head approvals: #{\"kimi\"}"))
    (is (str/includes? (:out r) "observed commit binding: {\"kimi\""))
    (is (some #(= ["api" "repos/open-hax/proxx/pulls/445/reviews/5406638390"] (vec (take 2 (:args %)))) (:calls r)))
    (is (empty? (mutations r))))
  (doseq [[endpoint at value]
          [["repos/open-hax/proxx/pulls/445/reviews/5406638390" [:user :node_id] "BOT_other"]
           ["repos/open-hax/proxx/pulls/445/reviews/5406638390" [:body] "I found no actionable correctness, security, or workflow findings."]
           ["repos/open-hax/proxx/actions/runs/37208570573/artifacts?per_page=100" [:artifacts 0 :digest] (str "sha256:" (apply str (repeat 64 "0")))]
           ["repos/open-hax/proxx/actions/runs/37208570573/artifacts?per_page=100" [:artifacts 0 :expired] true]
           ["repos/open-hax/proxx/contents/.github/workflows/opencode-code-review.yml?ref=b2a757a3cdf5367792dbdbf5016d9a18ef54cc25" [:sha] (apply str (repeat 40 "0"))]]]
    (let [r (execute (assoc-in (kimi-passing-config) (into [:apiResponses endpoint] at) value) "status" "open-hax/proxx" "445")]
      (is (not (str/includes? (:out r) "exact-head approvals: #{\"kimi\"}")))
      (is (str/includes? (str (:out r) (:err r)) "Kimi publication proof:"))
      (is (empty? (mutations r)))))
  ;; Self-consistent adverse ZIP/body/digests exercise the real transport and
  ;; role proof. These are synthetic derivatives, never native approval facts.
  (let [raw (js/JSON.parse (fs/readFileSync (path/join here "fixtures/native-proxx-kimi-passing.json") "utf8"))]
    (doseq [variant (.-adverseTransports raw)]
      (let [overrides (into {} (for [endpoint (js/Object.keys (.-apiOverrides variant))]
                                 [endpoint (js->clj (aget (.-apiOverrides variant) endpoint) :keywordize-keys true)]))
            config (-> (kimi-passing-config) (assoc :reviews [(js->clj (.-review variant) :keywordize-keys true)])
                       (update :apiResponses merge overrides))
            r (execute config "status" "open-hax/proxx" "445")]
        (is (str/includes? (:out r) "observed commit binding: {\"kimi\"") (.-label variant))
        (is (str/includes? (:out r) "exact-head approvals: #{}") (.-label variant))
        (is (not (str/includes? (:out r) "Kimi publication proof:")) (.-label variant))
        (is (empty? (mutations r))))))
  (let [r (execute (assoc (kimi-passing-config) :reviews [(:canary native-kimi-passing)]) "status" "open-hax/proxx" "445")]
    (is (str/includes? (:out r) "exact-head approvals: #{}"))
    (is (not (str/includes? (:out r) "observed commit binding: {\"kimi\"")))
    (is (empty? (mutations r)))))

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

(deftest cli-split-kimi-producer-is-optional-only-for-the-exact-reviewed-pair
  ;; Source-contract fixture for the new split job, not an observed App run.
  ;; Existing MiMo exact-head approval/history and native Codex account quota
  ;; exercise the unchanged available-agent policy through the actual CLI.
  (let [check {:name "Produce exact-head Kimi review" :workflow "OpenCode Kimi PR Review"
               :required false}
        quota {:id 9000 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
               :created_at "2026-10-03T01:01:00Z"
               :body "You have reached your Codex usage limits for code reviews."}
        config (assoc base :comments [quota])]
    (doseq [[state description] [["FAILURE" "Review failed"] ["PENDING" "Review in progress"]
                                ["IN_PROGRESS" "Review in progress"] ["SKIPPED" "Review skipped"]
                                ["CANCELLED" "Review cancelled"] ["SUCCESS" "Review rate limited"]]]
      (let [row (assoc check :state state :description description)
            input (update config :checks conj row)
            optional (execute input "gate" "owner/repo" "445" "--apply")
            required (execute (update input :checks #(conj (vec (butlast %)) (assoc row :required true)))
                              "gate" "owner/repo" "445" "--apply")
            mandatory (execute input "gate" "owner/repo" "445" "--apply" "--reviewers" "kimi")]
        (is (= 0 (:exit optional)) (str state " " (:out optional) " " (:err optional)))
        (is (= 1 (count (writes optional "merge"))))
        (is (str/includes? (:out optional) "codex quota-unavailable: native source 9000"))
        (is (str/includes? (:out optional) "completed code rounds: 5"))
        (is (some #(some #{"--required"} (:args %)) (:calls required)))
        (doseq [blocked [required mandatory]]
          (is (= 2 (:exit blocked)) (str state " " (:out blocked) " " (:err blocked)))
          (is (str/includes? (:out blocked) "A required reviewer/check"))
          (is (empty? (writes blocked "merge"))))))
    (doseq [row [(assoc check :workflow "Other Kimi workflow")
                 (dissoc check :workflow)
                 (assoc check :name "Produce exact-head Kimi review / lint")
                 (assoc check :name "Produce exact-head Kimi review ")
                 (assoc check :name "Produce deterministic review evidence")
                 (assoc check :name "Compile Muse tools and stage skills")
                 (assoc check :name "Review runner regression tests")
                 (assoc check :name "Kimi structured runner regression tests")]
            state ["FAILURE" "PENDING"]]
      (let [blocked (execute (update config :checks conj (assoc row :state state))
                             "gate" "owner/repo" "445" "--apply")]
        (is (= 2 (:exit blocked)) (str row " " (:out blocked) " " (:err blocked)))
        (is (empty? (writes blocked "merge"))))))
  (doseq [reviews [[] [(assoc approval :commit_id other)]
                   [(assoc-in approval [:user :login] "opencode-agent[bot]")]]]
    (let [blocked (execute (assoc base :reviews reviews
                                 :checks [{:name "laws" :state "SUCCESS" :required true}
                                          {:name "Produce exact-head Kimi review" :workflow "OpenCode Kimi PR Review"
                                           :state "SUCCESS" :required false}])
                           "gate" "owner/repo" "445" "--apply")]
      (is (= 2 (:exit blocked)))
      (is (empty? (writes blocked "merge"))))))

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
  (doseq [body [(str "The staged diff was truncated at 31 of 85 files; "
                    "the truncated tail was bound to deterministic gates rather than exhaustively read.\nConfirmed findings: none.")
               (str "The staged diff was truncated at 300000 of 634965 bytes, so 15 of 39 files - "
                    "including law.cljc and all five test files - are outside this inline review; "
                    "their behavior is evidenced only by the PR's own preparation logs.\nConfirmed findings: none.")
               (str "The staged pr.diff was truncated at 300 KB of 659 KB (23 of 40 files); "
                    "law.cljc and the test files were read at head only to validate claims anchored on staged lines "
                    "and are not exhaustively reviewed.\nFindings: none confirmed.")]]
    (let [partial (assoc approval :body body)
        r (execute (assoc base :reviews [partial]) "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 2 (:exit r)))
    (is (str/includes? (:out r) "gate: BLOCKED"))
    (is (str/includes? (:out r) "incomplete review scope:"))
    (is (str/includes? (:out r) ":unreviewed-input"))
      (is (empty? (writes r "merge"))))))

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

(deftest walkthrough-does-not-block-and-coverage-alone-cannot-clear-quota
  (let [bot {:user {:login "coderabbitai[bot]" :type "Bot"}}
        notice (assoc bot :created_at "2026-10-03T01:00:00Z" :body "Review limit reached.")
        done (assoc bot :updated_at "2026-10-03T01:03:00Z"
                    :body (str "<!-- final_review_risk_coverage: {\"kind\":\"reviewed\",\"sourceCommitId\":\""
                               head "\",\"coveredCommitId\":\"" head "\"} -->"))]
    (let [result (execute (assoc base :comments [(assoc bot :body "The workflow preserves rate-limited reviews; operators wait 53 minutes.")])
                          "request" "riatzukiza/.agents" "8" "code")]
        (is (= 0 (:exit result)) (:err result))
        (is (= 1 (count (writes result "comment")))))
    (let [result (execute (assoc base :comments [notice done]) "request" "riatzukiza/.agents" "8" "code")]
      (is (= 1 (:exit result)))
      (is (empty? (writes result "comment"))))))

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

(deftest available-agent-cohort-is-wired-to-status-and-guarded-merge
  (let [request {:id 500 :user {:login "riatzukiza" :type "User"}
                 :created_at "2026-10-03T00:59:00Z"
                 :body (str "@codex review <!-- pr-flow-stage:code --> <!-- pr-flow-review:" head
                            " --> <!-- pr-flow-reviewer:codex -->")}
        quota {:id 501 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
               :created_at "2026-10-03T01:01:00Z"
               :body "You have reached your Codex usage limits for code reviews."}
        cr (assoc approval :id 502 :user {:login "coderabbitai[bot]" :type "Bot"})
        config (-> base (dissoc :prior-stage-rounds)
                   (assoc :reviews [approval cr] :comments [request quota]
                          :checks [{:name "laws" :state "SUCCESS" :required true}]))
        status (execute config "status" "riatzukiza/.agents" "8")
        merge (execute config "gate" "riatzukiza/.agents" "8" "--apply")]
    (let [red (execute (assoc config :baseline-source-ref "bc2dca2e80a6da4510ff625f18f0f100f70d7da9")
                       "gate" "riatzukiza/.agents" "8" "--apply")]
      (is (= 2 (:exit red)))
      (is (str/includes? (:out red) "completed code rounds: 0"))
      (is (empty? (writes red "merge"))))
    (is (= 0 (:exit status)) (:err status))
    (is (str/includes? (:out status) "completed code rounds: 1"))
    (is (str/includes? (:out status) "codex quota-unavailable: native source 501 request 500"))
    (is (str/includes? (:out status) "retry-at-ms UNKNOWN"))
    (is (str/includes? (:out status) "gate: PASS"))
    (is (= 0 (:exit merge)) (:err merge))
    (is (= 1 (count (writes merge "merge"))))
    (is (some #(some #{head} (:args %)) (writes merge "merge")))
    (let [automatic (execute (assoc config :comments [quota]) "gate" "riatzukiza/.agents" "8" "--apply")]
      (is (= 0 (:exit automatic)) (:err automatic))
      (is (= 1 (count (writes automatic "merge")))))
    (let [pending {:id 503 :user {:login "riatzukiza" :type "User"} :created_at "2026-10-03T01:01:00Z"
                   :body (str "@coderabbitai full review <!-- pr-flow-stage:code --> <!-- pr-flow-round:1 --> "
                              "<!-- pr-flow-review:" head " --> <!-- pr-flow-reviewer:coderabbit -->")}
          r (execute (update config :comments conj pending) "request" "riatzukiza/.agents" "8" "code")]
      (is (= 0 (:exit r)) (:err r))
      (is (str/includes? (:out r) "No request sent: pending"))
      (is (empty? (writes r "comment"))))
    (let [done (assoc approval :id 504 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
                      :submitted_at "2026-10-03T01:02:00Z" :body "Review complete.")
          r (execute (update config :reviews conj done) "request" "riatzukiza/.agents" "8" "code" "--reviewer" "codex")]
      (is (= 0 (:exit r)) (:err r))
      (is (= 1 (count (writes r "comment")))))
    (doseq [blocked [(assoc config :comments [request])
                     (assoc-in config [:comments 1 :user :type] "User")
                     (assoc config :reviews [approval])
                     (assoc-in config [:checks 0 :state] "FAILURE")]]
      (let [r (execute blocked "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= 2 (:exit r)))
        (is (empty? (writes r "merge")))))
    (let [r (execute config "gate" "riatzukiza/.agents" "8" "--apply" "--reviewers" "codex")]
      (is (= 2 (:exit r)))
      (is (empty? (writes r "merge"))))))

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
  (let [cohort (fn [rounds]
                 (assoc base :prior-stage-rounds 0
                        :reviews (vec (remove #(= "coderabbitai[bot]" (get-in % [:user :login]))
                                              (completed-stage-reviews rounds head)))
                        :comments (completed-stage-comments rounds head "code")
                        :checks [{:name "laws" :state "SUCCESS" :required true}
                                 {:name "CodeRabbit" :state "SUCCESS"}]))
        config (cohort 5)
        request (execute config "request" "riatzukiza/.agents" "8" "code")
        verdict {:user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
                 :created_at "2026-10-03T01:00:00Z"
                 :body (str "Codex Review: Didn't find any major issues. :tada:\n\n**Reviewed commit:** `" (subs head 0 10) "`")}
        accepted (execute (update config :comments conj verdict) "gate" "riatzukiza/.agents" "8" "--apply")
        short (execute (update (cohort 4) :comments conj verdict)
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

(deftest generic-value-options-never-silently-default
  (let [merged (execute base "gate" "riatzukiza/.agents" "8" "--apply" "--method=squash")
        note "Keep literal = values and $() `text`"
        requested (execute (assoc base :checks [{:name "laws" :state "SUCCESS" :required true}])
                           "request" "riatzukiza/.agents" "8" "code" "--reviewer=codex" (str "--note=" note))
        comments (writes requested "comment")]
    (is (= 0 (:exit merged)) (:err merged))
    (is (= 1 (count (writes merged "merge"))))
    (is (some #{"--squash"} (:args (first (writes merged "merge")))))
    (is (not-any? #{"--merge"} (:args (first (writes merged "merge")))))
    (is (= 0 (:exit requested)) (:err requested))
    (is (= 1 (count comments)))
    (is (str/starts-with? (:input (first comments)) "@codex review"))
    (is (str/includes? (:input (first comments)) note))))

(deftest malformed-value-options-stop-before-github-effects
  (doseq [args [["gate" "riatzukiza/.agents" "8" "--apply" "--method"]
               ["gate" "riatzukiza/.agents" "8" "--apply" "--method="]
               ["gate" "riatzukiza/.agents" "8" "--apply" "--method=--admin"]
               ["gate" "riatzukiza/.agents" "8" "--method=merge" "--method" "squash"]
               ["request" "riatzukiza/.agents" "8" "code" "--reviewer"]
               ["request" "riatzukiza/.agents" "8" "code" "--reviewer="]
               ["request" "riatzukiza/.agents" "8" "code" "--reviewer=--note"]
               ["request" "riatzukiza/.agents" "8" "code" "--reviewer=codex" "--reviewer" "coderabbit"]
               ["request" "riatzukiza/.agents" "8" "code" "--note"]
               ["request" "riatzukiza/.agents" "8" "code" "--note= "]
               ["request" "riatzukiza/.agents" "8" "code" "--note" "--reviewer" "codex"]
               ["request" "riatzukiza/.agents" "8" "code" "--note=one" "--note" "two"]]]
    (let [r (apply execute base args)]
      (is (= 1 (:exit r)) (str args " " (:err r)))
      (is (empty? (:calls r)) (str args " must have no GitHub calls")))))

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

(deftest unavailable-history-retains-native-evidence-and-blocks-informational-admission
  (let [s (first informational/captures)
        t (informational/evidence (informational/input s))
        accepted (first (:observations (actionability/disposition t)))
        receipt {:origin "pr-flow-actionability-observation" :decisions [accepted]}
        healthy (str (pr-str receipt) "\n")
        config (informational-config s)
        original-root (get-in s [:thread :comments :nodes 0])
        defect (-> (:thread s)
                   (assoc :id "PRRT_history_defect" :isResolved false)
                   (assoc-in [:comments :totalCount] 1)
                   (assoc-in [:comments :nodes]
                             [(assoc original-root :body "P1: real reviewer defect remains visible"
                                     :author {:login "other-reviewer" :__typename "User" :id "U_other" :databaseId 9001})]))
        response (update-in (informational-response s) [:data :repository :pullRequest :reviewThreads :nodes] conj defect)]
    (doseq [history [{:receipt-text (str "{broken\n" healthy)}
                     {:receipt-text (str healthy "{broken\n")}
                     {:receipt-text (str healthy "nil\n")}
                     {:receipt-text (str healthy "{:origin \"pr-flow-actionability-observation\" :decisions nil}\n")}
                     {:receipt-text (str healthy "{:origin \"other\"} trailing\n")}
                     {:no-receipt-ledger true} {:receipt-directory true}]]
      (let [c (merge config history)
            displayed (assoc c :threadPageResponses [response])
            status (execute displayed "status" "open-hax/proxx" "445")
            threads (execute displayed "threads" "open-hax/proxx" "445" "--all")
            gate (execute c "gate" "open-hax/proxx" "445" "--apply")]
        (is (= 0 (:exit status)) (str history " " (:err status)))
        (is (str/includes? (:out status) "threads: 2 total"))
        (is (str/includes? (:out status) "informational 0"))
        (is (str/includes? (:out status) "checks: {:pass 1}"))
        (is (str/includes? (:out status) "actionability history: UNAVAILABLE"))
        (is (str/includes? (:out status) "gate: BLOCKED"))
        (is (= 0 (:exit threads)) (:err threads))
        (is (str/includes? (:out threads) "PRRT_history_defect"))
        (is (str/includes? (:out threads) "real reviewer defect remains visible"))
        (is (str/includes? (:out threads) "actionability: finding unavailable"))
        (is (= 2 (:exit gate)) (str (:err gate) (:out gate)))
        (is (str/includes? (:out gate) "informational 0"))
        (doseq [result [status threads gate]]
          (is (empty? (writes result "merge")))
          (is (empty? (mutations result)))
          (is (= (:receipt-text history) (:receipts result)) "Unavailable history must never be rewritten or appended"))))))

(deftest known-empty-and-complete-history-still-admit-healthy-native-evidence
  (let [s (first informational/captures)
        t (informational/evidence (informational/input s))
        accepted (first (:observations (actionability/disposition t)))
        prefix (str (pr-str {:origin "pr-flow-actionability-observation" :decisions [accepted]}) "\n")
        tagged "{:origin \"receipt-river\" :captured-at #inst \"2026-10-03T14:00:00.000-00:00\" :id #uuid \"b0bb29f3-56d5-5c7c-9a1e-f4a1989e952f\"}\n"
        previous-reader (edn/read-string tagged)]
    (is (inst? (:captured-at previous-reader)))
    (is (uuid? (:id previous-reader)))
    (doseq [text ["" prefix (str tagged prefix)]]
      (let [r (execute (assoc (informational-config s) :receipt-text text)
                       "gate" "open-hax/proxx" "445" "--apply")]
        (is (= 0 (:exit r)) (str (:err r) (:out r)))
        (is (str/includes? (:out r) "informational 1"))
        (is (= 1 (count (writes r "merge"))))
        (is (= 1 (count (filter #(= "pr-flow-actionability-observation" (:origin %))
                               (observation-receipts r)))))
        (when (seq text) (is (= text (:receipts r)) "Existing healthy observation is not duplicated"))))))

(deftest default-flow-cli-runs-own-a-temporary-observation-ledger
  (let [s (first informational/captures)
        t (informational/evidence (informational/input s))
        accepted (first (:observations (actionability/disposition t)))
        seed {:ts "2026-10-03T14:04:00Z" :kind :observation :origin "pr-flow-actionability-observation"
              :owner "fixture" :dod "fixture" :pi "fixture" :host "isolated-fixture"
              :manifest [] :refs [] :decisions [accepted]}
        config (-> (informational-config s)
                   (dissoc :flow-data)
                   (assoc :receipt-history [seed]
                          :threadPageResponses [(informational-response (assoc-in s [:thread :isResolved] false))]))
        owned-ledger (path/resolve here ".." ".." ".." ".ημ" "receipts.edn")
        owned-before (when (fs/existsSync owned-ledger) (fs/readFileSync owned-ledger "utf8"))
        r (execute config "status" "open-hax/proxx" "445")
        events (observation-receipts r)]
    (is (= 0 (:exit r)) (:err r))
    (is (str/starts-with? (or (:receipts r) "") (str (pr-str seed) "\n")))
    (is (some #(= :revoked (get-in % [:decisions 0 :status])) events))
    (is (= owned-before (when (fs/existsSync owned-ledger) (fs/readFileSync owned-ledger "utf8")))
        "Default flow fixture must not write the checkout observation ledger")))

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

(defn current-issue-cohort []
  (let [marker (str "<!-- final_review_risk_coverage:{\"sourceCommitId\":\"" head
                    "\",\"coveredCommitId\":\"" head "\",\"kind\":\"reviewed\"} -->")
        done (assoc (last (completed-stage-comments 1 head "code"))
                    :body (str "Full review finished.\n<!-- recent_review_start -->\n"
                               "No actionable comments were generated in the recent review.\n"
                               "Reviewing files at " head ".\n<!-- recent_review_end -->\n" marker))]
    (assoc base :prior-stage-rounds 0
           :reviews (vec (remove #(= "coderabbitai[bot]" (get-in % [:user :login]))
                                 (completed-stage-reviews 1 head)))
           :comments (conj (vec (butlast (completed-stage-comments 1 head "code"))) done))))

(deftest coderabbit-issue-cohort-needs-a-successful-native-check
  (doseq [state [nil "PENDING" "FAILURE" "SKIPPED" "CANCELLED"]]
    (let [checks (cond-> [{:name "laws" :state "SUCCESS" :required true}]
                   state (conj {:name "CodeRabbit" :state state}))
          r (execute (assoc (current-issue-cohort) :checks checks)
                     "gate" "riatzukiza/.agents" "8" "--apply")]
      (is (= 2 (:exit r)) (str state " " (:out r) " " (:err r)))
      (is (str/includes? (:out r) "completed code rounds: 0"))
      (is (empty? (writes r "merge")))))
  (let [r (execute (assoc (current-issue-cohort)
                         :checks [{:name "laws" :state "SUCCESS" :required true}
                                  {:name "CodeRabbit" :state "SUCCESS"}])
                   "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "completed code rounds: 1"))
    (is (= 1 (count (writes r "merge"))))))

(deftest coderabbit-formal-review-keeps-optional-and-required-checks-distinct
  (let [formal (assoc approval :id 700 :user {:login "coderabbitai[bot]" :type "Bot"}
                      :body "No actionable comments were generated.")
        config (update (current-issue-cohort) :reviews conj formal)]
    (doseq [state [nil "FAILURE" "SKIPPED"]]
      (let [checks (cond-> [{:name "laws" :state "SUCCESS" :required true}]
                     state (conj {:name "CodeRabbit" :state state}))
            r (execute (assoc config :checks checks) "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= 0 (:exit r)) (:err r))
        (is (str/includes? (:out r) "completed code rounds: 1"))
        (is (= 1 (count (writes r "merge"))))))
    (doseq [state ["PENDING" "FAILURE" "SKIPPED" "CANCELLED"]]
      (let [r (execute (assoc config :checks [{:name "laws" :state "SUCCESS" :required true}
                                             {:name "CodeRabbit" :state state :required true}])
                       "gate" "riatzukiza/.agents" "8" "--apply")]
        (is (= 2 (:exit r)))
        (is (empty? (writes r "merge")))))))

(deftest coderabbit-issue-check-mutation-blocks-the-native-snapshot
  (let [success [{:name "laws" :state "SUCCESS" :required true}
                 {:name "CodeRabbit" :state "SUCCESS"}]
        failed (assoc-in success [1 :state] "FAILURE")
        r (execute (assoc (current-issue-cohort) :checksSequence [success failed])
                   "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 2 (:exit r)))
    (is (empty? (writes r "merge"))))
  ;; Passing provider evidence never waives a neighboring deterministic gate.
  (let [r (execute (assoc (current-issue-cohort)
                         :checks [{:name "laws" :state "SUCCESS" :required true}
                                  {:name "CodeRabbit" :state "SUCCESS"}
                                  {:name "coderabbit-review-gate" :state "FAILURE" :required true}])
                   "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 2 (:exit r)))
    (is (empty? (writes r "merge")))))

(def historical-cr-status
  {:id 55517229599 :context "CodeRabbit" :state "success"
   :created_at "2026-10-03T00:05:00Z" :updated_at "2026-10-03T00:05:00Z"
   :creator {:login "coderabbitai[bot]" :type "Bot"}
   :url "https://api.github.com/repos/riatzukiza/.agents/statuses/historical-fixture"})
(defn historical-issue-cohort []
  (assoc base :prior-stage-rounds 0
         :comments (completed-stage-comments 5 other "code")
         :reviews (into (vec (remove #(= "coderabbitai[bot]" (get-in % [:user :login]))
                                     (completed-stage-reviews 5 other))) [approval])
         :commitStatuses {other [historical-cr-status]}))

(deftest historical-checked-issue-cohort-survives-push-and-stays-current-approval-free
  (let [config (historical-issue-cohort)
        r (execute config "gate" "riatzukiza/.agents" "8" "--apply")
        no-approval (execute (update config :reviews #(filterv (fn [review] (not= head (:commit_id review))) %))
                             "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "completed code rounds: 5"))
    (is (str/includes? (:out r) "exact-head approvals: #{\"mimo\"}"))
    (is (= 1 (count (writes r "merge"))))
    (is (= 2 (:exit no-approval)))
    (is (str/includes? (:out no-approval) "completed code rounds: 5"))
    (is (empty? (writes no-approval "merge")))))

(deftest historical-issue-check-provenance-and-availability-fail-closed
  (doseq [rows [[] [(assoc historical-cr-status :state "pending")]
                 [historical-cr-status (assoc historical-cr-status :id 2 :state "failure"
                                             :created_at "2026-10-03T00:06:00Z")]
                 [historical-cr-status (assoc historical-cr-status :id 2 :state "pending"
                                             :created_at "2026-10-03T00:06:00Z")]
                 [(assoc-in historical-cr-status [:creator :type] "User")]
                 [(assoc-in historical-cr-status [:creator :login] "fake-coderabbit[bot]")]
                 [(assoc historical-cr-status :context "CodeRabbit review gate")]
                 [(dissoc historical-cr-status :id)]
                 [(assoc historical-cr-status :created_at "invalid")]]]
    (let [r (execute (assoc (historical-issue-cohort) :commitStatuses {other rows})
                     "gate" "riatzukiza/.agents" "8" "--apply")]
      (is (= 2 (:exit r)))
      (is (str/includes? (:out r) "completed code rounds: 0"))
      (is (str/includes? (:out r) "without successful exact-commit check evidence"))
      (is (empty? (writes r "merge")))))
  (doseq [change [{:unavailableCommitStatuses [other]} {:commitStatusPages {other nil}}
                  {:commitStatusPages {other {:unexpected []}}} {:commitResponses {other head}}]]
    (let [r (execute (merge (historical-issue-cohort) change) "gate" "riatzukiza/.agents" "8" "--apply")]
      (is (not= 0 (:exit r)))
      (is (empty? (writes r "merge")))))
  (let [r (execute (assoc (historical-issue-cohort)
                         :commitStatusPages {other [[(assoc historical-cr-status :state "pending"
                                                          :created_at "2026-10-03T00:04:00Z")]
                                                    [historical-cr-status]]})
                   "gate" "riatzukiza/.agents" "8" "--apply")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "completed code rounds: 5"))))

(deftest historical-check-fetches-require-native-completion-and-writer-authorization
  (doseq [config [(assoc (historical-issue-cohort) :authorized false)
                  (update (historical-issue-cohort) :comments
                          #(mapv (fn [c] (if (= "coderabbitai[bot]" (get-in c [:user :login]))
                                          (assoc-in c [:user :type] "User") c)) %))]]
    (let [r (execute config "status" "riatzukiza/.agents" "8")]
      (is (empty? (filter #(str/ends-with? (str (second (:args %))) "/statuses") (:calls r)))))))

(defn withdrawal-config
  "Real native Sol thread transported through the existing isolated gh seam.
   Only the prospective writer Handled comment is synthetic; no live writes."
  [raw]
  (assoc base :head withdrawal/head :prAuthor "riatzukiza"
         :reviews [(assoc approval :commit_id withdrawal/head)]
         :threads [raw]
         :threadPageResponses
         [{:data {:repository (assoc (:repository withdrawal/fixture)
                                     :pullRequest (assoc (:pr withdrawal/fixture) :isDraft false
                                                         :reviewThreads {:pageInfo {:hasNextPage false} :nodes [raw]}))}}]))

(deftest native-opener-withdrawal-readonly-cli-seam-red-green
  (let [config (withdrawal-config withdrawal/prospective)
        baseline (execute (assoc config :baseline-source-ref "0f95afe56fb01fbcf9d7934b72ce31fe96baf451")
                          "gate" "riatzukiza/sol" "3")
        current (execute config "gate" "riatzukiza/sol" "3")
        displayed (execute config "threads" "riatzukiza/sol" "3" "--all")
        actual (execute (withdrawal-config withdrawal/actual) "gate" "riatzukiza/sol" "3")]
    ;; Read-only gate is diagnostic and exits zero even while BLOCKED.
    (is (= 0 (:exit baseline)) (:err baseline))
    (is (str/includes? (:out baseline) "gate: BLOCKED"))
    (is (str/includes? (:out baseline) "P0/P1 thread(s) not fixed"))
    (is (= 0 (:exit current)) (str (:err current) (:out current)))
    (is (str/includes? (:out displayed) "native opener withdrawal: id=4176945609"))
    (is (str/includes? (:out displayed) withdrawal/withdrawal-url))
    (is (= 0 (:exit actual)))
    (is (str/includes? (:out actual) "gate: BLOCKED"))
    (is (str/includes? (:out actual) "without a settlement reply"))
    (doseq [r [baseline current displayed actual]]
      (is (empty? (mutations r)))
      (is (empty? (writes r "merge")))))
  (doseq [config [(assoc (withdrawal-config withdrawal/prospective) :authorized false)
                  (withdrawal-config (assoc withdrawal/prospective :isResolved false))
                  (withdrawal-config (assoc-in withdrawal/prospective [:comments :nodes 2 :author :__typename] "User"))
                  (withdrawal-config (assoc-in withdrawal/prospective [:comments :nodes 2 :pullRequestReview :commit :oid] other))]]
    (let [r (execute config "gate" "riatzukiza/sol" "3")]
      (is (= 0 (:exit r)))
      (is (str/includes? (:out r) "gate: BLOCKED"))
      (is (str/includes? (:out r) "P0/P1 thread(s) not fixed"))
      (is (empty? (mutations r)))
      (is (empty? (writes r "merge"))))))

(def rheos-stage-fixture
  (js->clj (js/JSON.parse (fs/readFileSync (path/join here "fixtures/native-rheos2-initial-code-stage.json") "utf8"))
           :keywordize-keys true))
(defn rheos-stage-config
  "The complete native review/comment/thread/check observation at ab6.
   Counterexamples change only local seam inputs, never native records."
  []
  (-> base (dissoc :prior-stage-rounds)
      (merge (select-keys rheos-stage-fixture [:head :reviews :comments :checks]))
      (assoc :prAuthor "riatzukiza" :threadPageResponses [(:threadResponse rheos-stage-fixture)])))

(deftest actual-legacy-stage-declaration-cli-red-green-without-review-rebinding
  (let [config (rheos-stage-config)
        baseline (execute (assoc config :baseline-source-ref "0f95afe56fb01fbcf9d7934b72ce31fe96baf451")
                          "status" "open-hax/rheos" "2")
        corrected (execute config "status" "open-hax/rheos" "2")]
    (is (str/includes? (:out baseline) "completed code rounds: 0"))
    (is (str/includes? (:out baseline) "gate: BLOCKED"))
    (is (str/includes? (:out corrected) "completed code rounds: 1"))
    (is (str/includes? (:out corrected) "gate: PASS"))
    (is (str/includes? (:out corrected) "native source 5968489429"))
    (doseq [r [baseline corrected]]
      (is (= 0 (:exit r)) (:err r))
      (is (empty? (mutations r)))
      (is (empty? (writes r "merge")))))
  (doseq [alter [#(str/replace % "pr-flow-stage:code" "pr-flow-stage:planning")
                 #(str "> " (str/replace % "\n" "\n> "))
                 #(str "```text\n" % "\n```")
                 #(str/replace % "66e8b67951971af541503c4a556c5645eaa727c0" "66e8b67")]]
    (let [config (update (rheos-stage-config) :comments
                         #(mapv (fn [c] (if (= 5968488345 (:id c)) (update c :body alter) c)) %))
          r (execute config "status" "open-hax/rheos" "2")]
      (is (str/includes? (:out r) "completed code rounds: 0"))
      (is (str/includes? (:out r) "gate: BLOCKED"))
      (is (empty? (mutations r)))
      (is (empty? (writes r "merge"))))))

(def native-coderabbit-quota-info
  (:comments (js->clj (js/JSON.parse (fs/readFileSync
                                      (path/join here "fixtures/native-coderabbit-quota-info.json") "utf8"))
                      :keywordize-keys true)))

(deftest actual-request-caller-recognizes-native-quota-information
  (let [{:keys [unknown wait available]} native-coderabbit-quota-info
        request {:id 9001 :user {:login "riatzukiza" :type "User"}
                 :created_at (.toISOString (js/Date.))
                 :body (str "@coderabbitai full review <!-- pr-flow-stage:code --> <!-- pr-flow-review:"
                            head " --> <!-- pr-flow-reviewer:coderabbit -->")}
        later-unknown (assoc unknown :updated_at (.toISOString (js/Date.)))
        later-wait (assoc wait :updated_at (.toISOString (js/Date.)))
        malformed (assoc later-wait :body (str/replace (:body wait) "52 minutes" "unknown minutes"))]
    ;; Only timestamps of synthetic later-boundary variants change; the two
    ;; positive cases replay exact native bodies, identities and timestamps.
    (doseq [comments [[unknown wait] [unknown available]]]
      (let [r (execute (assoc base :comments comments)
                       "request" "riatzukiza/.agents" "8" "code")
            posted (first (writes r "comment"))]
        (is (= 0 (:exit r)) (:err r))
        (is (= 1 (count (writes r "comment"))))
        (is (str/starts-with? (:input posted "") "@coderabbitai full review"))
        (is (str/includes? (:input posted "") (str "pr-flow-review:" head " -->")))
        (is (str/includes? (:input posted "") "pr-flow-reviewer:coderabbit -->"))
        (is (str/includes? (:input posted "") "pr-flow-round:6 -->"))
        (is (empty? (mutations r)))
        (is (empty? (writes r "merge")))))
    (doseq [[comments expected] [[[unknown] "rate-limited"]
                                 [[unknown later-wait] "cooldown"]
                                 [[available later-unknown] "rate-limited"]
                                 [[available malformed] "rate-limited"]
                                 [[unknown available request] "pending"]
                                 [[unknown (assoc-in available [:user :type] "User")] "rate-limited"]
                                 [[unknown (update available :body #(str "```text\n" % "\n```"))] "rate-limited"]]]
      (let [r (execute (assoc base :comments comments)
                       "request" "riatzukiza/.agents" "8" "code")]
        (is (str/includes? (:out r) (str "No request sent: " expected)))
        (is (empty? (writes r "comment")))
        (is (empty? (mutations r)))
        (is (empty? (writes r "merge")))))
    (let [pending (execute (assoc base :comments [unknown available]
                                 :checks [{:name "CodeRabbit" :state "PENDING"}])
                           "request" "riatzukiza/.agents" "8" "code")
          changed (execute (assoc base :comments [unknown available] :heads [head head head other])
                           "request" "riatzukiza/.agents" "8" "code")
          public-marker (execute (assoc base :comments [unknown available request] :authorized false)
                                 "request" "riatzukiza/.agents" "8" "code")]
      (is (str/includes? (:out pending) "No request sent: pending"))
      (is (empty? (writes pending "comment")))
      (is (= 1 (:exit changed)))
      (is (str/includes? (:err changed) "PR head changed before review request"))
      (is (empty? (writes changed "comment")))
      (is (= 0 (:exit public-marker)) (:err public-marker))
      (is (= 1 (count (writes public-marker "comment"))))
      (is (some #(str/includes? (second (:args %)) "/collaborators/riatzukiza/permission")
                (:calls public-marker))))
    (let [codex-quota {:id 9002 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
                       :created_at (.toISOString (js/Date.))
                       :body "You have reached your Codex usage limits for code reviews."}
          r (execute (assoc base :comments [codex-quota available])
                     "request" "riatzukiza/.agents" "8" "code" "--reviewer" "codex")]
      (is (= 1 (:exit r)))
      (is (str/includes? (:out r) "No request sent: rate-limited"))
      (is (empty? (writes r "comment"))))))

(def native-kimi-705
  (let [raw (js/JSON.parse (fs/readFileSync (path/join here "fixtures/native-proxx-kimi-705.json") "utf8"))]
    (assoc (js->clj raw :keywordize-keys true) :apiResponses
           (into {} (for [endpoint (js/Object.keys (.-apiResponses raw))]
                      [endpoint (js->clj (aget (.-apiResponses raw) endpoint) :keywordize-keys true)])))))
(defn kimi-705-config []
  ;; Use the actual shipped flow, never the proposed profile in the capture.
  (-> base (assoc :head (get-in native-kimi-705 [:review :commit_id])
                  :reviews [(:review native-kimi-705)] :apiResponses (:apiResponses native-kimi-705)
                  :flow-data the-flow)))

(deftest native-705-production-cli-source-artifact-and-verdict-binding
  (let [r (execute (kimi-705-config) "status" "open-hax/proxx" "452")]
    (is (= 0 (:exit r)) (:err r))
    (is (str/includes? (:out r) "observed commit binding: {\"kimi\""))
    (is (not (str/includes? (:out r) "Kimi publication proof:")))
    (is (str/includes? (:out r) "exact-head approvals: #{}"))
    (is (some #(= ["api" "repos/open-hax/proxx/pulls/452/reviews/5410087807"]
                  (vec (take 2 (:args %)))) (:calls r)))
    (is (some #(str/includes? (str (:args %)) "/artifacts/11325898191/zip") (:calls r)))
    (is (empty? (mutations r))))
  (let [config (update-in (kimi-705-config) [:flow-data :flow/defaults :review/kimi-publication :workflow-digests]
                          disj "974215a7113347a78bd2e0af48925838cc0d58e54e0acbe3ed7a0aaf5a183736")
        r (execute config "status" "open-hax/proxx" "452")]
    (is (str/includes? (:out r) "Kimi publication proof: mismatch"))
    (is (not (str/includes? (:out r) "observed commit binding: {\"kimi\"")))
    (is (str/includes? (:out r) "exact-head approvals: #{}"))
    (is (empty? (mutations r))))
  (doseq [[endpoint at value]
          [["repos/open-hax/proxx/pulls/452/reviews/5410087807" [:user :node_id] "BOT_other"]
           ["repos/open-hax/proxx/pulls/452/reviews/5410087807" [:body] "No issues found."]
           ["repos/open-hax/proxx/actions/runs/37264607437/attempts/1/jobs?per_page=100" [:jobs 1 :conclusion] "failure"]
           ["repos/open-hax/proxx/actions/runs/37264607437/artifacts?per_page=100" [:artifacts 0 :expired] true]
           ["repos/open-hax/proxx/actions/runs/37264607437/artifacts?per_page=100" [:artifacts 0 :digest]
            (str "sha256:" (apply str (repeat 64 "0")))]]]
    (let [r (execute (assoc-in (kimi-705-config) (into [:apiResponses endpoint] at) value)
                     "status" "open-hax/proxx" "452")]
      (is (not (str/includes? (:out r) "observed commit binding: {\"kimi\"")))
      (is (str/includes? (:out r) "Kimi publication proof:"))
      (is (str/includes? (:out r) "exact-head approvals: #{}"))
      (is (empty? (mutations r)))))
  (let [r (execute (kimi-705-config) "status" "open-hax/uxx" "14")]
    (is (not (str/includes? (:out r) "observed commit binding: {\"kimi\"")))
    (is (not-any? #(str/includes? (str (:args %)) "/actions/runs/") (:calls r)))
    (is (empty? (mutations r)))))

(deftest observation-writer-binds-the-repository-for-admission-and-revocation
  (doseq [s informational/captures]
    (let [repo (get-in s [:repository :nameWithOwner])
          n (str (get-in s [:pr :number]))
          t (informational/evidence (informational/input s))
          accepted (first (:observations (actionability/disposition t)))
          seed {:ts "2026-10-03T14:00:00Z" :kind :observation :repo repo
                :origin "existing-receipt" :owner "fixture" :dod "preserve history"
                :pi "fixture" :host "isolated-fixture" :manifest [] :refs []}
          prefix (str (pr-str seed) "\n")
          qualified (execute (assoc (informational-config s) :receipt-text prefix)
                             "status" repo n)
          revoked-seed (assoc seed :origin "pr-flow-actionability-observation"
                             :decisions [accepted])
          revoked-prefix (str (pr-str revoked-seed) "\n")
          revoked (execute (assoc (informational-config s) :receipt-text revoked-prefix
                                  :threadPageResponses
                                  [(informational-response (assoc-in s [:thread :isResolved] false))])
                           "status" repo n)]
      (doseq [[r existing expected] [[qualified prefix :qualified]
                                    [revoked revoked-prefix :revoked]]]
        (is (= 0 (:exit r)) (:err r))
        (is (str/starts-with? (:receipts r) existing))
        (let [appended (map edn/read-string
                            (remove str/blank? (str/split-lines
                                               (subs (:receipts r) (count existing)))))]
          (is (= 1 (count appended)))
          (is (= repo (:repo (first appended)))
              "Bind the repository name supplied to the native CLI, not its opaque GraphQL ID")
          (is (= expected (get-in (first appended) [:decisions 0 :status])))
          (is (= (:thread-id accepted) (get-in (first appended) [:decisions 0 :thread-id]))))
        (is (empty? (writes r "merge")))))))

(deftest observation-reader-scopes-explicit-repositories-after-validating-all-history
  (doseq [s informational/captures]
    (let [repo (get-in s [:repository :nameWithOwner])
          n (str (get-in s [:pr :number]))
          t (informational/evidence (informational/input s))
          accepted (first (:observations (actionability/disposition t)))
          record {:ts "2026-10-03T14:04:00Z" :kind :observation :repo repo
                  :origin "pr-flow-actionability-observation" :owner "fixture"
                  :dod "scope stored history" :pi "fixture" :host "isolated-fixture"
                  :manifest [] :refs [] :decisions [accepted]}
          local (str (pr-str record) "\n")
          ;; Deliberate synthetic ID reuse exercises repository isolation. This
          ;; is not evidence that an actual native GitHub ID collided.
          foreign-revoked (assoc record :repo "other/foreign"
                                :decisions [(assoc accepted :repo-id "R_foreign"
                                                   :pr-id "PR_foreign" :thread-id "PRRT_foreign"
                                                   :status :revoked :reason :withdrawn)])
          prefix (str local (pr-str foreign-revoked) "\n")
          healthy (execute (assoc (informational-config s) :receipt-text prefix)
                           "status" repo n)]
      (is (= 0 (:exit healthy)) (:err healthy))
      (is (str/includes? (:out healthy) "informational 1") (:out healthy))
      (is (not (str/includes? (:out healthy) "actionability history: UNAVAILABLE")))
      (is (= prefix (:receipts healthy)) "Never rewrite, delete or duplicate scoped history")
      (is (empty? (mutations healthy)))
      ;; Current-repository and repository-absent legacy revocations remain
      ;; terminal even when the corresponding native source is still present.
      (doseq [seed [(assoc record :decisions [accepted (assoc accepted :status :revoked :reason :withdrawn)])
                    (dissoc (assoc record :decisions [accepted (assoc accepted :status :revoked :reason :withdrawn)]) :repo)]]
        (let [text (str (pr-str seed) "\n")
              blocked (execute (assoc (informational-config s) :receipt-text text)
                               "gate" repo n "--apply")]
          (is (= 2 (:exit blocked)) (:err blocked))
          (is (str/includes? (:out blocked) "informational 0"))
          (is (not (str/includes? (:out blocked) "history: UNAVAILABLE")))
          (is (str/starts-with? (:receipts blocked) text))
          (is (empty? (mutations blocked)))))
      ;; Parse and validate the entire ledger before selecting repositories:
      ;; malformed foreign records at either end are never silently skipped.
      (doseq [invalid [(str (pr-str (assoc foreign-revoked :decisions nil)) "\n")
                       "{:repo \"other/foreign\" :origin \"other\"} trailing\n"]
              text [(str invalid local) (str local invalid)]]
        (let [blocked (execute (assoc (informational-config s) :receipt-text text)
                               "gate" repo n "--apply")]
          (is (= 2 (:exit blocked)) (:err blocked))
          (is (str/includes? (:out blocked) "actionability history: UNAVAILABLE"))
          (is (= text (:receipts blocked)))
          (is (empty? (mutations blocked)))))
      (let [blocked (execute (assoc (informational-config s) :receipt-text prefix
                                    :checks [{:name "laws" :state "FAILURE" :required true}])
                             "gate" repo n "--apply")]
        (is (= 2 (:exit blocked)))
        (is (empty? (mutations blocked)))))))

(deftest observation-repository-case-round-trip-retains-revocation
  (doseq [s informational/captures]
    (let [repo (get-in s [:repository :nameWithOwner])
          varied (str/upper-case repo)
          n (str (get-in s [:pr :number]))
          accepted (first (:observations (actionability/disposition
                                          (informational/evidence (informational/input s)))))
          seed {:ts "2026-10-03T14:04:00Z" :kind :observation :repo repo
                :origin "existing-receipt" :owner "fixture" :dod "case round trip"
                :pi "fixture" :host "isolated-fixture" :manifest [] :refs []}
          prefix (str (pr-str seed) "\n")
          qualified (execute (assoc (informational-config s) :receipt-text prefix)
                             "status" varied n)
          qualified-record (last (observation-receipts qualified))]
      (is (= 0 (:exit qualified)) (:err qualified))
      (is (str/includes? (:out qualified) "informational 1"))
      (is (str/starts-with? (:receipts qualified) prefix))
      (is (= (str/lower-case repo) (:repo qualified-record)))
      (is (= :qualified (get-in qualified-record [:decisions 0 :status])))
      (is (empty? (mutations qualified)))
      ;; Both existing mixed-case records and varied CLI spellings must keep
      ;; the same native repository's stored revocation terminal.
      (doseq [[stored operator] [[repo varied] [varied repo] [varied varied]]]
        (let [record (assoc seed :repo stored :origin "pr-flow-actionability-observation"
                           :decisions [accepted (assoc accepted :status :revoked :reason :withdrawn)])
              text (str (pr-str record) "\n")
              blocked (execute (assoc (informational-config s) :receipt-text text)
                               "gate" operator n "--apply")]
          (is (= 2 (:exit blocked)) (:err blocked))
          (is (str/includes? (:out blocked) "informational 0") (:out blocked))
          (is (not (str/includes? (:out blocked) "history: UNAVAILABLE")))
          (is (= text (:receipts blocked)))
          (is (empty? (writes blocked "merge")))
          (is (empty? (mutations blocked)))))
      ;; The writer uses the same normalized key when a real native-state
      ;; change revokes a previously admitted observation.
      (let [revoked (execute (assoc (informational-config s) :receipt-text (:receipts qualified)
                                   :threadPageResponses
                                   [(informational-response (assoc-in s [:thread :isResolved] false))])
                            "status" varied n)
            revoked-record (last (observation-receipts revoked))
            round-trip (execute (assoc (informational-config s) :receipt-text (:receipts revoked))
                                "gate" repo n "--apply")]
        (is (= 0 (:exit revoked)) (:err revoked))
        (is (str/starts-with? (:receipts revoked) (:receipts qualified)))
        (is (= (str/lower-case repo) (:repo revoked-record)))
        (is (= :revoked (get-in revoked-record [:decisions 0 :status])))
        (is (= 2 (:exit round-trip)) (:err round-trip))
        (is (str/includes? (:out round-trip) "informational 0"))
        (is (= (:receipts revoked) (:receipts round-trip)))
        (is (empty? (writes round-trip "merge")))
        (is (empty? (mutations round-trip)))))))

;; Stable native repository history, using existing isolated CLI fixtures.
(defn repository-scope-execute [label config repo n]
  (let [r (execute config "gate" repo n)]
    (is (empty? (writes r "merge")) label)
    (is (empty? (mutations r)) label)
    (is (str/starts-with? (:receipts r) (:receipt-text config)) label)
    (assoc r :history-seed (:receipt-text config))))
(defn record-for [s display]
  (let [accepted (first (:observations (actionability/disposition (informational/evidence (informational/input s)))))]
    {:ts "2026-10-05T16:25:00Z" :kind :observation :repo display :origin "pr-flow-actionability-observation"
     :owner "isolated-fixture" :dod "stable native repository history" :pi "fixture" :host "local fakeGH"
     :manifest [] :refs [] :decisions [accepted (assoc accepted :status :revoked :reason :withdrawn)]}))
(defn text-of [record] (str (pr-str record) "\n"))
(defn require-block [r label]
  (is (= (:history-seed r) (:receipts r)) label)
  (is (str/includes? (:out r) "gate: BLOCKED") label)
  (is (str/includes? (:out r) "informational 0") label))
(defn require-unavailable [r label]
  (require-block r label)
  (is (str/includes? (:out r) "actionability history: UNAVAILABLE") label))
(defn require-pass [r label]
  (is (str/includes? (:out r) "gate: PASS") label)
  (is (str/includes? (:out r) "informational 1") label))
(defn require-revoked-block [r label]
  ;; The real CLI appends a terminal revocation when existing qualified
  ;; evidence loses authority; preserve that append and the complete seed.
  (let [records (observation-receipts r)]
    (is (str/includes? (:out r) "gate: BLOCKED") label)
    (is (str/includes? (:out r) "informational 0") label)
    (is (= 2 (count records)) label)
    (is (= :revoked (get-in (last records) [:decisions 0 :status])) label)
    (is (= (get-in (first records) [:decisions 0 :assessment-id])
           (get-in (last records) [:decisions 0 :assessment-id])) label)))
(deftest stable-native-repository-history-contract
  (doseq [[ix s] (map-indexed vector informational/captures)]
    (let [repo (get-in s [:repository :nameWithOwner]) n (str (get-in s [:pr :number]))
          record (record-for s repo) config (informational-config s)]
      (doseq [[label r operator] [["canonical" record repo]
                                 ["case" (assoc record :repo (str/upper-case repo)) (str/upper-case repo)]
                                 ["legacy-nil" (assoc record :repo nil) repo]
                                 ["legacy-absent" (dissoc record :repo) repo]]]
        (require-block (repository-scope-execute (str ix "-" label) (assoc config :receipt-text (text-of r)) operator n) label))
      ;; This tests scope selection with healthy current synthetic protocols,
      ;; not immutable native comment mutation during a real GitHub rename.
      (doseq [[label current-name] [["rename-healthy-current-context" (str repo "-renamed")]
                                    ["transfer-healthy-current-context" (str "new-owner/" (last (str/split repo #"/")))]]]
        (let [current (assoc-in s [:repository :nameWithOwner] current-name)
              current-record (record-for current repo)
              r (repository-scope-execute (str ix "-" label) (assoc (informational-config current)
                                                         :receipt-text (text-of current-record)) current-name n)]
          (require-block r label)))
      ;; An actual native name change with the old protocol unchanged is
      ;; independently ineligible through context-digest binding, even in old21ad.
      (let [current-name (str repo "-renamed") current (assoc-in s [:repository :nameWithOwner] current-name)
            unchanged (assoc config :receipt-text (text-of record)
                                    :threadPageResponses [(informational-response current)])]
        (require-block (repository-scope-execute (str ix "-unchanged-old-protocol-context-change") unchanged current-name n)
                       "old protocol must stay ineligible after canonical context changes"))
      ;; New independent native IDs remain eligible; old revoked ID is terminal,
      ;; rather than forbidding all future evidence for the repository.
      (let [current-name (str repo "-renamed") current (assoc-in s [:repository :nameWithOwner] current-name)
            comments (:comments (informational-config current))
            p (informational/hash-body (assoc (first comments) :id 8101 :node_id "IC_fresh_8101"
                                              :created_at "2026-10-03T14:03:00Z" :updated_at "2026-10-03T14:03:00Z"))
            a (-> (second comments)
                  (assoc :id 8102 :node_id "IC_fresh_8102" :created_at "2026-10-03T14:04:00Z" :updated_at "2026-10-03T14:04:00Z")
                  (informational/change-payload #(assoc % 7 (:id p) 8 (:body-sha256 p))) informational/hash-body)
            cfg (assoc (informational-config current) :comments [p a] :receipt-text (text-of record))]
        (require-pass (repository-scope-execute (str ix "-fresh-independent-source") cfg current-name n) "fresh independent IDs remain possible"))
      (let [accepted (first (:decisions record)) local (text-of (assoc record :decisions [accepted]))
            foreign (assoc record :repo "other/foreign" :decisions [(assoc accepted :repo-id "R_foreign" :pr-id "PR_foreign"
                                                                                      :thread-id "PRRT_foreign" :status :revoked)])]
        (require-pass (repository-scope-execute (str ix "-foreign-control") (assoc config :receipt-text (str local (text-of foreign))) repo n)
                      "genuine foreign records excluded")
        (require-pass (repository-scope-execute (str ix "-same-display-foreign-native-id")
                                    (assoc config :receipt-text (str local (text-of (assoc foreign :repo repo)))) repo n)
                      "display-name reuse cannot choose a foreign native repository"))))
  (let [s (first informational/captures) repo (get-in s [:repository :nameWithOwner])
        n (str (get-in s [:pr :number])) record (record-for s repo) config (informational-config s)
        local (text-of record) accepted (first (:decisions record))]
    (doseq [[label value] [["missing" ::missing] ["nil" nil] ["number" 42] ["blank" " "] ["map" {}]]]
      (let [bad (update record :decisions #(mapv (fn [o] (if (= ::missing value) (dissoc o :repo-id) (assoc o :repo-id value))) %))]
        (require-unavailable (repository-scope-execute (str "invalid-decision-id-" label)
                                           (assoc config :receipt-text (text-of bad)) repo n) label)))
    (let [bad (assoc record :decisions [accepted (assoc accepted :repo-id "R_foreign" :status :revoked)])]
      (require-unavailable (repository-scope-execute "same-record-conflicting-native-ids" (assoc config :receipt-text (text-of bad)) repo n)
                           "conflicting IDs cannot be partially selected"))
    (require-pass (repository-scope-execute "empty-decisions-no-op" (assoc config :receipt-text (text-of (assoc record :decisions []))) repo n)
                  "valid empty decisions do not fabricate history")
    (doseq [[ix bad] (map-indexed vector [(text-of (assoc record :repo "other/foreign" :decisions nil))
                                         "{:repo \"other/foreign\" :origin \"other\"} trailing\n"
                                         (text-of (assoc record :repo 42))])
            [side text] [["front" (str bad local)] ["tail" (str local bad)]]]
      (require-unavailable (repository-scope-execute (str "whole-parse-" ix "-" side) (assoc config :receipt-text text) repo n)
                           "complete history validates before selection"))
    (doseq [[label value] [["nil" nil] ["number" 42] ["blank" " "]]]
      (let [response (informational-response (assoc-in s [:repository :id] value))]
        (require-unavailable (repository-scope-execute (str "native-identity-" label)
                                           (assoc config :receipt-text local :threadPageResponses [response]) repo n) label)))
    (let [healthy-record (text-of (assoc record :decisions [accepted]))]
      (doseq [[label cfg] [["spoof-assessor-id" (update-in config [:comments 1 :user :id] inc)]
                          ["spoof-assessor-node" (assoc-in config [:comments 1 :user :node_id] "BOT_wrong")]
                          ["human-assessor" (assoc-in config [:comments 1 :user :type] "User")]
                          ["edited-assessment" (assoc-in config [:comments 1 :updated_at] "2026-10-03T14:02:00Z")]
                          ["stale-native-head" (assoc config :head (apply str (repeat 40 "f"))
                                                          :threadPageResponses [(informational-response (assoc-in s [:pr :headRefOid]
                                                                                                                  (apply str (repeat 40 "f"))))])]
                          ["context-digest" (update-in config [:comments 1]
                                                       #(-> % (informational/change-payload (fn [v] (assoc v 5 (apply str (repeat 64 "a")))))
                                                              informational/hash-body))]
                          ["proposal-id" (update-in config [:comments 1]
                                                    #(-> % (informational/change-payload (fn [v] (assoc v 7 9001))) informational/hash-body))]]]
        (require-revoked-block (repository-scope-execute (str "authority-" label) (assoc cfg :receipt-text healthy-record) repo n) label)))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))

(def native-coderabbit-next-review-info
  (:comments (js->clj (js/JSON.parse (fs/readFileSync
                                      (path/join here "fixtures/native-coderabbit-next-review-info.json") "utf8"))
                      :keywordize-keys true)))

(defn execute-at [instant config & args]
  ;; Substitute only the child process clock. Real pr.cljs, law, native REST
  ;; data, writer lookup and final head recheck run through the existing fake gh.
  (let [tmp (fs/mkdtempSync (path/join (os/tmpdir) "pr-flow-clock-"))
        clock (path/join tmp "clock.cjs")
        previous (aget js/process.env "NODE_OPTIONS")]
    (try
      (fs/writeFileSync clock (str "Date.now = () => " (js/Date.parse instant) ";\n"))
      (aset js/process.env "NODE_OPTIONS" (str (when previous (str previous " ")) "--require=" clock))
      (apply execute config args)
      (finally
        (if (nil? previous) (js-delete js/process.env "NODE_OPTIONS")
            (aset js/process.env "NODE_OPTIONS" previous))
        (fs/rmSync tmp #js {:recursive true :force true})))))

(deftest actual-request-caller-observes-native-next-review-reset-and-zero-post-guards
  (let [{:keys [inquiry wait]} native-coderabbit-next-review-info
        {:keys [unknown available]} native-coderabbit-quota-info
        before "2026-10-05T12:31:00Z" after "2026-10-05T12:54:40Z"
        request {:id 9003 :user (:user inquiry) :created_at "2026-10-05T12:54:40Z"
                 :body (str "@coderabbitai full review <!-- pr-flow-stage:code --> <!-- pr-flow-review:"
                            head " --> <!-- pr-flow-reviewer:coderabbit --> <!-- pr-flow-round:6 -->")}
        later-unknown (assoc unknown :updated_at "2026-10-05T12:54:40Z")
        malformed (assoc wait :updated_at "2026-10-05T12:54:40Z"
                         :body (str/replace (:body wait) "30 minutes." "unknown minutes"))]
    (doseq [comments [[inquiry wait] [unknown wait] [available wait]]]
      (let [r (execute-at before (assoc base :comments comments)
                          "request" "riatzukiza/.agents" "17" "code")]
        (is (= 0 (:exit r)) (:err r))
        (is (str/includes? (:out r) "No request sent: cooldown; retry after 2026-10-05T12:54:40.000Z"))
        (is (empty? (writes r "comment")))
        (is (empty? (mutations r)))
        (is (empty? (writes r "merge")))))
    (doseq [[comments expected] [[[wait later-unknown] "rate-limited"]
                                 [[available malformed] "rate-limited"]
                                 [[wait request] "pending"]]]
      (let [r (execute-at after (assoc base :comments comments)
                          "request" "riatzukiza/.agents" "17" "code")]
        (is (str/includes? (:out r) (str "No request sent: " expected)))
        (is (= (if (= expected "rate-limited") 1 0) (:exit r)))
        (when (= expected "rate-limited")
          (is (str/includes? (:err r) "operator attention; no retry scheduled")))
        (is (empty? (writes r "comment")))
        (is (empty? (mutations r)))
        (is (empty? (writes r "merge")))))
    (let [pending (execute-at after (assoc base :comments [inquiry wait]
                                          :checks [{:name "CodeRabbit" :state "PENDING"}])
                              "request" "riatzukiza/.agents" "17" "code")
          changed (execute-at after (assoc base :comments [inquiry wait] :heads [head head head other])
                              "request" "riatzukiza/.agents" "17" "code")]
      (is (str/includes? (:out pending) "No request sent: pending"))
      (is (empty? (writes pending "comment")))
      (is (= 1 (:exit changed)))
      (is (str/includes? (:err changed) "PR head changed before review request"))
      (is (empty? (writes changed "comment"))))
    (let [allowed (execute-at after (assoc base :comments [inquiry wait] :authorized true)
                              "request" "riatzukiza/.agents" "17" "code")
          posted (first (writes allowed "comment"))]
      (is (= 0 (:exit allowed)) (:err allowed))
      (is (= 1 (count (writes allowed "comment"))))
      (is (str/starts-with? (:input posted "") "@coderabbitai full review"))
      (is (str/includes? (:input posted "") (str "pr-flow-review:" head " -->")))
      (is (str/includes? (:input posted "") "pr-flow-reviewer:coderabbit -->"))
      (is (str/includes? (:input posted "") "pr-flow-stage:code -->"))
      (is (str/includes? (:input posted "") "pr-flow-round:6 -->"))
      (is (empty? (mutations allowed)))
      (is (empty? (writes allowed "merge"))))
    ;; Public exact-head markers cannot create a trusted pending request. The
    ;; actual caller still consults the native writer lookup and current head.
    (let [public (execute-at after (assoc base :comments [inquiry wait request] :authorized false)
                             "request" "riatzukiza/.agents" "17" "code")]
      (is (= 0 (:exit public)) (:err public))
      (is (= 1 (count (writes public "comment"))))
      (is (some #(str/includes? (second (:args %)) "/collaborators/riatzukiza/permission")
                (:calls public))))
    (let [codex-quota (assoc unknown :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
                                    :created_at "2026-10-05T12:24:40Z" :updated_at "2026-10-05T12:24:40Z"
                                    :body "You have reached your Codex usage limits for code reviews.")
          r (execute-at after (assoc base :comments [codex-quota wait])
                        "request" "riatzukiza/.agents" "17" "code" "--reviewer" "codex")]
      (is (= 1 (:exit r)))
      (is (str/includes? (:out r) "No request sent: rate-limited"))
      (is (empty? (writes r "comment"))))))

(run-tests)
