;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.native-context-law
  "Pure structural bindings for complete native conversation input.")

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
  [snapshot {:keys [repo repository-database-id pr-number head thread-id] :as target}]
  (let [matches (when (vector? (:threads snapshot))
                  (filterv #(= thread-id (:id %)) (:threads snapshot)))]
    (cond
      (not (map? snapshot)) {:refusal :context-unavailable}

      (not (and (map? target)
                (every? some? [repo repository-database-id pr-number head thread-id])))
      {:refusal :target-unavailable}

      (not (and (= repo (get-in snapshot [:repository :nameWithOwner]))
                (= repository-database-id (get-in snapshot [:repository :databaseId]))
                (= pr-number (get-in snapshot [:pr :number])) (= head (:head snapshot))))
      {:refusal :target-mismatch}

      (not= "OPEN" (get-in snapshot [:pr :state])) {:refusal :pr-not-open}
      (not (false? (:draft? snapshot))) {:refusal :draft-context}
      (not (false? (:incomplete? snapshot))) {:refusal :incomplete-context}
      (not (vector? (:history snapshot))) {:refusal :history-unavailable}
      (not= 1 (count matches)) {:refusal :thread-selection}
      :else {:selected (first matches)})))
