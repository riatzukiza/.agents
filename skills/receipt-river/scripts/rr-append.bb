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

(require '[receipt-river.common :as c])

(defn usage []
  (println "Usage: rr-append.bb --kind KIND --origin ORIGIN [options]")
  (println "")
  (println "Required:")
  (println "  --kind KIND          :observation, :test-run, :build, :decision, :push-truth, :catalog, etc.")
  (println "  --origin TEXT        path or task reference")
  (println "")
  (println "Optional:")
  (println "  --owner TEXT         default: receipt-river")
  (println "  --dod TEXT           definition of done affected")
  (println "  --pi TEXT            pi/interpreter context")
  (println "  --host TEXT          default: local")
  (println "  --manifest TEXT      comma-separated changed files")
  (println "  --refs TEXT          related commits, issues, session ids")
  (println "  --note TEXT          human-readable summary")
  (println "  --tests TEXT         test command(s) and result summary")
  (println "  --decisions TEXT     decision record")
  (println "  --drift TEXT         observed deviation from plan")
  (System/exit 1))

(let [args (c/parse-key-value-args *command-line-args*)
      kind (get args "--kind")
      origin (get args "--origin")]

  (when (or (str/blank? kind) (str/blank? origin))
    (binding [*out* *err*]
      (println "Error: --kind and --origin are required"))
    (usage))

  (let [root (or (c/find-project-root)
                 (do (binding [*out* *err*]
                       (println "Could not find project root containing .ημ/ or .git/"))
                     (System/exit 1)))
        receipt-path (c/receipt-file root)
        owner (or (get args "--owner") "receipt-river")
        host (or (get args "--host") "local")
        receipt {:ts (c/now-iso)
                 :kind (keyword (str/replace kind #"^:" ""))
                 :origin origin
                 :owner owner
                 :dod (get args "--dod" "")
                 :pi (get args "--pi" "")
                 :host host
                 :manifest (get args "--manifest" "none")
                 :refs (get args "--refs" "none")
                 :note (get args "--note" "")
                 :tests (get args "--tests" "")
                 :decisions (get args "--decisions" "")
                 :drift (get args "--drift" "")}
        receipt (into {} (filter (fn [[_ v]] (not= v "")) receipt))]
    (.mkdirs (.getParentFile (io/file receipt-path)))
    (spit receipt-path (str (pr-str receipt) "\n") :append true)
    (println "Appended to" receipt-path)))
