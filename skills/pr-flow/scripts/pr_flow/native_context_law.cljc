;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.native-context-law
  "Pure structural bindings for complete native conversation input."
  (:require [pr-flow.law :as law]))

(defn page-snapshot
  "The four native coordinates that must agree across thread pages."
  [repository pr]
  {:repository-id (:id repository) :pr-id (:id pr)
   :head (:headRefOid pr) :draft? (:isDraft pr)})

(defn select-fresh-context
  "Select one original normalized thread from a complete bound snapshot.
   Target carries caller-validated repo, repository-database-id, pr-number,
   head and thread-id coordinates. This performs no normalization, permission,
   classification, history parsing or semantic/publication admission."
  [snapshot {:keys [repo repository-database-id pr-number head thread-id review-id item-id] :as target}]
  (let [body? (= :review-body-item (:kind target))
        matches (if body?
                  (when (vector? (:reviews snapshot)) (filterv #(= review-id (:id %)) (:reviews snapshot)))
                  (when (vector? (:threads snapshot)) (filterv #(= thread-id (:id %)) (:threads snapshot))))]
    (cond
      (not (map? snapshot)) {:refusal :context-unavailable}

      (not (and (map? target)
                (every? some? [repo repository-database-id pr-number head])
                (if body?
                  (and (integer? review-id) (pos? review-id) (string? item-id)
                       (not-any? #(contains? target %) [:thread-id :root-comment-id :items :scopes]))
                  (some? thread-id))))
      {:refusal :target-unavailable}

      (not (and (= repo (get-in snapshot [:repository :nameWithOwner]))
                (= repository-database-id (get-in snapshot [:repository :databaseId]))
                (= pr-number (get-in snapshot [:pr :number])) (= head (:head snapshot))))
      {:refusal :target-mismatch}

      (not= "OPEN" (get-in snapshot [:pr :state])) {:refusal :pr-not-open}
      (not (false? (:draft? snapshot))) {:refusal :draft-context}
      (not (false? (:incomplete? snapshot))) {:refusal :incomplete-context}
      (not (vector? (:history snapshot))) {:refusal :history-unavailable}
      (and body? (not (true? (:reviews-complete? snapshot)))) {:refusal :incomplete-reviews}
      (not= 1 (count matches)) {:refusal (if body? :review-selection :thread-selection)}
      (and body? (not (law/native-review-item? (first matches) item-id (:identities target))))
      {:refusal :item-selection}
      body? {:selected (cond-> {:kind :review-body-item :review-id review-id :item-id item-id
                                :review (first matches) :head head :pr-author (:pr-author snapshot)
                                :pr-author-user {:login (get-in snapshot [:pr :author :login])
                                                 :id (get-in snapshot [:pr :author :databaseId])
                                                 :node_id (get-in snapshot [:pr :author :id])
                                                 :type (get-in snapshot [:pr :author :__typename])}
                                :issue-comments (:issue-comments snapshot)}
                         (contains? target :identities) (assoc :identities (:identities target)))}
      :else {:selected (first matches)})))
