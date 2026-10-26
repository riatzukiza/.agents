(ns test-native-dl
  (:require ["fs" :as fs] ["path" :as path]
            [nbb.core :refer [*file*]]
            [cljs.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [pr-flow.law :as law]))
(def native-review
  (js->clj (js/JSON.parse (fs/readFileSync
    (path/join (path/dirname *file*) "fixtures/proxx-native-dl-review-5649047508.json") "utf8")) :keywordize-keys true))
(deftest native-dl-retains-marked-and-unmarked-items
  (let [items (law/review-findings native-review)]
    (is (= 55 (count (filter :positive-body-item items))))
    (is (= 1 (count (filter #(= :review-body (:kind %)) items))))
    (is (= 56 (count items)))))
(deftest native-dl-rejects-unbalanced-or-foreign-envelopes
  (doseq [body [(str/replace-first (:body native-review) "</dd>" "")
               (str "<div>\n" (:body native-review) "\n</div>")
               (str "> quoted\n" (:body native-review))]]
    (is (empty? (filter :positive-body-item (law/review-findings (assoc native-review :body body)))))))
(deftest native-dl-does-not-normalize-unknown-source
  (doseq [body [(str/replace-first (:body native-review) "Additional comments: 56" "Additional comments: 57")
               (str "```html\n" (:body native-review) "\n```")
               (str/replace-first (:body native-review) #"<img src=\"[^\"]+\"" "<img src=\"https://unknown.invalid/x\" alt=\"https://storage.googleapis.com/coderabbit_public_assets/x\"")
               (str/replace-first (:body native-review) "<img src=" "<img onerror=\"unknown\" src=")]]
    (is (empty? (filter :positive-body-item (law/review-findings (assoc native-review :body body)))))))
(deftest native-dl-count-mismatch-keeps-whole-body-obligation
  (doseq [body [(str/replace-first (:body native-review) "Additional comments: 56" "Additional comments: 57")
               (str/replace-first (:body native-review) "Additional comments: 56" "Additional comments: 55")]]
    (let [items (law/review-findings (assoc native-review :body body))]
      (is (empty? (filter :positive-body-item items)))
      (is (= 1 (count (filter #(= :review-body (:kind %)) items)))))))
(deftest native-dl-corrective-prose-and-duplicate-ids-remain-blocking
  (let [changed (str/replace-first (:body native-review) "LGTM!" "LGTM! Fix the broken behavior.")
        ids (map second (re-seq #"<!-- cr-comment:v1:([a-z0-9]+) -->" (:body native-review)))
        duplicate (str/replace-first (:body native-review) (second ids) (first ids))]
    (is (= 54 (count (filter :positive-body-item (law/review-findings (assoc native-review :body changed))))))
    (is (< (count (filter :positive-body-item (law/review-findings (assoc native-review :body duplicate)))) 55))))
(deftest native-dl-acknowledgments-do-not-clear-the-unmarked-finding
  ;; Synthetic writer metadata exercises unchanged native admission; this test
  ;; creates no real review, comment, assessment or settlement evidence.
  (let [head "b3d8cc6c099e8aadbef2ac9804736a3d76a2e410"
        review (assoc native-review :updated_at (:submitted_at native-review))
        ids (map :id (filter :positive-body-item (law/review-findings review)))
        writer {:id 1 :authorized? true :user {:login "fixture-writer" :type "User"}
                :created_at "2026-10-27T00:00:00Z" :updated_at "2026-10-27T00:00:00Z"
                :body (str "review-id:" (:id review) "\n"
                           (str/join "\n" (map #(str "- Handled cr-comment:v1:" % ": affirmative item on " head) ids)))}
        context {:head head}]
    (is (= 1 (law/unanswered-review-count [review] [writer] context)))
    (is (= 56 (law/unanswered-review-count [review] [(assoc writer :authorized? false)] context)))
    (is (= 56 (law/unanswered-review-count [(assoc-in review [:user :login] "unknown[bot]")] [writer] context)))
    (is (= 56 (law/unanswered-review-count [review] [writer] {:head (apply str (repeat 40 "a"))})))))
(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests 'test-native-dl)
