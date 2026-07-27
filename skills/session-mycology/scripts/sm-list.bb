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
  (println "Usage: sm-list.bb [--limit N] [--spores-only] [--ledger-only]")
  (System/exit 1))

(let [args (c/parse-key-value-args *command-line-args*)
      limit-str (get args "--limit" "20")
      limit (try (Integer/parseInt limit-str)
                 (catch Exception _
                   (binding [*out* *err*]
                     (println "Invalid --limit:" limit-str))
                   (System/exit 1)))
      spores-only (contains? args "--spores-only")
      ledger-only (contains? args "--ledger-only")
      root (or (c/find-project-root)
               (do (binding [*out* *err*]
                     (println "Could not find project root containing .ημ/ or .git/"))
                   (System/exit 1)))
      sm-dir (io/file root ".ημ" "session-mycology")
      ledger (io/file sm-dir "ledger.md")
      spores-dir (io/file sm-dir "spores")]

  (when-not (or spores-only ledger-only)
    (println "# Session Mycology: " root))

  (when (and (not spores-only) (.exists ledger))
    (println "\n## Recent ledger entries")
    (let [content (slurp ledger)
          entries (->> (str/split content #"(?m)^- ts:")
                       (remove str/blank?)
                       (map #(str "- ts:" %))
                       (take-last limit))]
      (if (seq entries)
        (doseq [entry entries]
          (print entry))
        (println "  (none)"))))

  (when (and (not ledger-only) (.exists spores-dir))
    (println "\n## Spores")
    (let [spores (->> (.listFiles spores-dir)
                      (filter #(.endsWith (.getName %) ".md"))
                      (sort-by #(.getName %))
                      (reverse)
                      (take limit))]
      (if (seq spores)
        (doseq [s spores]
          (println " -" (.getName s)))
        (println "  (none)")))))
