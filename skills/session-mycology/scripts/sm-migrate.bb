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
  (println "Usage: sm-migrate.bb [options]")
  (println "")
  (println "Migrates .ημ/session-mycology/ledger.md to ledger.edn")
  (println "")
  (println "Options:")
  (println "  --source FILE   Path to source ledger.md (default: discovered .ημ/session-mycology/ledger.md)")
  (println "  --target FILE   Path to target ledger.edn (default: discovered .ημ/session-mycology/ledger.edn)")
  (println "  --backup        Rename source ledger.md to ledger.md.bak upon successful migration")
  (println "  --force         Overwrite target file if it already exists")
  (println "  --dry-run       Parse and validate records without writing")
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

(defn parse-entry-block [raw]
  (let [lines (str/split-lines raw)
        ts (str/trim (first lines))
        data (atom {:ts ts :note "" :receipt-refs [] :spore nil})
        current-key (atom nil)
        annex (atom [])]
    (doseq [line (rest lines)]
      (if-let [[_ k v] (re-matches #"^\s+([a-zA-Z0-9_-]+):\s*(.*)" line)]
        (let [kw (keyword k)
              val (str/trim v)]
          (reset! current-key kw)
          (case kw
            :p-efficiency (swap! data assoc :p-efficiency (parse-float-safe val 0.5))
            :p-friction (swap! data assoc :p-friction (parse-float-safe val 0.5))
            :p-skill-candidate (swap! data assoc :p-skill-candidate (parse-float-safe val 0.0))
            :spore (swap! data assoc :spore (parse-spore val))
            :receipt-refs (swap! data assoc :receipt-refs (parse-refs val))
            (swap! data assoc kw val)))
        (if (and @current-key (= @current-key :note) (re-matches #"^\s{4,}.*" line))
          (swap! data update :note #(str % (if (seq %) " " "") (str/trim line)))
          (when (not (str/blank? line))
            (swap! annex conj line)))))
    (let [res @data]
      (if (seq @annex)
        (assoc res :raw-annex (str/join "\n" @annex))
        res))))

(let [args (c/parse-key-value-args *command-line-args*)
      help? (or (contains? args "--help") (contains? args "-h"))]
  (when help? (usage))

  (let [root (c/find-project-root)
        source-path (get args "--source")
        target-path (get args "--target")
        backup? (contains? args "--backup")
        force? (contains? args "--force")
        dry-run? (contains? args "--dry-run")
        source-file (if source-path
                      (io/file source-path)
                      (if root
                        (c/legacy-ledger-md-file root)
                        nil))
        target-file (if target-path
                      (io/file target-path)
                      (if root
                        (c/ledger-edn-file root)
                        nil))]

    (when (nil? source-file)
      (binding [*out* *err*]
        (println "Error: Project root not found and no --source specified."))
      (System/exit 1))

    (when (not (.exists source-file))
      (println (str "Source file does not exist: " (.getPath source-file)))
      (println "Nothing to migrate.")
      (System/exit 0))

    (when (and (.exists target-file) (not force?) (not dry-run?))
      (binding [*out* *err*]
        (println (str "Error: Target file already exists: " (.getPath target-file)))
        (println "Pass --force to overwrite."))
      (System/exit 1))

    (println (str "Reading " (.getPath source-file) "..."))
    (let [content (slurp source-file)
          raw-entries (remove str/blank? (str/split content #"(?m)^- ts:\s*"))
          records (mapv parse-entry-block raw-entries)]

      (println (str "Parsed " (count records) " entries from ledger.md"))
      (let [with-annex (filter :raw-annex records)]
        (when (seq with-annex)
          (println (str "  (" (count with-annex) " entries preserved historical annexed Markdown text)"))))

      (if dry-run?
        (do
          (println "\n[DRY RUN] Would write to:" (.getPath target-file))
          (when (seq records)
            (println "\nFirst entry preview:")
            (println (pr-str (first records)))
            (println "\nLast entry preview:")
            (println (pr-str (last records))))
          (println "\nDry run complete. No files written."))
        (do
          (.mkdirs (.getParentFile target-file))
          (with-open [w (io/writer target-file)]
            (doseq [r records]
              (.write w (str (pr-str r) "\n"))))
          (println (str "Wrote " (count records) " EDN lines to " (.getPath target-file)))

          ;; Verification pass
          (print "Verifying target EDN readability...")
          (flush)
          (with-open [rdr (io/reader target-file)]
            (let [read-back (mapv edn/read-string (line-seq rdr))]
              (if (= (count read-back) (count records))
                (println " PASS (exact count and EDN structure verified).")
                (do
                  (binding [*out* *err*]
                    (println (str "\nVerification FAILED: expected " (count records)
                                 " records, but read back " (count read-back))))
                  (System/exit 1)))))

          (when backup?
            (let [backup-file (io/file (.getParentFile source-file) "ledger.md.bak")]
              (if (.renameTo source-file backup-file)
                (println (str "Archived source file to " (.getPath backup-file)))
                (binding [*out* *err*]
                  (println (str "Warning: Failed to rename source file to " (.getPath backup-file)))))))

          (println "Migration completed successfully."))))))
