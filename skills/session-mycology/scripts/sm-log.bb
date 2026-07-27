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
  (println "Options:")
  (println "  --task TEXT          one-line task summary (required)")
  (println "  --efficiency FLOAT   0..1 confidence path was near-minimal (default: 0.5)")
  (println "  --friction FLOAT     0..1 confidence work was harder than expected (default: 0.5)")
  (println "  --candidate FLOAT    0..1 confidence a skill would help (default: 0.0)")
  (println "  --spore FILE         spore filename if one was created")
  (println "  --receipt-refs TEXT  comma-separated receipt line hashes or timestamps")
  (println "  --note TEXT          one-line reflection")
  (System/exit 1))

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
        ledger (io/file root ".ημ" "session-mycology" "ledger.md")]
    (.mkdirs (.getParentFile ledger))
    (let [eff (or (get args "--efficiency") "0.5")
          fric (or (get args "--friction") "0.5")
          cand (or (get args "--candidate") "0.0")
          spore (get args "--spore" "none")
          refs (get args "--receipt-refs" "none")
          note (get args "--note" "")]
      (spit ledger
            (str "- ts: " (c/now-iso) "\n"
                 "  session: " (c/session-id) "\n"
                 "  task: " task "\n"
                 "  p-efficiency: " eff "\n"
                 "  p-friction: " fric "\n"
                 "  p-skill-candidate: " cand "\n"
                 "  spore: " spore "\n"
                 "  receipt-refs: " refs "\n"
                 "  note: " note "\n")
            :append true)
      (println "Appended to" (.getPath ledger)))))
