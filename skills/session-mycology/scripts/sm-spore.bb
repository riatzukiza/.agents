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
  (println "Usage: sm-spore.bb --slug \"...\" --task \"...\" [options]")
  (println "")
  (println "Options:")
  (println "  --slug SLUG          kebab-case spore slug (required)")
  (println "  --task TEXT          one-line source task summary (required)")
  (println "  --problem TEXT       what was harder than expected")
  (println "  --pattern TEXT       what repeated or could repeat")
  (println "  --better-path TEXT   what the next agent should do differently")
  (println "  --efficiency FLOAT   default: 0.5")
  (println "  --friction FLOAT     default: 0.5")
  (println "  --candidate FLOAT    default: 0.0")
  (println "  --receipt-refs TEXT  comma-separated receipt refs")
  (println "  --dry-run            print the spore without writing")
  (System/exit 1))

(let [args (c/parse-key-value-args *command-line-args*)
      slug (get args "--slug")
      task (get args "--task")]

  (when (or (str/blank? slug) (str/blank? task))
    (binding [*out* *err*]
      (println "Error: --slug and --task are required"))
    (usage))

  (let [root (or (c/find-project-root)
                 (do (binding [*out* *err*]
                       (println "Could not find project root containing .ημ/ or .git/"))
                     (System/exit 1)))
        spores-dir (c/spores-dir root)
        filename (str (c/ts-slug) "-" (c/kebab-slug slug) ".md")
        spore-file (io/file spores-dir filename)
        eff (or (get args "--efficiency") "0.5")
        fric (or (get args "--friction") "0.5")
        cand (or (get args "--candidate") "0.0")
        problem (get args "--problem" "")
        pattern (get args "--pattern" "")
        better-path (get args "--better-path" "")
        refs (get args "--receipt-refs" "none")
        content (str "---\n"
                     "status: incubating\n"
                     "created: " (c/now-iso) "\n"
                     "source-session: " (c/session-id) "\n"
                     "source-task: " task "\n"
                     "p-efficiency: " eff "\n"
                     "p-friction: " fric "\n"
                     "p-skill-candidate: " cand "\n"
                     "promoted-to: \"\"\n"
                     "rejected-reason: \"\"\n"
                     "---\n\n"
                     "## Problem\n" problem "\n\n"
                     "## Pattern\n" pattern "\n\n"
                     "## Candidate skill outline\n"
                     "- Name suggestion\n"
                     "- Trigger phrases\n"
                     "- Key steps or rules\n"
                     "- Anti-patterns to avoid\n\n"
                     "## Better path\n" better-path "\n\n"
                     "## Receipt refs\n- " refs "\n")]
    (if (get args "--dry-run")
      (println content)
      (do (spit spore-file content)
          (println "Created" (.getPath spore-file))))))
