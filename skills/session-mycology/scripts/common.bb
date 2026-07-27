(ns session-mycology.common
  "Shared helpers for session-mycology bb scripts."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn find-project-root
  "Walk up from start-dir looking for .ημ/ then .git/. Returns canonical path or nil."
  ([]
   (find-project-root (System/getProperty "user.dir")))
  ([start-dir]
   (loop [dir (io/file start-dir)]
     (when dir
       (let [path (.getCanonicalPath dir)]
         (cond
           (.isDirectory (io/file dir ".ημ"))
           path

           (.isDirectory (io/file dir ".git"))
           path

           (= path "/")
           nil

           :else
           (recur (.getParentFile dir))))))))

(defn ensure-dir
  "Ensure a directory exists and return its canonical path."
  [^java.io.File dir]
  (doto dir .mkdirs))

(defn eta-mu-dir
  "Return the .ημ/ directory inside project root, creating it if needed."
  [project-root]
  (ensure-dir (io/file project-root ".ημ")))

(defn session-mycology-dir
  "Return the .ημ/session-mycology/ directory, creating it if needed."
  [project-root]
  (ensure-dir (io/file project-root ".ημ" "session-mycology")))

(defn spores-dir
  "Return the .ημ/session-mycology/spores/ directory, creating it if needed."
  [project-root]
  (ensure-dir (io/file project-root ".ημ" "session-mycology" "spores")))

(defn now-iso []
  (.toString (java.time.Instant/now)))

(defn ts-slug []
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss")))

(defn kebab-slug
  "Turn an arbitrary string into a kebab-case slug."
  [s]
  (-> s
      (str/lower-case)
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"^-+|-+$" "")
      (str/replace #"-{2,}" "-")))

(defn session-id
  "Best-effort session identifier. Prefer OPENCODE_SESSION_ID, then cwd."
  []
  (or (System/getenv "OPENCODE_SESSION_ID")
      (System/getProperty "user.dir")))

(defn parse-key-value-args
  "Parse a flat seq of --key value args into a map. Boolean flags without a
   value are stored as true. Last value wins."
  [args]
  (loop [args args
         result {}]
    (if (empty? args)
      result
      (let [k (first args)
            v (second args)]
        (if (str/starts-with? k "--")
          (if (and v (not (str/starts-with? v "--")))
            (recur (drop 2 args) (assoc result k v))
            (recur (rest args) (assoc result k true)))
          (recur (rest args) result))))))
