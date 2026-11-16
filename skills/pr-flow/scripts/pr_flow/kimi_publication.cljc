(ns pr-flow.kimi-publication
  "Admission for the observed Proxx publisher, not a model or review engine.
   Native/source/byte facts are collected by the CLI; no model tag is authority."
  (:require [clojure.string :as str]))

(def principal {:login "opencode-agent[bot]" :id 219766164
                :node-id "BOT_kgDODRldlA" :type "Bot"})
(defn principal? [u]
  (= principal {:login (:login u) :id (:id u) :node-id (:node_id u) :type (:type u)}))
(defn- sha? [n s] (and (string? s) (boolean (re-matches (re-pattern (str "[0-9a-f]{" n "}")) s))))
(defn- positive? [n] (and (integer? n) (pos? n) (<= n 9007199254740991)))

(defn applicable? [repository profile]
  (and (= "open-hax/proxx" repository (:repository profile))
       (= 1 (:version profile)) (= principal (:principal profile))
       (= 285819940 (:workflow-id profile))
       (= ".github/workflows/opencode-code-review.yml" (:workflow-path profile))
       (= "OpenCode Kimi PR Review" (:workflow-name profile))
       (= "2810f4515424a146fe37390fb0baf532cca31236" (:runtime-sha profile))
       (= "0fa9d7838df3f0718d971beb972a48d2bf73fce6d90f09411a656e57ce3960d7" (:runtime-digest profile))
       (= "fd4d5630c462f0f202ac20e39ec1433fba4dfa13e12a6f6ffd5c0ec035d2a7e1" (:auth-digest profile))
       (seq (:workflow-digests profile)) (every? #(sha? 64 %) (:workflow-digests profile))))

(def model {:providerID "kimi-code-plan-global" :modelID "kimi-for-coding"})
(def control {:requested {:variant "low"}
              :advertisedNativeControl {:apiNpm "@ai-sdk/openai-compatible" :reasoningEffort "low"}
              :opencodeVersion "1.18.34" :observedAssistantVariant "low" :executedIdentity model
              :underlyingProviderModel nil
              :binding "Pinned OpenCode catalog low mapping and assistant variant; not a provider reasoning-budget attestation"})
(def body-fields
  [:origin :repository :head :base :runtimeSha :runtimeBlobSha256 :authBlobSha256 :reviewBlobSha256
   :runtimeBaseAncestorVerified :requestedModel :executedModel :runID :runAttempt :artifactName :prNumber
   :workflowSha :opencodeVersion :archiveSha256 :diffSha256 :coveredFileCount :executionControl])
(defn original-right-line
  "Native legacy position reviews omit line fields. Their single diff hunk
   ends at the original added-line location; ambiguous hunks stay unbound."
  [comment]
  (or (:original_line comment)
      (when (positive? (:original_position comment))
        (let [[header & lines] (str/split-lines (str (:diff_hunk comment)))
              start (second (re-matches #"@@ -[0-9]+(?:,[0-9]+)? \+([0-9]+)(?:,[0-9]+)? @@.*" (or header "")))]
          (when (and start (seq lines) (every? #(re-find #"^[ +\-]" %) lines)
                     (str/starts-with? (last lines) "+"))
            (+ #?(:clj (Long/parseLong start) :cljs (js/parseInt start 10))
               (count (remove #(str/starts-with? % "-") lines)) -1))))))

(defn- job? [j run attempt head name steps]
  (and (positive? (:id j)) (= (:id run) (:run_id j)) (= head (:head_sha j))
       (= attempt (:run_attempt j)) (= name (:name j))
       (= "completed" (:status j)) (= "success" (:conclusion j))
       (every? (fn [required]
                 (= 1 (count (filter #(and (= required (:name %))
                                          (= "completed" (:status %))
                                          (= "success" (:conclusion %))) (:steps j))))) steps)))

(defn- body-bound? [r proof]
  (let [prefix (:expected-body proof) body (:body r) submission (:submission proof)
        marker (:submission-marker-sha256 proof)]
    (and (string? prefix) (not (str/blank? prefix)) (string? body)
         (sha? 64 marker)
         (str/starts-with? prefix (str "Kimi review of exact head " (:head submission)
                                      "\nBase " (get-in proof [:context :base])
                                      "\n<!-- kimi-submission:" marker " -->\n\n" (:summary submission)))
         (str/starts-with? body prefix)
         (let [suffix (subs body (count prefix))
               lines (remove str/blank? (str/split-lines suffix))
               receipts (keep #(second (re-matches #"<!-- kimi-discord-delivered:v1:([1-9][0-9]*):[0-9a-f]{64} -->" %)) lines)
               ids (set (map #(str (:id %)) (:inline-comments proof)))]
           (and (<= (count lines) 100) (= (count lines) (count receipts) (count (set receipts)))
                (every? ids receipts)
                ;; A newline separates helper-owned receipts from the original body.
                (or (= "" suffix) (str/starts-with? suffix "\n")))))))

(defn admitted?
  "One physical App, one scoped publication role. Diagnostics and summaries
   without independently collected native/source/artifact/readback facts fail."
  [{:keys [user id state commit_id submitted_at kimi-publication] :as r}]
  (let [{:keys [profile context run producer-job publisher-job artifact provenance submission
                workflow-commit runtime-comparison source-digests archive-sha256 review-sha256
                readback]} kimi-publication
        {:keys [repository pr-number head base]} context
        p provenance run-id (:id run) producer-attempt (:runAttempt p)
        consumer-attempt (:run_attempt run)
        files (:coveredFiles submission)]
    (boolean
     (and (applicable? repository profile) (principal? user) (positive? id)
          (= "COMMENTED" state) (= head commit_id) (sha? 40 head) (sha? 40 base) (positive? pr-number)
          (= (str "https://api.github.com/repos/" repository "/pulls/" pr-number) (:pull_request_url r))
          (= (select-keys r [:id :node_id :state :commit_id :submitted_at :body])
             (select-keys readback [:id :node_id :state :commit_id :submitted_at :body]))
          (principal? (:user readback))
          (= "pull_request" (:event run)) (= (:workflow-id profile) (:workflow_id run))
          (= (:workflow-path profile) (:path run)) (= head (:head_sha run))
          (= repository (get-in run [:repository :full_name]) (get-in run [:head_repository :full_name]))
          (= 1178288746 (get-in run [:repository :id]) (get-in run [:head_repository :id]))
          (positive? run-id) (positive? producer-attempt) (positive? consumer-attempt)
          (<= producer-attempt consumer-attempt)
          (= 1 (count (filter #(= pr-number (:number %)) (:pull_requests run))))
          (job? producer-job run producer-attempt head "Produce exact-head Kimi review"
                ["Run exact-head Kimi review without publication credentials" "Record native execution provenance"
                 "Preserve native submission and its execution provenance"])
          (job? publisher-job run consumer-attempt head "Review pull request with OpenCode"
                ["Prepare immutable publication runtime" "Download only the retained successful producer artifact"
                 "Publish exact-head review and its own bounded Discord notifications"])
          (string? submitted_at) (string? (:completed_at producer-job)) (string? (:completed_at publisher-job))
          (not (neg? (compare submitted_at (:completed_at producer-job))))
          (not (pos? (compare submitted_at (:completed_at publisher-job))))
          (= "github-actions-native-execution" (:origin p))
          (= repository (:repository p)) (= pr-number (:prNumber p)) (= head (:head p) (:head submission))
          (= base (:base p)) (= (str run-id) (:runID p))
          (= (str "https://github.com/" repository "/actions/runs/" run-id) (:runURL p))
          (= (str repository "/" (:workflow-path profile) "@refs/pull/" pr-number "/merge") (:workflowRef p))
          (sha? 40 (:workflowSha p)) (= (:workflowSha p) (:sha workflow-commit))
          (= [base head] (mapv :sha (:parents workflow-commit)))
          (contains? (set (:workflow-digests profile)) (:workflow source-digests))
          (= (:runtime-sha profile) (:runtimeSha p) (get-in runtime-comparison [:merge_base_commit :sha]))
          (#{"ahead" "identical"} (:status runtime-comparison))
          (= true (:runtimeBaseAncestorVerified p))
          (= (:runtime-digest profile) (:runtimeBlobSha256 p) (:runtime source-digests))
          (= (:auth-digest profile) (:authBlobSha256 p) (:auth source-digests))
          (= "1.18.34" (:opencodeVersion p))
          (= "0f22479647226d1d2dd99595d20082ee7bda3870b62dc6a90b41efc1a71d7e9a" (:archiveSha256 p))
          (= model (:requestedModel p) (:executedModel p))
          (= "Successful immutable parseStructured requires assistant providerID/modelID to equal requested Kimi identities" (:executedModelBinding p))
          (= control (:executionControl p) (:executionControl submission))
          (positive? (:id artifact)) (false? (:expired artifact))
          (= (:artifactName p) (:name artifact)
             (str "kimi-native-" run-id "-" pr-number "-" head "-" producer-attempt))
          (= run-id (get-in artifact [:workflow_run :id])) (= head (get-in artifact [:workflow_run :head_sha]))
          (sha? 64 archive-sha256) (= (str "sha256:" archive-sha256) (:digest artifact))
          (sha? 64 review-sha256) (= review-sha256 (:reviewBlobSha256 p))
          (sha? 64 (:diffSha256 submission)) (= (:diffSha256 submission) (:diffSha256 p))
          (vector? files) (seq files) (= (count files) (count (set files)))
          (every? #(and (string? %) (not (str/blank? %))) files) (= files (:coveredFiles p))
          (= #{:head :diffSha256 :coveredFiles :summary :comments :executionControl} (set (keys submission)))
          (string? (:summary submission)) (not (str/blank? (:summary submission)))
          (vector? (:comments submission)) (<= (count (:comments submission)) 100)
          (every? #(and (= #{:path :line :body} (set (keys %)))
                        (contains? (set files) (:path %)) (positive? (:line %))
                        (string? (:body %)) (not (str/blank? (:body %)))) (:comments submission))
          (every? (fn [c]
                    (and (principal? (:user c)) (= id (:pull_request_review_id c))
                         (= head (:original_commit_id c))
                         (some #(and (= (select-keys c [:path :body]) (select-keys % [:path :body]))
                                     (= (original-right-line c) (:line %))) (:comments submission))))
                  (:inline-comments kimi-publication))
          (body-bound? r kimi-publication)))))

(defn finding-free? [r] (and (admitted? r) (empty? (get-in r [:kimi-publication :submission :comments]))))
