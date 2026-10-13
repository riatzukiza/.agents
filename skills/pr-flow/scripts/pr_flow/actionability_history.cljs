(ns pr-flow.actionability-history
  "Decode complete canonical observation history; no reads or append authority."
  (:require [clojure.string :as str]
            [edamame.core :as edamame]))

(defn observations
  "Decode every complete receipt line before repository selection. Nil source,
   invalid records or repository identity mean unavailable; valid empty is [].
   The caller owns complete-source acquisition, byte custody and filesystem I/O."
  [source repo-id]
  (try
    (when-not (and (string? repo-id) (not (str/blank? repo-id)))
      (throw (ex-info "Native repository identity unavailable" {})))
    (when-not (or (nil? source) (string? source))
      (throw (ex-info "Complete history text unavailable" {})))
    (when (some? source)
      (vec (mapcat (fn [line]
                     (let [forms (edamame/parse-string-all line)
                           receipt (first forms)]
                       (when-not (and (= 1 (count forms)) (map? receipt))
                         (throw (ex-info "Invalid receipt record" {})))
                       (when (= "pr-flow-actionability-observation" (:origin receipt))
                         (when-not (and (or (nil? (:repo receipt)) (string? (:repo receipt)))
                                        (vector? (:decisions receipt))
                                        (every? #(and (map? %) (contains? #{:thread-actionability :positive-bot-thread-preservation} (:purpose %))
                                                      (string? (:repo-id %)) (not (str/blank? (:repo-id %))))
                                                (:decisions receipt))
                                        (<= (count (set (map :repo-id (:decisions receipt)))) 1))
                           (throw (ex-info "Invalid actionability observation record" {})))
                         (filterv #(= repo-id (:repo-id %)) (:decisions receipt)))))
                   (remove str/blank? (str/split-lines source)))))
    (catch :default _ nil)))
