#!/usr/bin/env bb

(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[clojure.edn :as edn])

(def script-dir
  (some-> *file* io/file .getParent))

(when-not script-dir
  (binding [*out* *err*]
    (println "Run this script as an executable so *file* is bound."))
  (System/exit 1))

(load-file (str script-dir "/common.bb"))

(require '[session-mycology.common :as c])

(defn usage []
  (println "Usage: sm-list.bb [--limit N] [--spores-only] [--ledger-only] [--raw]")
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
      raw? (contains? args "--raw")
      root (or (c/find-project-root)
               (do (binding [*out* *err*]
                     (println "Could not find project root containing .ημ/ or .git/"))
                   (System/exit 1)))
      sm-dir (c/session-mycology-dir root)
      ledger-edn (c/ledger-edn-file root)
      ledger-md (c/legacy-ledger-md-file root)
      spores-dir (c/spores-dir root)]

  (when-not (or spores-only ledger-only)
    (println "# Session Mycology: " root))

  (when (and (not spores-only) (or (.exists ledger-edn) (.exists ledger-md)))
    (println "\n## Recent ledger entries")
    (cond
      (.exists ledger-edn)
      (with-open [rdr (io/reader ledger-edn)]
        (let [entries (->> (line-seq rdr)
                           (remove str/blank?)
                           (map edn/read-string)
                           (take-last limit))]
          (if (seq entries)
            (doseq [e entries]
              (if raw?
                (println (pr-str e))
                (do
                  (println (str "- ts: " (:ts e)))
                  (println (str "  session: " (:session e)))
                  (println (str "  task: " (:task e)))
                  (println (str "  p-efficiency: " (:p-efficiency e)))
                  (println (str "  p-friction: " (:p-friction e)))
                  (println (str "  p-skill-candidate: " (:p-skill-candidate e)))
                  (println (str "  spore: " (or (:spore e) "none")))
                  (println (str "  receipt-refs: " (if (seq (:receipt-refs e))
                                                     (str/join ", " (:receipt-refs e))
                                                     "none")))
                  (println (str "  note: " (:note e)))
                  (when (:raw-annex e)
                    (println "  annex:\n   " (str/replace (:raw-annex e) #"\n" "\n    "))))))
            (println "  (none)"))))

      (.exists ledger-md)
      (let [content (slurp ledger-md)
            entries (->> (str/split content #"(?m)^- ts:")
                         (remove str/blank?)
                         (map #(str "- ts:" %))
                         (take-last limit))]
        (if (seq entries)
          (doseq [entry entries]
            (print entry))
          (println "  (none)")))))

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
