;; SPDX-License-Identifier: GPL-3.0-or-later
(ns ultra-code.native-cli
  (:require [edamame.core :as reader]
            [ultra-code.native :as native]
            ["node:fs" :as fs]
            ["node:crypto" :as crypto]))

(defn sha256 [bytes] (-> (crypto/createHash "sha256") (.update bytes) (.digest "hex")))
(defn output-hash [output] (sha256 (js/Buffer.from (pr-str output) "utf8")))
(defn parse-one [s]
  (let [forms (reader/parse-string-all s)]
    (when-not (= 1 (count forms)) (throw (js/Error. "Expected exactly one EDN form")))
    (first forms)))
(defn read-edn [path] (parse-one (.toString (fs/readFileSync path) "utf8")))
(defn fail! [s] (.error js/console s) (set! (.-exitCode js/process) 2))
(defn main []
  (let [[cmd a b out & extra] (vec *command-line-args*)]
    (try
      (cond
        (and (= cmd "sha256") a (nil? b)) (println (sha256 (fs/readFileSync a)))
        (and (= cmd "output-hash") a b (nil? out) (empty? extra))
        (let [receipts (read-edn a) matches (filter #(= b (:job-id %)) receipts)]
          (when-not (and (vector? receipts) (= 1 (count matches)) (contains? (first matches) :output))
            (throw (js/Error. "Expected exactly one receipt with an output for that job")))
          (println (output-hash (:output (first matches)))))
        (and (= cmd "check") a b (empty? extra))
        (let [bytes (fs/readFileSync a)
              plan (parse-one (.toString bytes "utf8"))
              receipts (read-edn b)
              _ (when-not (and (map? plan) (vector? receipts)) (throw (js/Error. "Expected a plan map and a receipts vector")))
              hashes (into {} (for [r receipts :when (and (map? r) (contains? r :output))] [(:job-id r) (output-hash (:output r))]))
              result (native/qualify (assoc plan :input-hash (sha256 bytes) :receipts receipts :output-hashes hashes))
              text (str (pr-str result) "\n")]
          (if out
            (let [tmp (str out ".tmp-" (.-pid js/process) "-" (crypto/randomUUID))]
              (try (fs/writeFileSync tmp text #js {:flag "wx"}) (fs/linkSync tmp out) (fs/unlinkSync tmp)
                   (catch :default e (when (fs/existsSync tmp) (fs/unlinkSync tmp)) (throw e))))
            (print text))
          (when-not (= :complete-clean (:status result)) (set! (.-exitCode js/process) 1)))
        :else (fail! "Usage: native.cljs sha256 FILE | output-hash RECEIPTS.edn JOB-ID | check PLAN.edn RECEIPTS.edn [RESULT.edn]"))
      (catch :default e (fail! (str "Evidence validation error: " (.-message e)))))))
(main)
