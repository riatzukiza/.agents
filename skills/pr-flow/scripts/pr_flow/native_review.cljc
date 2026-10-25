;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.native-review
  "Whole native review selection and custody. No HTML extraction or gate authority."
  (:require [clojure.string :as str]
            [pr-flow.law :as law]))

(defn- text? [x] (and (string? x) (not (str/blank? x))))
(defn- positive? [x] (and (integer? x) (pos? x)))
(defn- rest-actor [actor]
  [(:id actor) (:node_id actor) (:login actor) (:type actor)])
(defn- native-actor [actor]
  [(:databaseId actor) (:id actor) (law/graphql-rest-login actor) (:__typename actor)])
(defn- actor? [[id node login kind]]
  (and (positive? id) (text? node) (text? login) (contains? #{"User" "Bot"} kind)))

(defn participants
  "Preserve complete native principal tuples; missing or contradictory identities refuse."
  [snapshot]
  (let [comments (mapcat #(get-in % [:comments :nodes]) (:raw-threads snapshot))
        actors (vec (distinct
                      (concat [(native-actor (get-in snapshot [:pr :author]))]
                              (map #(rest-actor (:user %)) (:reviews snapshot))
                              (map #(rest-actor (:user %)) (:raw-issue-comments snapshot))
                              (map #(native-actor (:author %)) comments)
                              (map #(native-actor (get-in % [:pullRequestReview :author])) comments))))]
    (when (and (every? actor? actors)
               (every? (fn [index]
                         (every? #(= 1 (count (distinct (val %)))) (group-by #(nth % index) actors)))
                       [0 1 2]))
      (vec (sort actors)))))

(defn select-source
  "Select one whole raw published review from complete canonical collection.
   Permissions are native observations keyed by complete User principal tuples.
   Digests bind all native corpus/history/policy; none proves freshness by itself."
  [snapshot {:keys [repository pr-number review-id] :as selection} permissions policy-source sha256]
  (let [repo (:repository snapshot) pr (:pr snapshot) actors (participants snapshot)
        matches (filter #(= review-id (:id %)) (:reviews snapshot))
        review (first matches) node (:native-review review) actor (:user review)
        required-users (set (filter #(= "User" (nth % 3)) actors))
        complete? (and (= #{:repository :pr-number :review-id} (set (keys selection)))
                       (positive? pr-number) (positive? review-id)
                       (= repository (:nameWithOwner repo)) (= pr-number (:number pr))
                       (positive? (:databaseId repo)) (text? (:id repo)) (text? (:id pr))
                       (= "OPEN" (:state pr)) (false? (:isDraft pr)) (false? (:draft? snapshot))
                       (= (:head snapshot) (:headRefOid pr)) (law/valid-head? (:head snapshot))
                       (false? (:incomplete? snapshot)) (true? (:reviews-complete? snapshot))
                       (vector? (:raw-threads snapshot)) (vector? (:raw-issue-comments snapshot))
                       (vector? (:raw-reviews snapshot)) (vector? (:reviews snapshot))
                       (vector? (:history snapshot)) (some? actors)
                       (= required-users (set (keys permissions))) (every? boolean? (vals permissions))
                       (= 1 (count matches))
                       (contains? #{"COMMENTED" "APPROVED" "CHANGES_REQUESTED" "DISMISSED"} (:state review))
                       (law/review-update-matches? review node (:type actor))
                       (= (rest-actor actor) (native-actor (:author node)))
                       (text? policy-source) (fn? sha256))]
    (if-not complete?
      {:refusal :incomplete-native-custody}
      {:canonical-obligations (law/review-findings review)
       :input {:repository repository :pr-number pr-number :head (:head snapshot)
               :source {:id (:id review) :node-id (:node_id review) :body (:body review)
                        :updated-at (:updatedAt node) :commit (:commit_id review)
                        :actor {:id (:id actor) :node-id (:node_id actor) :login (:login actor) :type (:type actor)}}}
       :custody {:corpus-sha256 (sha256 (pr-str [repo pr (:raw-threads snapshot)
                                                (:raw-issue-comments snapshot) (:raw-reviews snapshot)
                                                (:reviews snapshot)]))
                 :history-sha256 (sha256 (pr-str (:history snapshot)))
                 :permissions-sha256 (sha256 (pr-str (vec (sort permissions))))
                 :policy-sha256 (sha256 policy-source)}})))
