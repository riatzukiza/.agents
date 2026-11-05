(ns pr-flow.kimi-native
  "Read-only native facts for the one reviewed Proxx publication contract.
   Source bytes are hashed, never executed. No auth exchange or review writes."
  (:require ["child_process" :as cp] ["fs" :as fs] ["os" :as os] ["path" :as path]
            ["crypto" :as crypto] [clojure.string :as str]
            [pr-flow.kimi-publication :as publication]))

(defn- digest [algorithm bytes] (.digest (.update (crypto/createHash algorithm) bytes) "hex"))
(defn- decode [bytes] (.decode (js/TextDecoder. "utf-8" #js {:fatal true}) bytes))
(defn- parse [bytes] (js/JSON.parse (decode bytes)))
(defn- data [value] (js->clj value :keywordize-keys true))
(defn- fail [] (throw (ex-info "Kimi publication proof unavailable or mismatched" {})))
(defn- source-digest [get-json endpoint expected-path]
  (let [source (get-json endpoint)
        encoded (str/replace (str (:content source)) #"\s" "")
        bytes (js/Buffer.from encoded "base64")]
    (when-not (and (= "file" (:type source)) (= expected-path (:path source))
                   (= "base64" (:encoding source)) (<= (.-length bytes) (* 1024 1024))
                   (= encoded (.toString bytes "base64"))
                   (= (:sha source) (digest "sha1" (js/Buffer.concat
                                                   #js [(js/Buffer.from (str "blob " (.-length bytes) "\u0000")) bytes]))))
      (fail))
    (digest "sha256" bytes)))

(defn- archive [bytes]
  ;; Use the existing platform ZIP tool; do not implement archive semantics.
  ;; Digest and bounds precede extraction. Nothing from the archive executes.
  (when (or (zero? (.-length bytes)) (> (.-length bytes) (* 2 1024 1024))) (fail))
  (let [tmp (fs/mkdtempSync (path/join (os/tmpdir) "pr-flow-kimi-proof-"))
        file (path/join tmp "submission.zip")
        unzip (fn [args]
                (let [r (cp/spawnSync "unzip" (clj->js args)
                                      #js {:timeout 5000 :maxBuffer (* 2 1024 1024)})]
                  (when-not (= 0 (.-status r)) (fail))
                  (.-stdout r)))]
    (try
      (fs/writeFileSync file bytes)
      (when-not (= ["kimi-provenance.json" "kimi-review.json"]
                   (sort (str/split-lines (decode (unzip ["-Z1" file]))))) (fail))
      {:review (unzip ["-p" file "kimi-review.json"])
       :provenance (unzip ["-p" file "kimi-provenance.json"])}
      (finally (fs/rmSync tmp #js {:recursive true :force true})))))

(defn- expected-body [base review-json provenance-json native-comments]
  (let [review (data review-json) p (data provenance-json)
        marker (digest "sha256" (js/JSON.stringify #js {:base base :review review-json}))
        body-json #js {}
        _ (doseq [k publication/body-fields]
            (aset body-json (name k) (if (= k :coveredFileCount) (count (:coveredFiles p))
                                        (aget provenance-json (name k)))))
        attached? (fn [c] (some #(and (= (select-keys c [:path :body]) (select-keys % [:path :body]))
                                      (= (:line c) (publication/original-right-line %))) native-comments))
        fallback (str/join "" (for [c (:comments review) :when (not (attached? c))]
                                (str "\n\nUnattached finding at " (:path c) ":" (:line c)
                                     " (not an added diff line):\n" (:body c))))]
    (str "Kimi review of exact head " (:head review) "\nBase " base
         "\n<!-- kimi-submission:" marker " -->\n\n" (:summary review) fallback
         "\n\nOpenCode control provenance (runtime observations, not provider attestation):"
         "\nRequested OpenCode variant=low."
         "\nAdvertised native low mapping (@ai-sdk/openai-compatible): reasoningEffort=low."
         "\nPinned OpenCode version=1.18.34."
         "\nObserved assistant variant=low on kimi-code-plan-global/kimi-for-coding."
         "\nUnderlying provider model=UNKNOWN; actual reasoning budget=UNKNOWN."
         "\n\nNative execution provenance (runtime observations, not provider attestation or reviewer quorum):"
         "\n```json\n" (js/JSON.stringify body-json nil 2) "\n```")))

(defn hydrate
  "Return native reviews with independently acquired proof, or a safe failure
   enum. Callback endpoints are fixed to the configured repo and numeric IDs."
  [repository pr-number head reviews profile {:keys [get-json get-pages get-archive]}]
  (mapv
   (fn [r]
     (let [r (dissoc r :kimi-publication :kimi-publication-error)]
       (if-not (and (publication/applicable? repository profile) (publication/principal? (:user r))
                    (= head (:commit_id r))
                    (str/starts-with? (str (:body r)) (str "Kimi review of exact head " head "\n"))) r
         (try
           (let [repo (str "repos/" repository)
                 pr (get-json (str repo "/pulls/" pr-number))
                 _ (when-not (and (= head (get-in pr [:head :sha])) (= pr-number (:number pr))
                                  (= repository (get-in pr [:head :repo :full_name]))
                                  (= "open" (:state pr)) (false? (:draft pr))) (fail))
                 matches (re-seq #"(?s)\n\nNative execution provenance \(runtime observations, not provider attestation or reviewer quorum\):\n```json\n(.*?)\n```" (:body r))
                 _ (when-not (= 1 (count matches)) (fail))
                 hint (data (js/JSON.parse (second (first matches))))
                 run-id (:runID hint)
                 _ (when-not (and (string? run-id) (re-matches #"[1-9][0-9]{0,15}" run-id)
                                  (js/Number.isSafeInteger (js/Number run-id))) (fail))
                 run (get-json (str repo "/actions/runs/" run-id))
                 attempt (:run_attempt run) producer-attempt (:runAttempt hint)
                 _ (when-not (and (integer? attempt) (pos? attempt) (<= attempt 100)
                                  (integer? producer-attempt) (<= 1 producer-attempt attempt)) (fail))
                 jobs (get-pages (str repo "/actions/runs/" run-id "/attempts/" attempt "/jobs?per_page=100") :jobs)
                 producer-jobs (if (= attempt producer-attempt) jobs
                                (get-pages (str repo "/actions/runs/" run-id "/attempts/" producer-attempt "/jobs?per_page=100") :jobs))
                 one (fn [pred items] (let [items (filter pred items)] (when-not (= 1 (count items)) (fail)) (first items)))
                 publisher (one #(= "Review pull request with OpenCode" (:name %)) jobs)
                 producer (one #(= "Produce exact-head Kimi review" (:name %)) producer-jobs)
                 name (str "kimi-native-" run-id "-" pr-number "-" head "-" producer-attempt)
                 artifact (one #(= name (:name %)) (get-pages (str repo "/actions/runs/" run-id "/artifacts?per_page=100") :artifacts))
                 _ (when-not (and (integer? (:id artifact)) (pos? (:id artifact))
                                  (false? (:expired artifact)) (<= 1 (:size_in_bytes artifact) (* 2 1024 1024))) (fail))
                 bytes (get-archive (str repo "/actions/artifacts/" (:id artifact) "/zip"))
                 archive-sha (digest "sha256" bytes)
                 _ (when-not (and (= (.-length bytes) (:size_in_bytes artifact))
                                  (= (str "sha256:" archive-sha) (:digest artifact))) (fail))
                 extracted (archive bytes) submission (parse (:review extracted)) provenance (parse (:provenance extracted))
                 p (data provenance) workflow-sha (:workflowSha p)
                 _ (when-not (and (string? workflow-sha) (re-matches #"[0-9a-f]{40}" workflow-sha)) (fail))
                 runtime (:runtime-sha profile)
                 source (fn [sha file] (source-digest get-json (str repo "/contents/" file "?ref=" sha) file))
                 readback (get-json (str repo "/pulls/" pr-number "/reviews/" (:id r)))
                 comments (get-pages (str repo "/pulls/" pr-number "/reviews/" (:id r) "/comments") nil)
                 _ (when-not (every? #(and (publication/principal? (:user %))
                                          (= (:id r) (:pull_request_review_id %)) (= head (:original_commit_id %))
                                          (publication/original-right-line %)) comments) (fail))
                 proof {:profile profile :context {:repository repository :pr-number pr-number :head head :base (get-in pr [:base :sha])}
                        :run run :producer-job producer :publisher-job publisher :artifact artifact
                        :provenance p :submission (data submission)
                        :workflow-commit (get-json (str repo "/commits/" workflow-sha))
                        :runtime-comparison (get-json (str repo "/compare/" runtime "..." (get-in pr [:base :sha])))
                        :source-digests {:workflow (source workflow-sha (:workflow-path profile))
                                         :runtime (source runtime ".github/scripts/kimi-review.cjs")
                                         :auth (source runtime ".github/scripts/opencode-app-auth.cjs")}
                        :archive-sha256 archive-sha :review-sha256 (digest "sha256" (:review extracted))
                        :submission-marker-sha256 (digest "sha256" (js/JSON.stringify
                                                                    #js {:base (get-in pr [:base :sha]) :review submission}))
                        :inline-comments comments :readback readback
                        :expected-body (expected-body (get-in pr [:base :sha]) submission provenance comments)}
                 candidate (assoc r :kimi-publication proof)]
             (if (publication/admitted? candidate) candidate
                 (assoc r :kimi-publication-error :mismatch)))
           ;; Never emit native/private error bodies, tokens or raw artifacts.
           (catch :default _ (assoc r :kimi-publication-error :proof-unavailable)))))) reviews))
