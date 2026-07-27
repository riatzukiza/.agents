(ns receipt-river.common
  "Shared helpers for receipt-river bb scripts."
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

(defn receipt-file
  "Return the canonical receipt path. Prefers .ημ/receipts.edn if it exists,
   otherwise falls back to project-root/receipts.edn."
  [project-root]
  (let [eta-mu-file (io/file project-root ".ημ" "receipts.edn")
        root-file (io/file project-root "receipts.edn")]
    (.getCanonicalPath (if (.exists eta-mu-file) eta-mu-file root-file))))

(defn now-iso []
  (.toString (java.time.Instant/now)))

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

(defn read-lines
  "Read non-blank lines from a file."
  [path]
  (if (.exists (io/file path))
    (->> (slurp path)
         (str/split-lines)
         (filter #(not (str/blank? %)))
         (doall))
    []))
