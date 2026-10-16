(ns test-babashka
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [pr-flow.actionability :as actionability]
            [pr-flow.law :as law]))

(deftest account-quota-availability-is-portable-and-retains-mandatory-agents
  (let [head (apply str (repeat 40 "a"))
        old (apply str (repeat 40 "b"))
        request {:id 1 :trusted? true :created_at "2026-10-03T01:00:00Z"
                 :body (str "<!-- pr-flow-review:" old " --> <!-- pr-flow-reviewer:codex -->")}
        quota {:id 2 :user {:login "chatgpt-codex-connector[bot]" :type "Bot"}
               :created_at "2026-10-03T01:01:00Z"
               :body "You have reached your Codex usage limits for code reviews."}
        input {:head head :comments [request quota] :reviews [] :checks []
               :identities {"codex" #{"chatgpt-codex-connector[bot]"} "mimo" #{"eta-mu-ai[bot]"}}
               :now-ms 1790989320000}
        observed (law/reviewer-availability input)]
    (is (= #{"mimo"} (:review-participants observed)))
    (is (= old (get-in observed [:review-availability "codex" :request-head])))
    (is (= #{"mimo" "codex"} (:review-participants (law/reviewer-availability (assoc input :mandatory #{"codex"})))))))

(deftest issue-comments-without-bodies-remain-findings
  ;; NBB tolerated the old split-lines nil call; Babashka throws. Exercise
  ;; disposition itself so both native null and absent body fields stay safe.
  (doseq [comment [{:body nil} {}]]
    (testing (str "issue comment " (pr-str comment))
      (is (= {:kind :finding :status :absent :observations []}
             (select-keys (actionability/disposition {:issue-comments [comment]
                                                     :actionability-observations []})
                          [:kind :status :observations])))
      (is (= {:kind :finding :status :unavailable :observations []}
             (select-keys (actionability/disposition {:issue-comments [comment]})
                          [:kind :status :observations]))))))

(deftest codex-positive-envelope-is-portable
  (let [legacy "Codex Review: Didn't find any major issues."
        envelope "<!-- codex-review-receipt:v1 -->\n\n"]
    (is (true? (law/codex-positive-body? legacy)))
    (is (true? (law/codex-positive-body? (str envelope legacy))))
    (is (false? (law/codex-positive-body? (str envelope envelope legacy))))
    (is (false? (law/codex-positive-body? (str " " legacy))))
    (is (false? (law/codex-positive-body? nil)))
    (is (true? (law/codex-positive-body? (str envelope legacy "\n\n> " envelope))))))

(deftest in-sentence-marker-incomplete-scope-is-portable
  (let [prefix "Codex Review: Didn't find any major issues."
        marker "<!-- codex-review-receipt:v1 -->"
        admission (str "Review " marker " is incomplete.")]
    (doseq [envelope ["" (str marker "\n\n")]]
      (doseq [prose [admission (str "Review\t" marker "\r\nis\tincomplete.")]]
        (let [body (str envelope prefix "\n\n" prose)]
          (is (true? (law/codex-positive-body? body)))
          (is (= :incomplete-review (law/incomplete-review-reason body)) (pr-str prose))))
      (doseq [excluded [(str "> " admission)
                        (str "```text\n" admission "\n```")
                        (str "<details>\n<summary>🧩 Analysis chain</summary>\n" admission "\n</details>")
                        (str "Reject incomplete " marker " review submissions.")]]
        (is (nil? (law/incomplete-review-reason (str envelope prefix "\n\n" excluded)))
            (pr-str excluded))))))

(deftest paragraph-and-exclusion-scope-boundaries-are-portable
  (doseq [separator ["\n\n" "\r\n\r\n" "\r\r" "\n \t\n"]]
    (is (nil? (law/incomplete-review-reason
                (str "Review" separator "Rate limited requests are retried with backoff.")))
        (pr-str separator)))
  (doseq [body ["Review\n> example text\nis incomplete."
                "Review\n```text\nexample text\n```\nis incomplete."
                "Review <details><summary>🧩 Analysis chain</summary>example text</details> is incomplete."
                "Review <details><summary>✏️ Learnings added</summary>example text</details> is incomplete."
                "Review <!-- This is an auto-generated comment: tweet message by coderabbit.ai -->example text<!-- end of auto-generated comment: tweet message by coderabbit.ai --> is incomplete."]]
    (is (nil? (law/incomplete-review-reason body)) body))
  (doseq [body ["Review <!-- unrelated inert note --> is incomplete."
                "> <!--\n\nReview <!-- unrelated inert note --> is incomplete.\n-->"]]
    (is (= :incomplete-review (law/incomplete-review-reason body)) body)))

(let [{:keys [fail error]} (run-tests 'test-babashka)]
  (when (pos? (+ fail error))
    (System/exit 1)))
