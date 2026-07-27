#!/usr/bin/env bb

(require '[clojure.java.io :as io])

(def script-dir
  (some-> *file* io/file .getParent))

(when-not script-dir
  (binding [*out* *err*]
    (println "Run this script as an executable so *file* is bound."))
  (System/exit 1))

(load-file (str script-dir "/common.bb"))

(require '[receipt-river.common :as c])

(let [args (c/parse-key-value-args *command-line-args*)
      eta-mu (contains? args "--eta-mu")
      root (or (c/find-project-root)
               (do (binding [*out* *err*]
                     (println "Could not find project root containing .ημ/ or .git/"))
                   (System/exit 1)))
      receipt-path (if eta-mu
                     (do (.mkdirs (io/file root ".ημ"))
                         (.getCanonicalPath (io/file root ".ημ" "receipts.edn")))
                     (c/receipt-file root))]
  (when-not (.exists (io/file receipt-path))
    (spit receipt-path "")
    (println "Initialized" receipt-path))
  (when (.exists (io/file receipt-path))
    (println "Receipts already exist at" receipt-path)))
