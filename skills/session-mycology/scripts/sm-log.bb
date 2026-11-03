#!/usr/bin/env bb

(require '[clojure.java.io :as io]
         '[clojure.string :as str])

(def script-dir
  (some-> *file* io/file .getParent))

(when-not script-dir
  (binding [*out* *err*]
    (println "Run this script as an executable so *file* is bound."))
  (System/exit 1))

(load-file (str script-dir "/common.bb"))

(require '[session-mycology.common :as c])

(defn usage []
  (println "Usage: sm-log.bb --task \"...\" [options]")
  (println "")
  (println "Appends an entry to .ημ/session-mycology/ledger.edn")
  (println "")
  (println "Options:")
  (println "  --task TEXT          one-line task summary (required)")
  (println "  --efficiency FLOAT   0..1 confidence path was near-minimal (default: 0.5)")
  (println "  --friction FLOAT     0..1 confidence work was harder than expected (default: 0.5)")
  (println "  --candidate FLOAT    0..1 confidence a skill would help (default: 0.0)")
  (println "  --spore FILE         spore filename if one was created")
  (println "  --receipt-refs TEXT  comma-separated receipt line hashes or timestamps")
  (println "  --note TEXT          one-line reflection")
  (System/exit 1))

(defn parse-float-safe [s default-val]
  (try
    (let [v (Double/parseDouble (str/trim (str s)))]
      (max 0.0 (min 1.0 v)))
    (catch Exception _ default-val)))

(defn parse-refs [s]
  (let [cleaned (str/trim (str s))]
    (if (or (empty? cleaned) (= cleaned "none") (= cleaned "nil"))
      []
      (->> (str/split cleaned #"[,\s]+")
           (map str/trim)
           (remove empty?)
           vec))))

(defn parse-spore [s]
  (let [cleaned (str/trim (str s))]
    (if (or (empty? cleaned) (= cleaned "none") (= cleaned "nil"))
      nil
      cleaned)))

(let [args (c/parse-key-value-args *command-line-args*)
      task (get args "--task")]

  (when (str/blank? task)
    (binding [*out* *err*]
      (println "Error: --task is required"))
    (usage))

  (let [root (or (c/find-project-root)
                 (do (binding [*out* *err*]
                       (println "Could not find project root containing .ημ/ or .git/"))
                     (System/exit 1)))
        ledger (c/ledger-edn-file root)]
    (.mkdirs (.getParentFile ledger))
    (let [eff (parse-float-safe (get args "--efficiency") 0.5)
          fric (parse-float-safe (get args "--friction") 0.5)
          cand (parse-float-safe (get args "--candidate") 0.0)
          spore (parse-spore (get args "--spore"))
          refs (parse-refs (get args "--receipt-refs"))
          note (or (get args "--note") "")
          entry {:ts (c/now-iso)
                 :session (c/session-id)
                 :task task
                 :p-efficiency eff
                 :p-friction fric
                 :p-skill-candidate cand
                 :spore spore
                 :receipt-refs refs
                 :note note}]
      (spit ledger (str (pr-str entry) "\n") :append true)
      (println "Appended to" (.getPath ledger)))))
