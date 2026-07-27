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
  (println "Usage: rr-last.bb [--kind KIND] [--limit N]")
  (System/exit 1))

(let [args (c/parse-key-value-args *command-line-args*)
      kind-filter (when-let [k (get args "--kind")]
                    (keyword (str/replace k #"^:" "")))
      limit-str (get args "--limit" "5")
      limit (try (Integer/parseInt limit-str)
                 (catch Exception _
                   (binding [*out* *err*]
                     (println "Invalid --limit:" limit-str))
                   (System/exit 1)))
      root (or (c/find-project-root)
               (do (binding [*out* *err*]
                     (println "Could not find project root containing .ημ/ or .git/"))
                     (System/exit 1)))
      receipt-path (c/receipt-file root)
      lines (c/read-lines receipt-path)
      receipts (map read-string lines)
      filtered (if kind-filter
                 (filter #(= (:kind %) kind-filter) receipts)
                 receipts)]
  (println "# Last receipts:" receipt-path)
  (doseq [r (take-last limit filtered)]
    (println (pr-str r))))
