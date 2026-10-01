(ns pr-flow.flow
  "Pure laws over the pr-flow state machine data (flow.edn): well-formedness
   and next-state queries. Reading the file is the caller's job.")

(defn states [flow] (set (keys (:flow/states flow))))

(defn problems
  "Return a vector of well-formedness problems; empty means lawful."
  [flow]
  (let [ss (states flow)
        ts (:flow/transitions flow)
        targets (set (map second ts))
        sources (set (map first ts))]
    (cond-> []
      (not (ss (:flow/initial flow)))
      (conj {:problem :initial-not-a-state :state (:flow/initial flow)})

      :always
      (into (for [[a b] ts :when (not (and (ss a) (ss b)))]
              {:problem :transition-to-unknown-state :edge [a b]}))

      :always
      (into (for [s ss :when (and (not= s (:flow/initial flow)) (not (targets s)))]
              {:problem :unreachable-state :state s}))

      :always
      (into (for [s ss :when (and (not ((:flow/terminal flow) s)) (not (sources s)))]
              {:problem :dead-end-state :state s}))

      :always
      (into (for [[s {:keys [skill]}] (:flow/states flow) :when (not (string? skill))]
              {:problem :state-without-skill :state s})))))

(defn next-states
  "States reachable in one step from `state`."
  [flow state]
  (->> (:flow/transitions flow) (filter #(= state (first %))) (map second) distinct vec))

(defn skills
  "Every skill the flow names, as entry or :uses."
  [flow]
  (->> (vals (:flow/states flow))
       (mapcat (fn [{:keys [skill uses]}] (cons skill uses)))
       (remove nil?) set))
