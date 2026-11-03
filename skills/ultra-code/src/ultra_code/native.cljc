;; SPDX-License-Identifier: LGPL-3.0-or-later
(ns ultra-code.native
  (:require [clojure.string :as str]))

(defn nonblank? [x] (and (string? x) (not (str/blank? x))))
(defn positive-int? [x] (and (integer? x) (pos? x)))
(defn path? [p]
  (and (nonblank? p) (not (str/starts-with? p "/"))
       (not (re-find #"[\\*?\[\]{}]" p))
       (every? #(and (not (str/blank? %)) (not (#{"." ".."} %))) (str/split p #"/" -1))))
(defn overlaps? [a b]
  (or (= a b) (str/starts-with? a (str b "/")) (str/starts-with? b (str a "/"))))
(defn problem [code data] (assoc data :code code))
(defn reachable? [jobs from target]
  (loop [todo (seq (get-in jobs [from :deps])) seen #{}]
    (if-let [x (first todo)]
      (cond (= x target) true (seen x) (recur (next todo) seen)
            :else (recur (concat (next todo) (get-in jobs [x :deps])) (conj seen x)))
      false)))
(defn finding? [f] (and (map? f) (nonblank? (:id f)) (nonblank? (:title f)) (nonblank? (:detail f))))
(defn valid-output? [kind output]
  (and (map? output)
       (case kind
         :implement (boolean? (:ok output))
         :gate (boolean? (:passed output))
         :review (and (true? (:scope-complete output)) (vector? (:findings output))
                      (every? finding? (:findings output))
                      (= (count (:findings output)) (count (set (map :id (:findings output))))) )
         :skeptic (and (boolean? (:scope-complete output))
                       (vector? (:additional-findings output)) (every? finding? (:additional-findings output))
                       (vector? (:votes output))
                       (every? #(and (map? %) (nonblank? (:finding-id %))
                                     (contains? #{:confirmed :refuted :uncertain} (:verdict %))
                                     (nonblank? (:reason %))) (:votes output)))
         false)))
(defn successful? [job r]
  (and (= :complete (:status r)) (or (not (contains? r :exit-code)) (= 0 (:exit-code r))) (valid-output? (:kind job) (:output r))
       (case (:kind job) :implement (true? (get-in r [:output :ok]))
             :gate (true? (get-in r [:output :passed]))
             :skeptic (and (true? (get-in r [:output :scope-complete])) (not-any? #(= :uncertain (:verdict %)) (get-in r [:output :votes]))) true)))
(defn lineage-complete? [jobs receipts bad-jobs dependencies]
  (loop [pending (seq dependencies) seen #{}]
    (if-let [id (first pending)]
      (cond
        (contains? seen id) (recur (next pending) seen)
        (or (contains? bad-jobs id) (not (successful? (get jobs id) (get receipts id)))) false
        :else (recur (concat (next pending) (:deps (get jobs id))) (conj seen id)))
      true)))
(defn qualify* [bundle]
  (let [{:keys [snapshot input-hash round capacity limited? jobs receipts]} bundle
        jobs (if (vector? jobs) jobs []) receipts (if (vector? receipts) receipts [])
        jm (into {} (map (juxt :id identity) jobs))
        rm (into {} (map (juxt :job-id identity) receipts))
        base (concat
              (when-not (and (string? snapshot) (re-matches #"[0-9a-f]{64}" snapshot) (string? input-hash) (re-matches #"[0-9a-f]{64}" input-hash)
                             (positive-int? round) (positive-int? capacity) (boolean? limited?)
                             (map? (:inputs bundle)) (every? #(nonblank? (get (:inputs bundle) %)) [:prompt :roles :schemas :packets :gates :model])) [(problem :invalid-binding {})])
              (when (or (empty? jobs) (not= (count jobs) (count jm))) [(problem :invalid-roster {})])
              (when (or (not (some #(= :review (:kind %)) jobs)) (not (some #(= :gate (:kind %)) jobs))) [(problem :missing-coverage {})])
              (when-not (= (count receipts) (count rm)) [(problem :duplicate-receipt {})])
              (for [r receipts :when (not (contains? jm (:job-id r)))] (problem :unknown-receipt {:job-id (:job-id r)}))
              (for [j jobs :when (not (and (nonblank? (:id j)) (contains? #{:implement :review :skeptic :gate} (:kind j))
                                             (positive-int? (:attempt j)) (vector? (:deps j))
                                             (every? nonblank? (:deps j)) (vector? (:writes j))
                                             (every? path? (:writes j))
                                             (or (= :implement (:kind j)) (empty? (:writes j)))
                                             (or (not= :skeptic (:kind j)) (nonblank? (:author-job j)))))]
                (problem :invalid-job {:job-id (:id j)}))
              (for [j jobs d (:deps j) :when (not (contains? jm d))] (problem :missing-dependency {:job-id (:id j) :dependency d}))
              (for [j jobs :when (reachable? jm (:id j) (:id j))] (problem :cycle {:job-id (:id j)}))
              (for [a jobs b jobs :when (and (nonblank? (:id a)) (nonblank? (:id b)) (neg? (compare (:id a) (:id b)))
                                            (some true? (for [x (:writes a) y (:writes b) :when (and (path? x) (path? y))] (overlaps? x y)))
                                            (not (reachable? jm (:id a) (:id b))) (not (reachable? jm (:id b) (:id a))))]
                (problem :write-conflict {:jobs [(:id a) (:id b)]}))
              (for [j jobs :when (and (= :review (:kind j)) (not (some #(and (= :skeptic (:kind %)) (= (:id j) (:author-job %))) jobs)))] (problem :missing-skeptic {:job-id (:id j)}))
              (for [j jobs :when (and (= :skeptic (:kind j)) (or (not= :review (:kind (get jm (:author-job j)))) (not (reachable? jm (:id j) (:author-job j)))))] (problem :invalid-skeptic-job {:job-id (:id j)})))
        binding-problems (concat
          (for [r receipts :let [j (get jm (:job-id r))] :when (and j (or (not= snapshot (:snapshot r)) (not= input-hash (:input-hash r)) (not= round (:round r)) (not= (:attempt j) (:attempt r))))] (problem :stale-receipt {:job-id (:job-id r)}))
          (for [j jobs :when (= :skeptic (:kind j)) :let [r (get rm (:id j)) claimed (get-in r [:upstream-hashes (:author-job j)]) actual (get-in bundle [:output-hashes (:author-job j)])] :when (and (= :complete (:status r)) (nonblank? claimed) (nonblank? actual) (not= claimed actual))] (problem :stale-upstream {:job-id (:id j)})))
        evidence (concat
                  (when limited? [(problem :limited {})])
                  (when (some #(= :implement (:kind %)) jobs) [(problem :unfrozen-wave {})])
                  (for [j jobs :let [r (get rm (:id j))] :when (nil? r)] (problem :missing-receipt {:job-id (:id j)}))
                  (for [r receipts :let [j (get jm (:job-id r))] :when (and j (or (not (nonblank? (:context-id r))) (and (contains? #{:review :skeptic} (:kind j)) (or (not= :fresh (:context-mode r)) (not (nonblank? (:spawn-ref r))))) (and (contains? r :exit-code) (not (integer? (:exit-code r)))) (not (contains? #{:complete :failed :timeout :running} (:status r))) (and (= :complete (:status r)) (not (valid-output? (:kind j) (:output r))))))] (problem :invalid-receipt {:job-id (:job-id r)}))
                  (for [r receipts :when (contains? #{:timeout :running} (:status r))] (problem :unfinished {:job-id (:job-id r)}))
                  (for [j jobs :let [r (get rm (:id j))] :when (and (= :complete (:status r)) (some #(not (successful? (get jm %) (get rm %))) (:deps j)))] (problem :dependency-incomplete {:job-id (:id j)}))
                  (for [j jobs :when (and (= :review (:kind j)) (nonblank? (:context-id (get rm (:id j))))
                                          (some #(= (:context-id (get rm (:id j))) (:context-id (get rm (:id %)))) (filter (fn [x] (= :implement (:kind x))) jobs)))] (problem :shared-context {:job-id (:id j)}))
                  (for [a jobs b jobs :when (and (contains? #{:review :skeptic} (:kind a)) (contains? #{:review :skeptic} (:kind b)) (neg? (compare (:id a) (:id b))) (nonblank? (:context-id (get rm (:id a)))) (= (:context-id (get rm (:id a))) (:context-id (get rm (:id b)))))] (problem :shared-agent-context {:jobs [(:id a) (:id b)]}))
                  (for [a jobs b jobs :when (and (contains? #{:review :skeptic} (:kind a)) (contains? #{:review :skeptic} (:kind b)) (neg? (compare (:id a) (:id b))) (nonblank? (:spawn-ref (get rm (:id a)))) (= (:spawn-ref (get rm (:id a))) (:spawn-ref (get rm (:id b)))))] (problem :shared-spawn-ref {:jobs [(:id a) (:id b)]}))
                  (for [j jobs :when (= :skeptic (:kind j))
                        :let [r (get rm (:id j)) ar (get rm (:author-job j)) out (:output r)
                              ids (mapv :id (get-in ar [:output :findings])) votes (:votes out) vote-ids (mapv :finding-id votes)]
                        :when (and (= :complete (:status r))
                                   (or (not= #{(:author-job j)} (set (keys (:upstream-hashes r))))
                                       (not (nonblank? (get-in r [:upstream-hashes (:author-job j)])))
                                       (not (nonblank? (get-in bundle [:output-hashes (:author-job j)])))
                                       (not (true? (:scope-complete out)))
                                       (not= (count vote-ids) (count (set vote-ids)))
                                       (not= (set ids) (set vote-ids))
                                       (some #(= :uncertain (:verdict %)) votes)
                                       (some #(and (nonblank? (:context-id r)) (= (:context-id r) (:context-id (get rm (:id %))))) (filter (fn [x] (= :implement (:kind x))) jobs))))]
                    (problem :incomplete-skeptic {:job-id (:id j)})))
        failures (for [r receipts :let [j (get jm (:job-id r))] :when (or (= :failed (:status r)) (and (integer? (:exit-code r)) (not (zero? (:exit-code r)))) (and (= :complete (:status r)) (or (and (= :gate (:kind j)) (false? (get-in r [:output :passed]))) (and (= :implement (:kind j)) (false? (get-in r [:output :ok]))))))] (problem :execution-failed {:job-id (:job-id r)}))
        problems (vec (concat base binding-problems evidence failures))
        bad-jobs (set (mapcat #(concat (when-let [id (:job-id %)] [id]) (:jobs %)) problems))
        running (count (filter #(= :running (:status %)) receipts))
        available (max 0 (- (if (positive-int? capacity) capacity 0) running))
        ready (if (or (seq base) limited?) []
                (->> jobs (filter #(and (not (contains? rm (:id %))) (lineage-complete? jm rm bad-jobs (:deps %)))) (take available) (mapv :id)))
        confirmed? (some #(and (= :skeptic (:kind %)) (or (seq (get-in rm [(:id %) :output :additional-findings])) (some (fn [v] (= :confirmed (:verdict v))) (get-in rm [(:id %) :output :votes])))) jobs)]
    {:status (cond (seq binding-problems) :stale (seq failures) :failed (seq problems) :incomplete confirmed? :findings :else :complete-clean)
     :problems problems :ready ready}))

(defn qualify [bundle]
  (if (and (map? bundle) (vector? (:jobs bundle)) (vector? (:receipts bundle))
           (every? #(and (map? %) (string? (:id %)) (vector? (:deps %)) (vector? (:writes %))) (:jobs bundle))
           (every? #(and (map? %) (or (nil? (:upstream-hashes %)) (map? (:upstream-hashes %)))) (:receipts bundle))
           (every? #(every? (fn [k] (let [v (get-in % [:output k])] (or (nil? v) (vector? v)))) [:findings :votes :additional-findings]) (:receipts bundle)))
    (qualify* bundle)
    {:status :incomplete :problems [(problem :invalid-shape {})] :ready []}))
