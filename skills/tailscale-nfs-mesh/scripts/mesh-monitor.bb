#!/usr/bin/env bb

(ns mesh-monitor
  "Poll Tailscale status across the mesh and record changes to an append-only ledger."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [babashka.process :as proc]))

;; ---------------------------------------------------------------------------
;; Config
;; ---------------------------------------------------------------------------

(def ledger-path
  (or (System/getenv "MESH_LEDGER")
      "/home/err/networks/tailscale/ledger/tailscale-mesh.edn"))

(def mesh-hosts
  [{:name "stealth" :ssh nil}        ;; local, no SSH needed
   {:name "knoxx"   :ssh "knoxx-ts"}
   {:name "yoga"    :ssh "yoga-ts"}])

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn now-iso []
  (.toString (java.time.Instant/now)))

(defn ssh-exec
  "Run a command via SSH. Returns trimmed stdout or nil."
  [target cmd]
  (try
    (let [result (if target
                   (proc/shell {:out :string :err :string} "ssh" "-o" "ConnectTimeout=5" target cmd)
                   (proc/shell {:out :string :err :string} "bash" "-c" cmd))]
      (when (zero? (:exit result))
        (str/trim (:out result))))
    (catch Exception _ nil)))

(defn parse-tailscale-status
  "Parse `tailscale status` output into a set of member maps."
  [output]
  (when output
    (let [lines (str/split-lines output)]
      (set
       (for [line lines
             :when (re-matches #"\d+\.\d+\.\d+\.\d+\s+.*" line)]
         (let [parts (str/split line #"\s+")
               ip    (first parts)
               name  (second parts)
               status (last parts)]
           {:ip ip :name name :online (not= status "offline")}))))))

(defn read-ledger
  "Read existing ledger entries."
  [path]
  (if (.exists (io/file path))
    (->> (slurp path)
         str/split-lines
         (filter #(not (str/blank? %)))
         (mapv edn/read-string))
    []))

(defn write-entry!
  "Append an EDN entry to the ledger."
  [path entry]
  (.mkdirs (.getParentFile (io/file path)))
  (spit path (str (pr-str entry) "\n") :append true))

(defn diff-members
  "Compare old and new member sets. Returns {:joins [...] :leaves [...] :changes [...]}."
  [old-members new-members]
  (let [old-names (set (map :name old-members))
        new-names (set (map :name new-members))
        joins  (filter #(not (old-names (:name %))) new-members)
        leaves (filter #(not (new-names (:name %))) old-members)
        ;; Detect online/offline changes
        old-map (into {} (map (juxt :name :online) old-members))
        new-map (into {} (map (juxt :name :online) new-members))
        changes (for [name (clojure.set/intersection old-names new-names)
                      :when (not= (get old-map name) (get new-map name))]
                  {:name name
                   :was-online (get old-map name)
                   :now-online (get new-map name)})]
    {:joins (vec joins)
     :leaves (vec leaves)
     :changes (vec changes)}))

;; ---------------------------------------------------------------------------
;; Commands
;; ---------------------------------------------------------------------------

(defn cmd-snapshot
  "Take a full mesh snapshot and record it."
  []
  (let [        entries (read-ledger ledger-path)
        last-snapshot (last (filter #(= :snapshot (:kind %)) entries))
        prev-members (or (:members last-snapshot) #{})
        ;; Poll all hosts
        results (for [{:keys [name ssh]} mesh-hosts]
                  (let [output (ssh-exec ssh "tailscale status --self=true 2>/dev/null || true")]
                    {:host name :members (parse-tailscale-status output)}))
        all-members (apply clojure.set/union (map :members results))
        diff (diff-members prev-members all-members)
        ts (now-iso)]
    ;; Record snapshot
    (write-entry! ledger-path
      {:ts ts
       :kind :snapshot
       :origin "mesh-monitor"
       :owner "mesh-monitor"
       :host "knoxx"
       :members all-members
       :host-details (vec results)
       :note (str "Mesh snapshot: " (count all-members) " members")})
    ;; Record events
    (doseq [join (:joins diff)]
      (write-entry! ledger-path
        {:ts ts
         :kind :member-join
         :origin "mesh-monitor"
         :owner "mesh-monitor"
         :host "knoxx"
         :member join
         :note (str (:name join) " joined the mesh (" (:ip join) ")")}))
    (doseq [leave (:leaves diff)]
      (write-entry! ledger-path
        {:ts ts
         :kind :member-leave
         :origin "mesh-monitor"
         :owner "mesh-monitor"
         :host "knoxx"
         :member leave
         :note (str (:name leave) " left the mesh (" (:ip leave) ")")}))
    (doseq [change (:changes diff)]
      (write-entry! ledger-path
        {:ts ts
         :kind :member-change
         :origin "mesh-monitor"
         :owner "mesh-monitor"
         :host "knoxx"
         :member change
         :note (str (:name change) " went from online=" (:was-online change) " to online=" (:now-online change))}))
    ;; Print summary
    (println (str "Snapshot recorded: " (count all-members) " members"))
    (when (seq (:joins diff))
      (println (str "  Joins: " (str/join ", " (map :name (:joins diff))))))
    (when (seq (:leaves diff))
      (println (str "  Leaves: " (str/join ", " (map :name (:leaves diff))))))
    (when (seq (:changes diff))
      (println (str "  Changes: " (str/join ", " (map :name (:changes diff))))))))

(defn cmd-log
  "Print the last N ledger entries."
  [n]
  (let [entries (read-ledger ledger-path)
        recent (take-last (or n 20) entries)]
    (doseq [entry recent]
      (println (str (:ts entry) " " (:kind entry) " " (:note entry ""))))))

(defn cmd-members
  "Print current known members."
  []
  (let [entries (read-ledger ledger-path)
        last-snapshot (last (filter #(= :snapshot (:kind %)) entries))]
    (if last-snapshot
      (do
        (println (str "Last snapshot: " (:ts last-snapshot)))
        (doseq [m (:members last-snapshot)]
          (println (str "  " (:name m) " " (:ip m) " online=" (:online m)))))
      (println "No snapshots recorded yet"))))

(defn cmd-status
  "Show ledger status."
  []
  (let [entries (read-ledger ledger-path)
        snapshots (filter #(= :snapshot (:kind %)) entries)
        joins (filter #(= :member-join (:kind %)) entries)
        leaves (filter #(= :member-leave (:kind %)) entries)
        changes (filter #(= :member-change (:kind %)) entries)]
    (println (str "Ledger: " ledger-path))
    (println (str "Total entries: " (count entries)))
    (println (str "Snapshots: " (count snapshots)))
    (println (str "Joins: " (count joins)))
    (println (str "Leaves: " (count leaves)))
    (println (str "Changes: " (count changes)))
    (when (seq entries)
      (println (str "Last entry: " (:ts (last entries)))))))

(defn usage []
  (str/join "\n"
    ["mesh-monitor.bb - Tailscale mesh monitoring ledger"
     ""
     "Usage:"
     "  mesh-monitor.bb snapshot    Take a mesh snapshot and record changes"
     "  mesh-monitor.bb log [N]     Show last N ledger entries (default: 20)"
     "  mesh-monitor.bb members     Show current known members"
     "  mesh-monitor.bb status      Show ledger statistics"
     ""
     "Ledger: " ledger-path]))

(defn -main [& args]
  (let [cmd (first args)]
    (case cmd
      "snapshot" (cmd-snapshot)
      "log"      (cmd-log (when-let [n (second args)] (parse-long n)))
      "members"  (cmd-members)
      "status"   (cmd-status)
      (println (usage)))))

(apply -main *command-line-args*)
