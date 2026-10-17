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

(deftest root-edits-do-not-reorder-native-rejection-evidence
  ;; CodeRabbit review5402437665/comment4174571185: a later root edit
  ;; changes metadata, not the native order or identity of the thread opener.
  (doseq [edited ["2026-10-03T11:37:30Z" "2026-10-03T11:39:00Z"]]
    (let [result (law/classify-thread (assoc-in input [:comments 0 :updated-at] edited))]
      (is (:settled? result))
      (is (:rejection-approved? result))
      (is (= (:author (first (:comments input))) (:reviewer result)))
      (is (= (:id agreement) (:rejection-source-id result)))
      (is (= :github-issue-comment (:rejection-channel result))))))

(deftest creation-order-preserves-proposal-and-agreement-edit-guards
  (let [root-edited (assoc-in input [:comments 0 :updated-at] "2026-10-03T11:40:00Z")]
    (is (approved? (assoc-in root-edited [:comments 1 :updated-at] "2026-10-03T11:36:00Z")))
    (is (approved? (assoc-in root-edited [:issue-comments 0 :updated_at] "2026-10-03T11:37:30Z")))
    (doseq [thread [(assoc-in root-edited [:comments 1 :updated-at] "2026-10-03T11:37:30Z")
                    (assoc-in root-edited [:issue-comments 0 :updated_at] "2026-10-03T11:39:00Z")
                    (assoc-in root-edited [:comments 1 :updated-at] "not-a-timestamp")
                    (assoc-in root-edited [:comments 2 :updated-at] "not-a-timestamp")]]
      (let [result (law/classify-thread thread)]
        (is (not (:rejection-approved? result)))
        (is (not (:settled? result)))))))

(deftest native-evidence-needs-valid-creation-times
  (doseq [location [[:comments 0 :created-at] [:comments 1 :created-at] [:comments 2 :created-at]
                    [:issue-comments 0 :created_at]]
          created [nil "" "not-a-timestamp" "2026-10-03T11:37:18"]]
    (is (not (approved? (assoc-in input location created)))))
  (doseq [[collection index field] [[:comments 0 :created-at] [:comments 1 :created-at]
                                    [:comments 2 :created-at] [:issue-comments 0 :created_at]]]
    (is (not (approved? (update-in input [collection index] dissoc field))))))

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

;; Explicit inert prospective controls. The node marker below is synthetic;
;; a unit principal or API eligibility result supplies no native Bot evidence.
(def prospective-user
  {:login "opencode-agent[bot]" :id 219766164 :node_id "inert-fixture-only" :type "Bot"})
(def prospective-thread
  (assoc input :resolved? false :issue-comments []
         :comments [(first (:comments input)) proposal]))
(def prospective-summary
  (str "Rejection agreement for " (:head fixture) ":\nFinding: " (:thread_id fixture)
       " comment" (get-in fixture [:root :databaseId])
       "\nReason: I traced the exact provenance boundary and the native run evidence; the asserted absence of these variables does not hold."
       "\nEvidence: .github/workflows/opencode-code-review.yml:95; https://example.invalid/inert-evidence"))
(defn request [thread]
  (law/rejection-assessment-request thread (:id proposal) prospective-user))
(defn publish [thread summary]
  (law/rejection-assessment-publication thread (:id proposal) summary prospective-user))

;; This is one real-shaped native item, never a fabricated PRRT conversation.
(def body-item-id "d9618d1e6c3cf0ee5e35cc46")
(def body-scope (str "review-id:101 cr-comment:v1:" body-item-id))
(def body-review
  {:id 101 :node_id "PRR_inert_body" :state "COMMENTED" :commit_id (:head fixture)
   :body (str "Retain the verified loader boundary. <!-- cr-comment:v1:" body-item-id " -->")
   :submitted_at "2026-10-03T11:30:00Z" :updated_at "2026-10-03T11:31:00Z"
   :user {:login "coderabbitai[bot]" :id 136622811 :node_id "BOT_inert_cr" :type "Bot"}})
(def body-native-review
  {:id (:node_id body-review) :databaseId 101 :state "COMMENTED" :body (:body body-review)
   :submittedAt (:submitted_at body-review) :updatedAt (:updated_at body-review)
   :commit {:oid (:head fixture)}
   :author {:login "coderabbitai[bot]" :databaseId 136622811 :id "BOT_inert_cr" :__typename "Bot"}})
(def body-proposal
  {:id 201 :node_id "IC_inert_body_proposal" :html_url "https://example.invalid/201"
   :source-channel :github-issue-comment :authorized? true
   :user {:login "riatzukiza" :id 10676925 :node_id "U_inert_writer" :type "User"}
   :created_at "2026-10-03T11:34:00Z" :updated_at "2026-10-03T11:34:10Z"
   :body (str "Rejection proposal for " (:head fixture) ": " body-scope
              "\nReason: Existing source already preserves the deliberate loader behavior."
              "\nEvidence: scripts/test_environment_promotion.py:20")})
(def body-input
  {:kind :review-body-item :review-id 101 :item-id body-item-id :head (:head fixture)
   :pr-author "riatzukiza" :pr-author-user (:user body-proposal)
   :identities identities :review (assoc body-review :native-review body-native-review)
   :issue-comments [body-proposal]})
(defn body-input-with-text [text]
  (-> body-input (assoc-in [:review :body] text) (assoc-in [:review :native-review :body] text)))
(def body-summary
  (str "Rejection agreement for " (:head fixture) ": " body-scope
       "\nReason: I independently traced the loader and its regression controls; the current source preserves the requested behavior."
       "\nEvidence: scripts/test_environment_promotion.py:20"))

(deftest prospective-body-item-uses-existing-request-and-publication-boundaries
  (let [r (law/rejection-assessment-request body-input 201 prospective-user)
        p (law/rejection-assessment-publication body-input 201 body-summary prospective-user)]
    (is (:eligible? r))
    (is (= [101 body-item-id] [(:review-id r) (:item-id r)]))
    (is (= (:body body-proposal) (:proposal-body r)))
    (is (= (:updated_at body-review) (:review-updated-at r)))
    (is (= "2026-10-03T11:34:10.000Z" (:created-after r)))
    (is (:eligible? p))
    (is (= body-summary (:body p)))
    (is (not-any? #(contains? p %) [:thread-id :root-comment-id :source-id :agreement-id :settled? :rejection-approved?]))))

(deftest prospective-body-item-refuses-source-and-scope-races
  (doseq [v [(dissoc body-input :review)
             (assoc body-input :thread-id "PRRT_inert")
             (assoc body-input :items [body-item-id])
             (assoc body-input :review-id 102)
             (assoc body-input :item-id "unknown")
             (body-input-with-text (str "> " (:body body-review)))
             (body-input-with-text (str "```html\n" (:body body-review) "\n```"))
             (body-input-with-text (str "<blockquote>" (:body body-review) "</blockquote>"))
             (body-input-with-text (str "<details><summary>Quoted example (1)</summary><blockquote>"
                                        (:body body-review) "</blockquote></details>"))
             (body-input-with-text (str "<details><summary>Quoted example: 1</summary><blockquote>"
                                        (:body body-review) "</blockquote></details>"))
             (body-input-with-text (str "<!-- This is an auto-generated comment: tweet message by coderabbit.ai -->\n"
                                        (:body body-review) "\n<!-- end of auto-generated comment: tweet message by coderabbit.ai -->"))
             (body-input-with-text (str (:body body-review) (:body body-review)))
             (assoc-in body-input [:review :native-review :body] "Edited native body")
             (assoc-in body-input [:review :native-review :author :databaseId] 1)
             (assoc-in body-input [:review :native-review :updatedAt] "2026-10-03T11:35:00Z")
             (update-in body-input [:review] dissoc :native-review)
             (assoc-in body-input [:review :user :type] "User")
             (assoc-in body-input [:review :user :login] "unknown[bot]")
             (assoc-in body-input [:review :commit_id] "unknown")
             (assoc-in body-input [:review :state] "PENDING")
             (assoc body-input :issue-comments nil)]]
    (is (not (:eligible? (law/rejection-assessment-request v 201 prospective-user))))))

(deftest prospective-body-item-physical-principals-are-distinct
  (doseq [u [(assoc prospective-user :id (get-in body-review [:user :id]))
             (assoc prospective-user :node_id (get-in body-review [:user :node_id]))
             (assoc prospective-user :id (get-in body-proposal [:user :id]))
             (assoc prospective-user :node_id (get-in body-proposal [:user :node_id]))]]
    (is (not (:eligible? (law/rejection-assessment-request body-input 201 u)))))
  (doseq [v [(dissoc body-input :pr-author-user)
             (assoc-in body-input [:pr-author-user :login] "another-user")]]
    (is (not (:eligible? (law/rejection-assessment-request v 201 prospective-user))))))

(deftest prospective-body-item-keeps-latest-native-user-and-independent-principal
  (doseq [v [(assoc-in body-input [:issue-comments 0 :authorized?] false)
             (assoc-in body-input [:issue-comments 0 :user :type] "Bot")
             (assoc-in body-input [:issue-comments 0 :user :login] "coderabbitai[bot]")
             (assoc-in body-input [:issue-comments 0 :updated_at] "invalid")
             (assoc-in body-input [:issue-comments 0 :created_at] "2026-10-03T11:30:00Z")
             (update body-input :issue-comments conj body-proposal)
             (update body-input :issue-comments conj
                     (assoc body-proposal :id 202 :created_at "2026-10-03T11:35:00Z"
                            :updated_at "2026-10-03T11:35:00Z" :body (str (:body body-proposal) "\nReason: ambiguous")))]]
    (is (not (:eligible? (law/rejection-assessment-request v 201 prospective-user)))))
  (doseq [u [(assoc prospective-user :login "coderabbitai[bot]")
             (assoc prospective-user :type "User") (assoc prospective-user :extra true)]]
    (is (not (:eligible? (law/rejection-assessment-request body-input 201 u)))))
  (doseq [body [(str body-summary "\n" body-summary)
               (str "> " body-summary)
               (str/replace body-summary body-item-id "8471f90c17574324d5bf3da1")
               (str/replace body-summary "Reason: I independently" "Reason: I disagree with the proposal. I independently")
               (str/replace body-summary #"(?m)^Evidence:.*$" "Evidence: none")]]
    (is (not (:eligible? (law/rejection-assessment-publication body-input 201 body prospective-user))))))

(deftest prospective-rejection-selects-actual-latest-writer-proposal-only
  (let [r (request prospective-thread)]
    (is (:eligible? r))
    (is (= (:id proposal) (:proposal-id r)))
    (is (= (:body proposal) (:proposal-body r)))
    (is (= (:updated-at proposal) (:proposal-updated-at r)))
    (is (= "2026-10-03T11:34:10.000Z" (:created-after r)))
    (is (= prospective-user (:expected-user r)))
    (is (not-any? #(contains? r %) [:source-id :agreement-id :created-at :url :settled? :rejection-approved?])))
  (doseq [t [(dissoc prospective-thread :issue-comments)
             (assoc prospective-thread :issue-comments nil)
             (assoc prospective-thread :head (apply str (repeat 40 "b")))
             (assoc prospective-thread :root-comment-id 4172695095)
             (assoc-in prospective-thread [:comments 1 :authorized?] false)
             (update-in prospective-thread [:comments 1] dissoc :authorized?)
             (assoc-in prospective-thread [:comments 1 :user :type] "Bot")
             (assoc-in prospective-thread [:comments 1 :user :login] "other-writer")
             (assoc-in prospective-thread [:comments 0 :author] (:author proposal))
             (update-in prospective-thread [:comments 1] dissoc :updated-at)
             (assoc-in prospective-thread [:comments 1 :updated-at] "invalid")
             (assoc-in prospective-thread [:comments 1 :updated-at] "2026-10-03T11:33:00Z")
             (assoc-in prospective-thread [:comments 1 :body] (str "> " (:body proposal)))
             (update-in prospective-thread [:comments 1 :body] str/replace #"(?m)^Finding:[^\n]*\n" "")
             (update prospective-thread :comments conj
                     (assoc proposal :id 4172979880 :created-at "2026-10-03T11:35:00Z" :updated-at "2026-10-03T11:35:00Z"))
             (update prospective-thread :comments conj
                     (assoc proposal :id 4172979880 :created-at "2026-10-03T11:35:00Z" :updated-at "2026-10-03T11:35:00Z"
                            :body (str (:body proposal) "\nRejection proposal for " (:head fixture) ":")))]]
    (is (not (:eligible? (request t)))))
  (is (not (:eligible? (law/rejection-assessment-request prospective-thread 1 prospective-user)))))

(deftest prospective-rejection-principal-keeps-existing-independence-policy
  (doseq [u [(assoc prospective-user :login "coderabbitai[bot]")
             (assoc prospective-user :login "unknown-assessor[bot]")
             (assoc prospective-user :type "User")
             (assoc prospective-user :id nil)
             (assoc prospective-user :node_id "")
             (assoc prospective-user :extra true)]]
    (is (not (:eligible? (law/rejection-assessment-request prospective-thread (:id proposal) u)))))
  (doseq [t [(assoc prospective-thread :pr-author (:login prospective-user))
             (assoc-in prospective-thread [:comments 0 :author] (:login prospective-user))
             (assoc-in prospective-thread [:identities "opencode"] #{"opencode-agent[bot]" "coderabbitai[bot]"})]]
    (let [u (if (contains? (get-in t [:identities "opencode"]) "coderabbitai[bot]")
              (assoc prospective-user :login "coderabbitai[bot]") prospective-user)]
      (is (not (:eligible? (law/rejection-assessment-request t (:id proposal) u)))))))

(deftest prospective-rejection-body-is-reconstructed-without-native-credit
  (let [r (publish prospective-thread prospective-summary)]
    (is (:eligible? r))
    (is (= prospective-summary (:body r)))
    (is (not-any? #(contains? r %) [:source-id :agreement-id :created-at :url :settled? :rejection-approved?]))
    (is (not (:settled? (law/classify-thread prospective-thread)))))
  (doseq [body [nil "I agree."
               (str "> " (str/replace prospective-summary "\n" "\n> "))
               (str "```text\n" prospective-summary "\n```")
               (str "<details><summary>Example</summary>\n" prospective-summary "\n</details>")
               (str/replace prospective-summary (:head fixture) (apply str (repeat 40 "b")))
               (str/replace prospective-summary (:thread_id fixture) "PRRT_another")
               (str prospective-summary "\nFinding: PRRT_another comment42")
               (str prospective-summary "\nReason: Another reason.")
               (str/replace prospective-summary #"(?m)^Reason:.*$" "Reason: I agree.")
               (str/replace prospective-summary #"(?m)^Reason:.*$" "Reason: I disagree with the rejection; this still reproduces.")
               (str/replace prospective-summary #"(?m)^Evidence:.*$" "Evidence: none")
               (str prospective-summary "\nExtra unsourced tail.")]]
    (is (not (:eligible? (publish prospective-thread body)))))
  ;; Native metadata is supplied only in this explicit inert post-POST control.
  ;; Prospective eligibility never substitutes for unchanged settlement law.
  (let [native (assoc agreement :body prospective-summary)]
    (is (approved? (assoc input :issue-comments [native])))
    (is (not (approved? (assoc input :issue-comments
                              [(assoc native :created_at (:updated-at proposal) :updated_at (:updated-at proposal))]))))
    (is (not (approved? (assoc input :issue-comments
                              [(assoc native :updated_at "2026-10-03T11:39:00Z")]))))))
