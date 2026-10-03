(ns test-legacy
  (:require ["fs" :as fs] ["path" :as path]
            [cljs.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [nbb.core :refer [*file*]]
            [pr-flow.law :as law]))

(def here (path/dirname *file*))
(def legacy-body (fs/readFileSync (path/join here "fixtures/legacy-coderabbit-review.md") "utf8"))
(def expected-severities
  (edn/read-string (fs/readFileSync (path/join here "fixtures/legacy-coderabbit-severities.edn") "utf8")))

(defn legacy-item [banner id]
  (str "<details>\n<summary>fixture.md (1)</summary><blockquote>\n\n"
       banner "\n\n**Finding.**\n\n<!-- cr-comment:v1:" id " -->\n\n</blockquote></details>\n"))

(deftest real-legacy-review-has-exact-per-item-severities
  (let [findings (law/review-body-findings legacy-body)]
    (is (= 49 (count findings)))
    (is (= 49 (count (set (map :id findings)))))
    (is (= expected-severities (into {} (map (juxt :id :severity) findings))))
    (is (= {:p1 33 :p2 14 :p3 2} (frequencies (map :severity findings))))
    (is (= "Legacy finding 1." (:title (first findings))))))

(deftest legacy-severity-is-local-and-explicit
  (doseq [[banner expected] [["`1-2`: _Category_ | _🔴 Critical_ | _Effort_" :p0]
                             ["`1-2`: _Category_ | _🟠 Major_ | _Effort_" :p1]
                             ["`1-2`: _Category_ | _🟡 Minor_ | _Effort_" :p2]
                             ["`1-2`: _Category_ | _🔵 Trivial_ | _Effort_" :p3]]]
    (is (= [expected] (mapv :severity (law/review-body-findings (legacy-item banner "item1"))))))
  (testing "outer headings, prose, quotes and malformed banners cannot lower severity"
    (doseq [banner ["" "_🟡 Minor_" "`1-2`: _Category_ | _Unknown_ | _Effort_"
                     "`1-2`: _Category_ | _🟡 Major_ | _Effort_"
                     "`2-1`: _Category_ | _🟡 Minor_ | _Effort_"
                     "`1-2`: _Category_ | _🟡 Minor P0_ | _Effort_"
                     "Text mentioning Minor."
                     "> `1-2`: _Category_ | _🟡 Minor_ | _Effort_"
                     "```text\n`1-2`: _Category_ | _🟡 Minor_ | _Effort_\n```"]]
      (is (= [:p1] (mapv :severity (law/review-body-findings
                                   (str "<details>\n<summary>🟡 Minor comments (1)</summary><blockquote>\n"
                                        (legacy-item banner "unknown1") "</blockquote></details>")))))))
  (testing "a neighboring or nested item's banner cannot leak into a missing banner"
    (let [minor (legacy-item "`1-2`: _Category_ | _🟡 Minor_ | _Effort_" "minor1")
          unknown (legacy-item "Missing banner." "unknown1")]
      (is (= {"minor1" :p2 "unknown1" :p1}
             (into {} (map (juxt :id :severity) (law/review-body-findings (str minor unknown))))))
      (is (= :p1 (:severity (first (filter #(= "unknown1" (:id %))
                                                  (law/review-body-findings
                                                   (str/replace unknown "Missing banner." minor)))))))))
  (testing "an unclosed item is ambiguous"
    (is (= [:p1] (mapv :severity (law/review-body-findings
                                 (str/replace (legacy-item "`1-2`: _Category_ | _🟡 Minor_ | _Effort_" "unknown1")
                                              "</blockquote></details>" "")))))))

(deftest real-legacy-settlement-respects-severity-and-authority
  (let [review {:id 4895165651 :user {:login "coderabbitai[bot]" :type "Bot"}
                :state "COMMENTED" :body legacy-body :submitted_at "2026-10-03T01:00:00Z"}
        answer {:user {:login "different-writer" :type "User"} :created_at "2026-10-03T01:01:00Z"
                :body (str "Handled: review-id:4895165651\n"
                           (str/join "\n" (for [[id sev] expected-severities]
                                            (str "- " (case sev :p1 "Fixed" :p2 "Deferred" :p3 "Rejected")
                                                 " cr-comment:v1:" id ": verified disposition"))))}]
    (is (= 49 (law/unanswered-review-count [review] [])))
    (is (= 0 (law/unanswered-review-count [review] [answer])))
    (is (= 33 (law/unanswered-review-count [review] [(update answer :body str/replace "Fixed" "Deferred")])))
    (is (= 49 (law/unanswered-review-count [review] [(assoc-in answer [:user :login] "CODERABBITAI[BOT]")])))))

(deftest ambiguous-legacy-banner-and-generated-examples
  (let [minor (legacy-item "`1-2`: _Category_ | _🟡 Minor_ | _Effort_" "minor1")
        major "`1-2`: _Category_ | _🟠 Major_ | _Effort_"
        severity-of #(mapv :severity (law/review-body-findings %))]
    (is (= [:p1] (severity-of (str/replace minor "**Finding.**" major))))
    (is (= [:p2] (severity-of (str/replace minor "**Finding.**" (str "```text\n" major "\n```\n**Finding.**")))))
    (is (= [:p2] (severity-of (str/replace minor "**Finding.**" (str "> " major "\n**Finding.**")))))
    (is (= [:p1] (severity-of (str "~~~text\n" minor "~~~\n"))))
    (is (= [:p1] (severity-of (str minor "```text\nUnterminated example."))))))

(deftest modern-and-legacy-items-retain-distinct-banners
  (let [modern "<summary><em>🟠 Major</em> · Use <code>foo</code> · <code>x:1</code></summary><!-- cr-comment:v1:modern1 -->"
        legacy (legacy-item "`1-2`: _Category_ | _🟡 Minor_ | _Effort_" "legacy1")]
    (is (= {"modern1" :p1 "legacy1" :p2}
           (into {} (map (juxt :id :severity) (law/review-body-findings (str modern "\n" legacy))))))
    (is (= "Use <code>foo</code>" (:title (first (law/review-body-findings (str modern "\n" legacy))))))))

(defn modern-item [banner title prose]
  (str "<summary><em>" banner "</em> · " title " · <code>x:1</code></summary>"
       prose "<!-- cr-comment:v1:modern1 -->"))

(deftest explicit-own-title-priority-overrides-known-banner
  (doseq [[label expected] [["P0" :p0] ["P1" :p1] ["P2" :p2] ["P3" :p3]]]
    (let [title (str "[" label "] Broken authorization.")
          legacy (str/replace (legacy-item "`1-2`: _Category_ | _🟡 Minor_ | _Effort_" "legacy1")
                              "**Finding.**" (str "**" title "**"))]
      (is (= expected (:severity (first (law/review-body-findings legacy)))))
      (is (= expected (:severity (first (law/review-body-findings (modern-item "🟡 Minor" title "prose"))))))))
  (let [legacy (str/replace (legacy-item "`1-2`: _Category_ | _🟠 Major_ | _Effort_" "legacy1")
                            "**Finding.**" "**[P3] Optional cleanup.**")]
    (is (= :p3 (:severity (first (law/review-body-findings legacy)))))
    (is (= :p3 (:severity (first (law/review-body-findings (modern-item "🟠 Major" "[P3] Optional cleanup." ""))))))))

(deftest generated-quoted-or-later-priority-is-not-the-own-title
  (doseq [[prose legacy-severity]
          [["```text\n**[P0] Example only.**\n```" :p2]
           ["> **[P0] Quoted finding.**" :p2]
           ;; Nested blockquotes are an unsupported legacy container shape;
           ;; the existing conservative P1 fallback still applies.
           ["<blockquote>**[P0] Quoted finding.**</blockquote>" :p1]
           ["<details>\n<summary>🤖 Prompt for AI Agents</summary>\n**[P0] Generated finding.**\n</details>" :p2]
           ["Later prose mentions P0 and **[P0] another title.**" :p2]]]
    (let [legacy (str/replace (legacy-item "`1-2`: _Category_ | _🟡 Minor_ | _Effort_" "legacy1")
                              "**Finding.**" (str "**Own finding.**\n\n" prose))]
      (is (= legacy-severity (:severity (first (law/review-body-findings legacy)))))
      (is (= :p2 (:severity (first (law/review-body-findings (modern-item "🟡 Minor" "Own finding." prose))))))))
  (testing "a missing own title does not promote a quoted or generated bold title"
    (doseq [prose ["> **[P0] Quoted finding.**"
                   "<details>\n<summary>🤖 Prompt for AI Agents</summary>\n**[P0] Generated finding.**\n</details>"]]
      (is (= :p2 (:severity (first (law/review-body-findings
                                   (str/replace (legacy-item "`1-2`: _Category_ | _🟡 Minor_ | _Effort_" "legacy1")
                                                "**Finding.**" prose))))))))
  (testing "quoted/generated text embedded in a modern title is not live priority"
    (doseq [title ["> [P0] Quoted title." "```text\n[P0] Example title.\n```"
                   "<blockquote>[P0] Quoted title.</blockquote>"
                   "<details><summary>🤖 Prompt for AI Agents</summary>[P0] Generated title.</details>"]]
      (is (= :p2 (:severity (first (law/review-body-findings (modern-item "🟡 Minor" title "")))))))))

(deftest title-priority-cannot-downgrade-an-invalid-banner
  (doseq [banner ["`1-2`: _Category_ | _Unknown_ | _Effort_"
                   "`1-2`: _Category_ | _🟡 Major_ | _Effort_"
                   "`2-1`: _Category_ | _🟡 Minor_ | _Effort_"]]
    (is (= :p1 (:severity (first (law/review-body-findings
                                 (str/replace (legacy-item banner "legacy1") "**Finding.**" "**[P3] Optional.**")))))))
  (is (= :p1 (:severity (first (law/review-body-findings (modern-item "Unknown" "[P3] Optional." "")))))))
