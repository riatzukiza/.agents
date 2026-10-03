(ns test-issue-agreement
  (:require ["fs" :as fs] ["path" :as path]
            [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [nbb.core :refer [*file*]]
            [pr-flow.law :as law]))

(def fixture
  (js->clj (js/JSON.parse (fs/readFileSync
                          (path/join (path/dirname *file*) "fixtures/native-opencode-rejection.json") "utf8"))
           :keywordize-keys true))
(def identities (assoc law/default-reviewer-identities "opencode" #{"opencode-agent[bot]"}))
(defn native-thread-comment [c]
  {:id (:databaseId c) :author (get-in c [:author :login])
   :user {:login (get-in c [:author :login]) :type (get-in c [:author :__typename])}
   :authorized? true :body (:body c) :url (:url c) :created-at (:createdAt c) :updated-at (:updatedAt c)})
(def proposal (native-thread-comment (:proposal fixture)))
(def final-body
  (str "Rejected:\nReason: " (second (re-find #"(?m)^Reason: (.+)$" (:body proposal)))
       "\nEvidence: " (second (re-find #"(?m)^Evidence: (.+)$" (:body proposal)))))
(def settlement {:author (:pr_author fixture) :user {:login (:pr_author fixture) :type "User"}
                 :authorized? true :created-at "2026-10-03T11:38:00Z" :body final-body})
(def agreement (assoc (:agreement fixture) :source-channel :github-issue-comment))
(def input {:id (:thread_id fixture) :root-comment-id (get-in fixture [:root :databaseId])
            :head (:head fixture) :pr-author (:pr_author fixture) :identities identities :resolved? true
            :issue-comments [agreement]
            :comments [(native-thread-comment (:root fixture)) proposal settlement]})
(defn approved? [thread] (:rejection-approved? (law/classify-thread thread)))

(deftest actual-native-issue-assessment-is-distinct-scoped-evidence
  (let [result (law/classify-thread input)]
    (is (:settled? result))
    (is (:rejection-approved? result))
    (is (= "opencode" (:rejection-reviewer result)))
    (is (= :github-issue-comment (:rejection-channel result)))
    (is (= 5968785159 (:rejection-source-id result)))
    (is (= (:html_url agreement) (:rejection-url result))))
  (is (not (approved? (dissoc input :issue-comments))))
  ;; Completed publication is an actual assessment; run lifecycle is separate.
  (is (approved? (assoc-in input [:issue-comments 0 :workflow-state] :cancelled)))
  (is (empty? (:approved-heads (law/review-evidence (:head fixture) [] [agreement] identities))))
  (is (empty? (law/completed-review-rounds [] [agreement] identities))))

(deftest issue-agreement-requires-one-live-head-and-native-finding-binding
  (doseq [body [(str/replace (:body agreement) #"(?m)^Finding:[^\n]*\n" "")
                (str/replace (:body agreement) (:thread_id fixture) "PRRT_another")
                (str/replace (:body agreement) "comment4172695094" "comment4172695095")
                (str (:body agreement) "\nFinding: PRRT_kwDORjtCas6omXMd comment4172695094")
                (str/replace (:body agreement) "workflow line 95." "PRRT_another comment42")
                (str/replace (:body agreement) (:head fixture) (apply str (repeat 40 "b")))
                (str (:body agreement) "\nRejection agreement for " (:head fixture) ":")]]
    (is (not (approved? (assoc-in input [:issue-comments 0 :body] body)))))
  (doseq [thread [(dissoc input :root-comment-id)
                  (assoc input :root-comment-id 4172695095)
                  (assoc input :id "PRRT_another")
                  (assoc input :head (apply str (repeat 40 "b")))
                  (assoc input :head "57f1f452f6466b9f5cca56c1059e37e25c0559f8")
                  (update-in input [:comments 1 :body] str/replace #"(?m)^Finding:[^\n]*\n" "")]]
    (is (not (approved? thread)))))

(deftest quoted-generated-and-ambiguous-details-cannot-grant-agreement
  (doseq [body [(str "> " (str/replace (:body agreement) "\n" "\n> "))
                (str "```text\n" (:body agreement) "\n```")
                (str "<details><summary>Generated example</summary>\n" (:body agreement) "\n</details>")
                (str "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\n"
                     (:body agreement) "\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->")
                (str "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\n" (:body agreement))
                (str "Rejection agreement for " (:head fixture) ":\nFinding: " (:thread_id fixture)
                     " comment4172695094\n<details><details>example</details>\nReason: Generated only.\nEvidence: src/example.cljc\n</details>")
                (str "Rejection agreement for " (:head fixture) ":\nFinding: " (:thread_id fixture)
                     " comment4172695094\n<details>\nReason: Generated only.\nEvidence: src/example.cljc")
                (str "Rejection agreement for " (:head fixture) ":\nFinding: " (:thread_id fixture)
                     " comment4172695094\n<details><summary>Generated</summary>\nReason: Example only.\nEvidence: src/example.cljc\n</details>")
                (str/replace (:body agreement) "Reason:\nMy independent" "My independent")
                (str/replace (:body agreement) "Evidence:\n" "")
                (str (:body agreement) "\nReason: Duplicate ambiguous reason.")
                (str (:body agreement) "\nEvidence: src/other.cljc")
                (str "Rejection agreement for " (:head fixture) ":\nFinding: " (:thread_id fixture)
                     " comment4172695094\nReason: I agree.\nEvidence: src/example.cljc")
                (str "Rejection agreement for " (:head fixture) ":\nFinding: " (:thread_id fixture)
                     " comment4172695094\nReason: I agree with this rejection.\nEvidence: src/example.cljc")
                (str "Rejection agreement for " (:head fixture) ":\nFinding: " (:thread_id fixture)
                     " comment4172695094\nReason:\n\nEvidence: src/example.cljc")
                "I agree with the author. ✅"]]
    (is (not (approved? (assoc-in input [:issue-comments 0 :body] body))))))

(deftest native-source-identity-and-writer-independence-remain-required
  (doseq [thread [(assoc-in input [:issue-comments 0 :user :login] "fake-opencode[bot]")
                  (assoc-in input [:issue-comments 0 :user :type] "User")
                  (assoc-in input [:issue-comments 0 :user :login] "coderabbitai[bot]")
                  (assoc-in input [:issue-comments 0 :author] "another-author")
                  (update-in input [:issue-comments 0] dissoc :source-channel)
                  (update-in input [:issue-comments 0] dissoc :id)
                  (assoc input :pr-author "opencode-agent[bot]")
                  (assoc-in input [:comments 0 :author] "opencode-agent[bot]")
                  (-> input (assoc-in [:comments 0 :author] "opencode-agent[bot]")
                      (update :issue-comments conj
                              (assoc agreement :id 5968785000 :user {:login "another-writer" :type "User"}
                                     :created_at "2026-10-03T10:00:00Z" :updated_at "2026-10-03T10:00:00Z")))
                  (assoc-in input [:comments 0 :author] (:pr_author fixture))
                  (assoc-in input [:comments 1 :authorized?] false)
                  (assoc-in input [:comments 2 :authorized?] false)
                  (assoc-in input [:comments 2 :author] "another-writer")
                  (assoc-in input [:issue-comments 0 :html_url] "")]]
    (is (not (approved? thread)))))

(deftest agreement-cannot-be-reused-outside-its-proposal-and-final-window
  (doseq [[created edited] [[nil nil] ["2026-10-03T11:33:00Z" "2026-10-03T11:33:00Z"]
                            ["2026-10-03T11:34:10Z" "2026-10-03T11:34:10Z"]
                            ["2026-10-03T11:38:00Z" "2026-10-03T11:38:00Z"]
                            ["2026-10-03T11:39:00Z" "2026-10-03T11:39:00Z"]
                            [(:created_at agreement) "2026-10-03T11:39:00Z"]]]
    (is (not (approved? (-> input (assoc-in [:issue-comments 0 :created_at] created)
                           (assoc-in [:issue-comments 0 :updated_at] edited))))))
  (is (not (approved? (assoc-in input [:issue-comments 0 :created_at] "2026-10-03T11:33:00Z"))))
  (is (not (approved? (assoc-in input [:comments 1 :updated-at] "2026-10-03T11:37:30Z"))))
  (is (not (approved? (update-in input [:comments 2] dissoc :created-at))))
  (is (not (approved? (update input :comments
                             #(vec [(first %) (second %) (assoc proposal :created-at "2026-10-03T11:37:30Z") (last %)])))))
  (is (not (approved? (assoc input :comments [(first (:comments input)) settlement proposal])))))

(deftest real-later-pushback-still-blocks-the-supported-rejection
  (let [negative {:author "github-actions" :body "This still reproduces; the rejection is not correct."
                  :created-at "2026-10-03T11:37:30Z"}
        withdrawal {:user (:user agreement) :source-channel :github-issue-comment
                    :id 5968785160 :html_url "https://example.invalid/withdrawal"
                    :created_at "2026-10-03T11:39:00Z" :updated_at "2026-10-03T11:39:00Z"
                    :body (str "Finding: " (:thread_id fixture) " comment4172695094\nI withdraw my agreement; this still reproduces.")}]
    (is (not (approved? (update input :comments #(vec [(first %) (second %) negative (last %)])))))
    (is (not (approved? (update input :comments conj (assoc negative :created-at "2026-10-03T11:39:00Z")))))
    (is (not (approved? (update input :issue-comments conj withdrawal))))
    (is (not (approved? (update input :issue-comments conj
                               (assoc withdrawal :body (str/replace (:body agreement)
                                                                    "My independent verification confirms"
                                                                    "I withdraw my agreement. My independent verification confirms"))))))
    (is (approved? (update input :comments conj
                           (assoc negative :body "Verified the fix." :created-at "2026-10-03T11:39:00Z"))))
    (is (approved? (update input :issue-comments conj
                           (update withdrawal :body str/replace (:thread_id fixture) "PRRT_another"))))))

(deftest authenticated-source-bound-withdrawal-revokes-off-thread-agreement
  (let [withdrawal (assoc agreement :id 5968785160 :created_at "2026-10-03T11:39:00Z" :updated_at "2026-10-03T11:39:00Z")
        direct "I withdraw my rejection agreement in comment5968785159; it was incorrect."
        linked (str "I withdraw my rejection agreement at " (:html_url agreement) "; it was incorrect.")]
    (doseq [body [direct linked (str "Verified. " direct)
                 "I withdraw my agreement in issuecomment-5968785159; this still reproduces."
                 (str "Finding: " (:id input) "\nI withdraw my agreement; this still reproduces.")]]
      (is (not (approved? (update input :issue-comments conj (assoc withdrawal :body body))))))
    (doseq [c [(assoc withdrawal :body (str/replace direct "5968785159" "5968785000"))
               (assoc withdrawal :body (str/replace linked "open-hax/proxx/pull/445" "unrelated/repo/pull/7"))
               (assoc withdrawal :body (str "> " direct))
               (assoc withdrawal :body (str "```text\n" direct "\n```"))
               (assoc withdrawal :body (str "<details><summary>Example</summary>" direct "</details>"))
               (-> withdrawal (assoc :body direct) (assoc-in [:user :type] "User"))
               (-> withdrawal (assoc :body direct) (assoc-in [:user :login] "fake-opencode[bot]"))
               (assoc withdrawal :body "I withdraw my unrelated agreement.")
               (assoc withdrawal :body (str "The code references " (:html_url agreement) ". Verified."))
               (assoc withdrawal :body "I do not withdraw my agreement in comment5968785159. Verified.")]]
      (is (approved? (update input :issue-comments conj c))))
    (let [newer (assoc agreement :id 5968785158 :html_url "https://github.com/open-hax/proxx/pull/445#issuecomment-5968785158"
                       :created_at "2026-10-03T11:37:30Z" :updated_at "2026-10-03T11:37:30Z")
          renewed (update input :issue-comments conj newer)]
      (is (approved? (update renewed :issue-comments conj (assoc withdrawal :body direct))))
      (is (not (approved? (update renewed :issue-comments conj
                                 (assoc withdrawal :body (str/replace direct "5968785159" "5968785158")))))))))
