#!/usr/bin/env bb

(require '[clojure.java.io :as io])

(def script-dir
  (some-> *file* io/file .getParent))

(when-not script-dir
  (binding [*out* *err*]
    (println "Run this script as an executable so *file* is bound."))
  (System/exit 1))

(load-file (str script-dir "/common.bb"))

(require '[session-mycology.common :as c])

(let [root (or (c/find-project-root)
               (do (binding [*out* *err*]
                     (println "Could not find project root containing .ημ/ or .git/"))
                   (System/exit 1)))]
  (c/session-mycology-dir root)
  (c/spores-dir root)
  (println "Initialized .ημ/session-mycology/ in" root))
