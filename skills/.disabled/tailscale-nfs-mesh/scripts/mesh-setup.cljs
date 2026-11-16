#!/usr/bin/env nbb

(ns mesh-setup
  (:require [clojure.string :as str]
            [promesa.core :as p]
            ["child_process" :as cp]
            ["fs" :as fs]
            ["readline" :as rl]))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn env [k] (aget (.-env js/process) k))

(defn log! [msg]
  (println (str "\033[36m[mesh]\033[0m " msg)))

(defn warn! [msg]
  (.warn js/console (str "\033[33m[mesh]\033[0m " msg)))

(defn fail! [msg]
  (.error js/console (str "\033[31m[mesh]\033[0m " msg))
  (.exit js/process 1))

(defn notify! [title body & [{:keys [urgency]}]]
  (try
    (cp/execFileSync "notify-send"
                     (clj->js (cond-> ["notify-send"]
                                urgency (conj (str "-u" urgency))
                                :always (conj title body)))
                     #js {:stdio "ignore"})
    (catch js/Error _ nil)))

(defn open-browser! [url]
  (try
    (cp/execFileSync "xdg-open" #js [url] #js {:stdio "ignore" :detached true})
    (catch js/Error _ nil)))

(defn ssh-exec
  "Run a command over SSH. Returns trimmed stdout, nil on failure."
  [target cmd & [{:keys [timeout] :or {timeout 30000}}]]
  (let [parts (into-array (str/split target #"\s+"))]
    ;; Append the command as the last argument
    (let [args (into [] (concat parts [cmd]))]
      (try
        (let [result (cp/execFileSync "ssh" (into-array args)
                                      #js {:stdio "pipe" :timeout timeout})]
          (str/trim (.toString result "utf8")))
        (catch js/Error e
          (warn! (str "SSH failed on " target ": " (.-message e)))
          nil)))))

(defn ssh-exec!
  "Run a command over SSH. Throws on failure."
  [target cmd & [{:keys [timeout] :or {timeout 30000}}]]
  (let [result (ssh-exec target cmd {:timeout timeout})]
    (when (nil? result)
      (fail! (str "SSH command failed: " cmd)))
    result))

(defn local-exec!
  "Run a shell command locally."
  [cmd & [{:keys [timeout] :or {timeout 30000}}]]
  (try
    (let [result (cp/execFileSync "bash" #js ["-c" cmd]
                                  #js {:stdio "pipe" :timeout timeout})]
      (str/trim (.toString result "utf8")))
    (catch js/Error e
      (warn! (str "Local command failed: " cmd))
      nil)))

;; ---------------------------------------------------------------------------
;; Default config: 3-machine mesh
;; ---------------------------------------------------------------------------

(def default-config
  {:local-host  "stealth"
   :local-ts-ip "100.77.244.9"
   :local-share "/home/err/devel"
   :hosts
   [{:name    "knoxx"
     :ssh     "root@157.245.125.134"
     :ts-ip   "100.103.156.0"
     :share   "/home/err/spaces"}
    {:name    "yoga"
     :ssh     "192.168.12.68"
     :ts-ip   "100.105.116.31"
     :share   "/home/err/devel"}]})

;; ---------------------------------------------------------------------------
;; Phase functions
;; ---------------------------------------------------------------------------

(defn phase-install-tailscale [hosts]
  (log! "\n--- Phase 1: Install Tailscale ---")
  (doseq [{:keys [ssh name]} hosts]
    (log! (str "Installing Tailscale on " name "..."))
    (ssh-exec! ssh "curl -fsSL https://tailscale.com/install.sh | sh" {:timeout 120000})
    (log! (str "Tailscale installed on " name))))

(defn phase-start-tailscale [hosts]
  (log! "\n--- Phase 2: Start Tailscale ---")
  (let [auth-urls
        (for [{:keys [ssh name]} hosts]
          (do
            (log! (str "Starting Tailscale on " name "..."))
            (let [output (ssh-exec! ssh "sudo tailscale up --ssh --accept-routes 2>&1 || true" {:timeout 15000})
                  lines  (str/split-lines output)
                  url    (first (filter #(str/starts-with? (str/trim %) "https://login.tailscale.com") lines))]
              (when url
                (log! (str "Auth URL for " name ": " (str/trim url)))
                (str/trim url)))))]
    (when (seq (remove nil? auth-urls))
      (notify! "Tailscale Auth Required"
               (str "Opening browser for " (count (remove nil? auth-urls)) " machine(s)")
               {:urgency "critical"})
      (doseq [url (remove nil? auth-urls)]
        (js/setTimeout #(open-browser! url) 2000))
      (log! "\n*** Approve the Tailscale auth URLs in your browser ***")
      (log! "*** Press Enter when all machines are approved ***")
      ;; Wait for user input via readline
      (p/create
       (fn [resolve]
         (let [reader (rl/createInterface #js {:input (.-stdin js/process)
                                               :output (.-stdout js/process)})]
           (.once reader "line"
                  (fn [_]
                    (.close reader)
                    (resolve nil)))))))))

(defn phase-wait-tailscale [hosts]
  (log! "\n--- Phase 3: Wait for Tailscale ---")
  (doseq [{:keys [ssh name]} hosts]
    (log! (str "Waiting for Tailscale on " name "..."))
    (let [check (fn check [attempts]
                  (if (> attempts 30)
                    (warn! (str "Timeout waiting for Tailscale on " name))
                    (let [output (ssh-exec! ssh "tailscale status --self=true 2>/dev/null || true")]
                      (if (and output (str/includes? output name))
                        (log! (str "Tailscale connected on " name))
                        (.then (p/delay 2000)
                               (fn [_] (check (inc attempts))))))))]
      (check 0))))

(defn phase-setup-hosts [config]
  (log! "\n--- Phase 4: Configure /etc/hosts ---")
  (let [{:keys [hosts local-host local-ts-ip]} config
        all-hosts (concat [{:name local-host :ts-ip local-ts-ip}] hosts)]
    ;; Add entries on remote hosts
    (doseq [{:keys [ssh name]} hosts]
      (doseq [{:keys [name peer-name ts-ip]} (for [p all-hosts :when (not= (:name p) name)] p)]
        (let [entry (str ts-ip " " peer-name "-ts")]
          (log! (str "Adding to " name ": " entry))
          (ssh-exec! ssh (str "grep -q '" peer-name "-ts' /etc/hosts || echo '" entry "' | sudo tee -a /etc/hosts")))))))

(defn phase-install-nfs [hosts]
  (log! "\n--- Phase 5: Install NFS ---")
  (doseq [{:keys [ssh name]} hosts]
    (log! (str "Installing NFS on " name "..."))
    (ssh-exec! ssh "apt-get update -qq && apt-get install -y -qq nfs-kernel-server nfs-common" {:timeout 120000})
    (log! (str "NFS installed on " name))))

(defn phase-create-dirs [config]
  (log! "\n--- Phase 6: Create Mount Directories ---")
  (let [{:keys [hosts local-host]} config
        all-hosts (concat [{:name local-host}] hosts)]
    ;; Remote dirs
    (doseq [{:keys [ssh name]} hosts]
      (let [peers (map :name (filter #(not= (:name %) name) all-hosts))
            dirs  (str/join "," (map #(str "networks/tailscale/" %) peers))]
        (when (seq dirs)
          (log! (str "Creating dirs on " name ": " dirs))
          (ssh-exec! ssh (str "mkdir -p ~/" dirs)))))
    ;; Local dirs
    (doseq [{:keys [name]} hosts]
      (let [dir (str (env "HOME") "/networks/tailscale/" name)]
        (log! (str "Creating local dir: " dir))
        (fs/mkdirSync dir #js {:recursive true})))))

(defn phase-nfs-exports [config]
  (log! "\n--- Phase 7a: NFS Exports ---")
  (let [{:keys [hosts local-host local-share]} config
        all-hosts (concat [{:name local-host :share local-share}] hosts)]
    ;; Configure exports on each remote host
    (doseq [{:keys [ssh name share]} hosts]
      (let [peers    (filter #(not= (:name %) name) all-hosts)
            lines    (map (fn [p] (str share " " (:name p) "-ts(rw,sync,no_subtree_check)")) peers)
            export   (str/join "\n" lines)]
        (when (seq export)
          (log! (str "Exports on " name ":\n" export))
          (ssh-exec! ssh (str "sudo bash -c 'cat > /etc/exports << \"EOF\"\n" export "\nEOF'"))
          (ssh-exec! ssh "sudo exportfs -ra && sudo systemctl restart nfs-kernel-server"))))))

(defn phase-nfs-mounts [config]
  (log! "\n--- Phase 7b: NFS Mounts ---")
  (let [{:keys [hosts local-host local-share]} config
        all-hosts (concat [{:name local-host :share local-share}] hosts)]
    ;; Mount on each remote host
    (doseq [{:keys [ssh name]} hosts]
      (let [peers (filter #(not= (:name %) name) all-hosts)]
        (doseq [peer peers]
          (let [peer-name (:name peer)
                share (:share peer)
                mount-dir (str "/home/err/networks/tailscale/" peer-name)
                fstab-entry (str peer-name "-ts:" share "  " mount-dir "  nfs  _netdev,noauto,nofail,soft  0  0")]
            (log! (str "Mounting " peer-name " on " name "..."))
            (ssh-exec! ssh (str "grep -q '" peer-name "-ts.*/networks' /etc/fstab || echo '" fstab-entry "' | sudo tee -a /etc/fstab"))
            (ssh-exec! ssh (str "sudo mkdir -p " mount-dir))
            (ssh-exec! ssh (str "sudo mount " mount-dir " 2>/dev/null || true"))))))
    ;; Mount on local host
    (doseq [{:keys [name share]} hosts]
      (let [mount-dir (str (env "HOME") "/networks/tailscale/" name)
            fstab-entry (str name "-ts:" share "  " mount-dir "  nfs  _netdev,noauto,nofail,soft  0  0")]
        (log! (str "Mounting " name " locally..."))
        (local-exec! (str "grep -q '" name "-ts.*/networks' /etc/fstab || echo '" fstab-entry "' | sudo tee -a /etc/fstab"))
        (local-exec! (str "sudo mount " mount-dir " 2>/dev/null || true"))))))

(defn phase-cloudflared [hosts]
  (log! "\n--- Phase 8: Cloudflare Tunnels ---")
  (doseq [{:keys [ssh name]} hosts]
    (log! (str "Installing cloudflared on " name "..."))
    (ssh-exec! ssh
      "curl -fsSL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb -o /tmp/cf.deb && sudo dpkg -i /tmp/cf.deb && rm /tmp/cf.deb"
      {:timeout 60000})
    (log! (str "cloudflared installed on " name))))

;; ---------------------------------------------------------------------------
;; Main
;; ---------------------------------------------------------------------------

(defn run-mesh-setup! [config]
  (let [{:keys [hosts cloudflare]} config]
    (log! "=== Tailscale NFS Mesh Setup ===")
    (log! (str "Hosts: " (str/join ", " (map :name hosts))))

    ;; Phase 1-2: Tailscale
    (phase-install-tailscale hosts)
    (phase-start-tailscale hosts)
    (phase-wait-tailscale hosts)

    ;; Phase 3: Hosts entries
    (phase-setup-hosts config)

    ;; Phase 4-5: NFS
    (phase-install-nfs hosts)
    (phase-create-dirs config)
    (phase-nfs-exports config)
    (phase-nfs-mounts config)

    ;; Phase 6 (optional): Cloudflare
    (when cloudflare
      (phase-cloudflared hosts))

    ;; Done
    (log! "\n=== Mesh Setup Complete ===")
    (notify! "Mesh Setup Complete"
             (str (count hosts) " machine(s) provisioned into Tailscale NFS mesh")
             {:urgency "normal"})))

(defn load-config [config-path]
  (if config-path
    (let [raw     (fs/readFileSync config-path "utf8")
          parsed  (js/JSON.parse raw)]
      (js->clj parsed :keywordize-keys true))
    default-config))

(defn usage []
  (str/join
   "\n"
   ["Usage: nbb mesh-setup.cljs [config.json]"
    ""
    "Provision machines into a Tailscale + NFS mesh network."
    ""
    "If no config file is provided, uses the default 3-machine setup."
    ""
    "Config JSON keys:"
    "  local-host     - name of this machine (default: \"stealth\")"
    "  local-ts-ip    - Tailscale IP of this machine"
    "  local-share    - directory to export via NFS"
    "  hosts          - array of remote machines to provision"
    "   [].name       - hostname"
    "   [].ssh        - SSH target (user@ip)"
    "   [].ts-ip      - Tailscale IP"
    "    [].share      - directory to export via NFS"
    "  cloudflare     - install cloudflared (default: false)"]))

(defn main []
  (let [argv       (js->clj (.-argv js/process))
        args       (vec (drop 3 argv))
        config-file (first args)]
    (if (or (nil? config-file) (contains? #{"help" "-h" "--help"} config-file))
      (println (usage))
      (-> (run-mesh-setup! (load-config config-file))
          (p/catch (fn [e]
                     (fail! (or (.-stack e) (.-message e) (str e)))))))))

(main)
