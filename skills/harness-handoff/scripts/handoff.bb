#!/usr/bin/env bb
(ns handoff
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.process :refer [sh]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]
           [java.time Instant LocalDateTime ZoneId]
           [java.time.format DateTimeFormatter]))

;; ---------------------------------------------------------------------------
;; helpers

(defn now-iso [] (str (Instant/now)))

(defn now-stamp []
  (.format (DateTimeFormatter/ofPattern "yyyyMMddHHmmss")
           (LocalDateTime/now (ZoneId/systemDefault))))

(defn sha256 [^String s]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" %) digest))))

(defn die [& msgs]
  (binding [*out* *err*] (apply println msgs))
  (System/exit 1))

(defn git [& args]
  (let [{:keys [exit out err]} (apply sh {:err :string} "git" args)]
    (when-not (zero? exit)
      (die "git" (str/join " " args) "failed:" (str/trim err)))
    (str/trim out)))

(defn git-optional [& args]
  (let [{:keys [exit out]} (apply sh {:err :string} "git" args)]
    (when (zero? exit) (str/trim out))))

(defn repo-root []
  (git "rev-parse" "--show-toplevel"))

(defn repo-origin []
  (git-optional "remote" "get-url" "origin"))

(defn repo-branch []
  (or (git-optional "rev-parse" "--abbrev-ref" "HEAD") "unknown"))

(defn repo-head []
  (or (git-optional "rev-parse" "--short" "HEAD") "unknown"))

(defn handoff-file [root]
  (str (fs/path root ".handoff.edn")))

(defn home-dir []
  (or (System/getenv "HANDOFF_HOME") (str (fs/home))))

(defn sidecar-dir []
  (let [xdg (or (System/getenv "XDG_DATA_HOME")
                (str (fs/path (home-dir) ".local" "share")))]
    (str (fs/path xdg "handoff"))))

(defn sidecar-file [origin path]
  (str (fs/path (sidecar-dir)
                (str (sha256 (or origin (str "path:" path))) ".edn"))))

(defn append-line! [file m]
  (fs/create-dirs (fs/parent file))
  (spit file (str (pr-str m) "\n") :append true))

(defn read-edn-lines [file]
  (if (fs/exists? file)
    (->> (str/split-lines (slurp file))
         (remove str/blank?)
         (map edn/read-string)
         vec)
    []))

(defn parse-harness [s]
  (let [k (keyword (str/replace (str s) #"^:" ""))]
    (when-not (#{:opencode :codex :claude} k)
      (die "invalid --harness" (pr-str s) "(expected :opencode|:codex|:claude)"))
    k))

(defn parse-state [s]
  (let [k (keyword (str/replace (str s) #"^:" ""))]
    (when-not (#{:in-progress :blocked :done} k)
      (die "invalid --state" (pr-str s) "(expected :in-progress|:blocked|:done)"))
    k))

(defn parse-artifacts [s]
  (when (and s (not (str/blank? s)))
    (mapv str/trim (str/split s #","))))

;; ---------------------------------------------------------------------------
;; record

(defn cmd-record [{:keys [opts]}]
  (let [{:keys [harness session-id task state summary next artifacts notes]} opts]
    (when-not harness (die "missing --harness"))
    (when-not session-id (die "missing --session-id"))
    (when-not task (die "missing --task"))
    (when-not summary (die "missing --summary"))
    (when-not next (die "missing --next"))
    (let [root (repo-root)
          origin (repo-origin)
          entry (cond-> {:handoff/v 1
                         :ts (now-iso)
                         :harness (parse-harness harness)
                         :session-id (str session-id)
                         :repo {:origin origin
                                :branch (repo-branch)
                                :head (repo-head)}
                         :task (str task)
                         :state (if state (parse-state state) :in-progress)
                         :summary (str summary)
                         :next (str next)}
                  (parse-artifacts artifacts) (assoc :artifacts (parse-artifacts artifacts))
                  notes (assoc :notes (str notes)))]
      (append-line! (handoff-file root) entry)
      (append-line! (sidecar-file origin root)
                    {:ts (now-iso)
                     :origin origin
                     :path (str root)
                     :last-session {:harness (:harness entry)
                                    :id (:session-id entry)}})
      (println "recorded:" (handoff-file root))
      (println (pr-str entry)))))

;; ---------------------------------------------------------------------------
;; read / locate

(defn print-entry [{:keys [ts harness session-id state task summary next]}]
  (println (str "[" ts "] " (name harness) " (" session-id ") " (name state)))
  (println "  task:   " task)
  (println "  summary:" summary)
  (println "  next:   " next))

(defn moved-notice [origin root]
  (when origin
    (let [entries (->> (sidecar-file origin root)
                       (read-edn-lines)
                       (filter #(= origin (:origin %)))
                       (remove #(= (str root) (:path %))))]
      (when (seq entries)
        (let [{:keys [path ts last-session]} (last entries)]
          (println "no .handoff.edn here, but the sidecar index knows this origin at a different path:")
          (println "  old path:" path)
          (println "  seen:    " ts)
          (println "  last session there:" (pr-str last-session))
          (println "  -> the repo was probably moved. Run: bb handoff.bb relocate --from"
                   path "--to" (str root)))))))

(defn cmd-read [{:keys [opts]}]
  (let [root (repo-root)
        f (handoff-file root)
        entries (read-edn-lines f)
        limit (some-> (:limit opts) str parse-long)]
    (if (seq entries)
      (doseq [e (if limit (take-last limit entries) entries)]
        (print-entry e))
      (do
        (println "no .handoff.edn at" f)
        (moved-notice (repo-origin) root)))))

(defn cmd-locate [_]
  (let [root (repo-root)
        origin (repo-origin)
        f (sidecar-file origin root)
        entries (read-edn-lines f)]
    (if (seq entries)
      (doseq [{:keys [ts origin path last-session]} entries]
        (println (str "[" ts "] " path "  origin=" (or origin "nil")
                      "  last=" (pr-str last-session))))
      (println "no sidecar entries at" f))))

;; ---------------------------------------------------------------------------
;; relocate

(defn encode-cwd [p]
  (str/replace p "/" "-"))

(defn relocate-claude [old new]
  (let [home (home-dir)
        src (str (fs/path home ".claude" "projects" (encode-cwd old)))
        dst (str (fs/path home ".claude" "projects" (encode-cwd new)))]
    (cond
      (fs/exists? dst)
      (do (println "  claude: target" dst "already exists, skipping") :skipped)

      (fs/exists? src)
      (do (fs/move src dst)
          (println "  claude: moved" src "->" dst)
          :moved)

      :else
      (do (println "  claude: no project dir at" src) :absent))))

(defn relocate-codex [old new]
  (let [cfg (str (fs/path (home-dir) ".codex" "config.toml"))]
    (if-not (fs/exists? cfg)
      (do (println "  codex: no config.toml at" cfg) :absent)
      (let [content (slurp cfg)
            header (str "[projects.\"" old "\"]")
            replacement (str "[projects.\"" new "\"]")]
        (if (str/includes? content header)
          (let [bak (str cfg ".bak-" (now-stamp))]
            (io/copy (io/file cfg) (io/file bak))
            (spit cfg (str/replace content header replacement))
            (println "  codex: rewrote" header "->" replacement "(backup:" bak ")")
            :moved)
          (do (println "  codex: no trust entry for" header) :absent))))))

(defn opencode-running? []
  (zero? (:exit (sh {:err :string :out :string} "pgrep" "-f" "opencode"))))

(defn relocate-opencode [old new]
  (let [db (str (fs/path (home-dir) ".local" "share" "opencode" "opencode.db"))]
    (cond
      (not (fs/exists? db))
      (do (println "  opencode: no db at" db) :absent)

      (opencode-running?)
      (do (println "  opencode: REFUSED - an opencode process is running. Stop it and re-run relocate.")
          :refused)

      :else
      (let [bak (str db ".bak-" (now-stamp))]
        (io/copy (io/file db) (io/file bak))
        (let [q1 (format "UPDATE project SET worktree='%s' WHERE worktree='%s'; SELECT changes();" new old)
              q2 (format (str "UPDATE session SET directory=replace(directory,'%s','%s'),"
                              " path=replace(path,'%s','%s')"
                              " WHERE directory LIKE '%s%%'"
                              " AND project_id IN (SELECT id FROM project WHERE worktree='%s');"
                              " SELECT changes();")
                         old new old new old new)
              r1 (sh {:err :string} "sqlite3" db q1)]
          (when-not (zero? (:exit r1))
            (throw (ex-info "sqlite3 project update failed" {:err (:err r1)})))
          (let [n1 (str/trim (:out r1))
                r2 (sh {:err :string} "sqlite3" db q2)]
            (when-not (zero? (:exit r2))
              (throw (ex-info "sqlite3 session update failed" {:err (:err r2)})))
            (let [n2 (str/trim (:out r2))]
              (println "  opencode: db backed up to" bak)
              (println "  opencode: project rows changed:" n1 " session rows changed:" n2)
              :moved)))))))

(defn cmd-relocate [{:keys [opts]}]
  (let [{:keys [from to]} opts]
    (when-not from (die "missing --from OLD"))
    (when-not to (die "missing --to NEW"))
    (when-not (fs/exists? to)
      (die "target path does not exist:" to))
    (let [results (atom {})]
      (println "== relocate" from "->" to)
      (doseq [[label f] [["claude" relocate-claude]
                         ["codex" relocate-codex]
                         ["opencode" relocate-opencode]]]
        (println (str "-- " label))
        (try
          (swap! results assoc label (f from to))
          (catch Exception e
            (println (str "  " label ": FAILED - " (ex-message e)))
            (swap! results assoc label :failed))))
      (println "-- sidecar")
      (try
        (let [root (git "rev-parse" "--show-toplevel")
              origin (repo-origin)]
          (append-line! (sidecar-file origin root)
                        {:ts (now-iso)
                         :origin origin
                         :path (str (fs/absolutize to))
                         :relocated-from from
                         :last-session nil})
          (println "  sidecar: appended new path entry")
          (swap! results assoc "sidecar" :updated))
        (catch Exception e
          (println "  sidecar: FAILED -" (ex-message e))
          (swap! results assoc "sidecar" :failed)))
      (println)
      (println "== summary")
      (doseq [[k v] @results]
        (println (format "  %-10s %s" k (name v))))
      (println)
      (println "manual attention:")
      (println "  - internal cwd fields inside old transcript/rollout files are historical records; do NOT rewrite them.")
      (println "  - re-open sessions from the NEW path; resume keys off the new location."))))

;; ---------------------------------------------------------------------------
;; main

(def table
  [{:cmds ["record"] :fn cmd-record
    :args->opts [:harness :session-id :task :state :summary :next :artifacts :notes]}
   {:cmds ["read"] :fn cmd-read
    :args->opts [:limit]}
   {:cmds ["locate"] :fn cmd-locate}
   {:cmds ["relocate"] :fn cmd-relocate
    :args->opts [:from :to]}])

(defn -main [& args]
  (cli/dispatch table args))

(if (seq *command-line-args*)
  (apply -main *command-line-args*)
  (do (println "usage: bb handoff.bb <command> [opts]")
      (println "  record   --harness H --session-id ID --task T --state S --summary S --next N [--artifacts a,b] [--notes x]")
      (println "  read     [--limit N]")
      (println "  locate")
      (println "  relocate --from OLD --to NEW")))
