;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.repair-ack
  "Bounded independent verification of an author repair statement. No I/O."
  (:require [clojure.string :as str]))

(defn- text? [x] (and (string? x) (not (str/blank? x))))
(defn- id? [x] (and (integer? x) (pos? x)))
(defn- sha? [n x] (and (string? x) (boolean (re-matches (re-pattern (str "[0-9a-f]{" n "}")) x))))
(defn- time? [x] (and (string? x) (boolean (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z" x))))
(defn- actor [a] [(:login a) (:databaseId a) (:id a) (:__typename a)])
(defn- actor? [a] (and (text? (:login a)) (id? (:databaseId a)) (text? (:id a))
                       (contains? #{"User" "Bot"} (:__typename a))))
(defn- normalized [c]
  {:id (:databaseId c) :author (get-in c [:author :login]) :body (:body c) :url (:url c)
   :created-at (:createdAt c) :updated-at (:updatedAt c)})

(defn- plain? [s]
  ;; Do not mine quoted, generated or mixed-negative text for a positive token.
  (and (text? s) (not (re-find #"(?im)[`<>\r]|^\s*>|\b(?:not|cannot|can't|pending|unknown|unverified|retract|withdraw|unresolved|still fails|still reproduces)\b" s))))

(defn- native-analysis-tail [text]
  ;; Only the observed shell-execution carrier, never arbitrary details/prose.
  ;; Fences are structural: embedded fences, extra blocks and live prose refuse.
  (let [start "<details>\n<summary>🧩 Analysis chain</summary>\n<dl>\n<dd>\n\n"
        end "</dd>\n</dl>\n</details>\n\n"]
    (if (str/starts-with? text start)
      (when-let [i (str/index-of text end)]
        (let [carrier (subs text (count start) i)]
          (when (re-matches #"(?:🏁 Script executed:\n\n```shell\n(?:(?!```)[\s\S])*?\n```\n\nLength of output: [0-9]+\n\n---\n\n)+" carrier)
            (subs text (+ i (count end))))))
      text)))

(defn- assessment-text [body coderabbit? author-login]
  ;; Only the observed native envelope is transport. Never strip arbitrary
  ;; HTML/comments/details or mine examples for the live protocol. The five
  ;; authored lines immediately follow only the recognized native carrier/mention.
  (when (string? body)
    (let [prefix "<!-- coderabbit-ai-generated:v1 -->\n\n"]
      (if (and coderabbit? (str/starts-with? body prefix))
        (let [tail (native-analysis-tail (subs body (count prefix)))
              mention (str "`@" author-login "`")
              tail (cond
                     (and tail (str/starts-with? tail (str mention "\n"))) (subs tail (inc (count mention)))
                     (and tail (str/starts-with? tail (str mention " "))) (subs tail (inc (count mention)))
                     :else tail)
              lines (str/split (or tail "") #"\n" -1)
              live (str/join "\n" (take 5 lines))
              suffix (str "\n" (str/join "\n" (drop 5 lines)))]
          (when (and (<= 5 (count lines))
                     (re-matches #"\n{2,}(?:---\n\n<!-- This is an auto-generated comment: tweet message by coderabbit\.ai -->[\s\S]*?<!-- end of auto-generated comment: tweet message by coderabbit\.ai -->\n{2,})?(?:✅ Review thread resolved\.\n\n)?<sub><i>You are interacting with an AI system\.</i></sub>\n\n<!-- This is an auto-generated reply by CodeRabbit -->" suffix))
            live))
        body))))

(defn- affirmative-reason? [reason]
  (and (str/starts-with? (or reason "") "Reason: ") (plain? reason)
       (<= 48 (count reason))
       ;; Contradictory/uncertain live judgments cannot be overwritten by the
       ;; protocol's positive declaration. Unknown defect language stays out.
       (not (re-find #"(?i)\b(?:bugs?|defects?|vulnerabilit(?:y|ies)|unsafe|uncertain|unsure|possibly|perhaps|might|may|should|must|please|outstanding|remaining|need(?:s|ed|ing)?|requir(?:e[sd]?|ing)|fix(?:es|ed|ing)?|correct(?:ions?|ive|ing|ed))\b" reason))))

(defn- source-evidence? [evidence prefix]
  (and (string? evidence) (str/starts-with? evidence prefix) (plain? evidence)
       (let [path (subs evidence (count prefix))]
         (and (not (str/blank? path))
              (not (re-find #"[\s\\?#%:]" path))
              (every? #(and (not (str/blank? %)) (not (#{"." ".."} %)))
                      (str/split path #"/" -1))))))

(defn proof
  "Return source provenance only for a resolved author repair independently
   verified by a configured native Bot. reviewer maps a native actor to the
   existing roster; the enclosing immutable review, never comment.commit_id,
   supplies current-head evidence. All unknown input retains default findings."
  [t reviewer]
  (let [{:keys [repository pr thread]} (:native-context t)
        nodes (get-in thread [:comments :nodes]) root (first nodes) assessment (last nodes)
        prefix (butlast nodes) a (:author pr) b (:author assessment)
        head (:head t) r (:pullRequestReview assessment)
        url-prefix (str "https://github.com/" (:nameWithOwner repository) "/pull/" (:number pr) "#discussion_r")
        lines (str/split (or (assessment-text (:body assessment) (= "coderabbit" (reviewer b)) (:login a)) "") #"\n" -1)
        [header finding verified reason evidence] lines
        valid-node? (fn [c]
                      (let [review (:pullRequestReview c)]
                        (and (text? (:id c)) (id? (:databaseId c)) (actor? (:author c))
                             (= (:url c) (str url-prefix (:databaseId c))) (string? (:body c))
                             (string? (:diffHunk c)) (sha? 40 (get-in c [:originalCommit :oid]))
                             (time? (:createdAt c)) (time? (:updatedAt c))
                             (not (pos? (compare (:createdAt c) (:updatedAt c))))
                             (text? (:id review)) (id? (:databaseId review))
                             (contains? #{"COMMENTED" "APPROVED" "CHANGES_REQUESTED" "DISMISSED"} (:state review))
                             (string? (:body review)) (time? (:updatedAt review))
                             (sha? 40 (get-in review [:commit :oid]))
                             (= (actor (:author c)) (actor (:author review))))))]
    (when (and (text? (:id repository)) (id? (:databaseId repository)) (text? (:nameWithOwner repository))
               (text? (:id pr)) (id? (:number pr)) (= "OPEN" (:state pr)) (false? (:isDraft pr))
               (sha? 40 head) (= head (:headRefOid pr)) (actor? a) (= "User" (:__typename a))
               (= (:pr-author t) (:login a)) (= (:id t) (:id thread)) (text? (:id t))
               (= (:root-comment-id t) (:databaseId root))
               (true? (:resolved? t)) (true? (:isResolved thread))
               (boolean? (:outdated? t)) (= (:outdated? t) (:isOutdated thread))
               (vector? nodes) (< 1 (count nodes)) (false? (get-in thread [:comments :pageInfo :hasNextPage]))
               (= (count nodes) (get-in thread [:comments :totalCount]) (count (:comments t))
                  (count (set (map :id nodes))) (count (set (map :databaseId nodes))))
               (= (mapv normalized nodes) (mapv #(select-keys % (keys (normalized root))) (:comments t)))
               (= (mapv :createdAt nodes) (sort (map :createdAt nodes))) (every? valid-node? nodes)
               (= (actor a) (actor (:author root))) (true? (:authorized? (first (:comments t))))
               (boolean (re-find #"^Fixed(?: in [0-9a-f]{7,40})?: " (or (:body root) ""))) (plain? (:body root))
               (<= 40 (count (:body root)))
               (actor? b) (= "Bot" (:__typename b)) (reviewer b)
               (every? #(not= (% a) (% b)) [:login :databaseId :id])
               (= "COMMENTED" (:state r)) (= head (get-in r [:commit :oid]))
               (= (:createdAt assessment) (:updatedAt assessment))
               (not (pos? (compare (:updatedAt r) (:createdAt assessment))))
               (every? #(and (neg? (compare (:updatedAt %) (:createdAt assessment)))
                             (neg? (compare (get-in % [:pullRequestReview :updatedAt]) (:createdAt assessment)))) prefix)
               (vector? (:issue-comments t))
               (every? #(and (map? %) (string? (:body %))) (:issue-comments t))
               ;; Unrelated issue activity is independent. Any root-bound issue
               ;; at/after assessment (or with unknown time) conservatively
               ;; revokes; it cannot be hidden by re-resolving the thread.
               (every? (fn [c]
                         (or (not (str/includes? (or (:body c) "")
                                                (str "Finding: " (:id t) " comment" (:root-comment-id t))))
                             (and (time? (:created_at c)) (time? (:updated_at c))
                                  (not (pos? (compare (:created_at c) (:updated_at c))))
                                  (neg? (compare (:updated_at c) (:createdAt assessment))))))
                       (:issue-comments t))
               (= 5 (count lines)) (= header (str "Repair acknowledgement v1 for " head ":"))
               (= finding (str "Finding: " (:id t) " comment" (:root-comment-id t)))
               (= verified "Verified: complete-native-context/author-repair/no-unaddressed-finding")
               (affirmative-reason? reason)
               (source-evidence? evidence (str "Evidence: https://github.com/" (:nameWithOwner repository) "/blob/" head "/")))
      {:source-id (:databaseId assessment) :review-id (:databaseId r) :url (:url assessment)
       :head head :thread-id (:id t) :root-comment-id (:root-comment-id t)
       :reviewer (reviewer b)})))
