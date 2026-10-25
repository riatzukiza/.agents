;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.semantic-transport
  "Owned in-process semantic inference. Never imports an assessment from disk."
  (:require ["crypto" :as crypto] ["child_process" :as cp]
            ["path" :as path] [nbb.core :refer [*file*]]
            [clojure.string :as str]))

(def here (path/dirname *file*))
(defn sha256 [text] (.digest (.update (crypto/createHash "sha256") text "utf8") "hex"))
(defn source-path? [p]
  (and (string? p) (<= 1 (count p) 512)
       (not (re-find #"[\\\s?#%:]" p))
       (every? #(and (seq %) (not (#{"." ".."} %))) (str/split p #"/" -1))))
(defn immutable-source [read-json repo head p]
  (when-not (and (source-path? p) (re-matches #"[0-9a-f]{40}" head))
    (throw (ex-info "Invalid immutable semantic source coordinate" {})))
  (let [r (read-json "api" (str "repos/" repo "/contents/" p "?ref=" head))
        bytes (when (and (= "file" (:type r)) (= p (:path r)) (= "base64" (:encoding r)))
                (js/Buffer.from (:content r) "base64"))
        actual (when bytes (.digest (.update (.update (crypto/createHash "sha1")
                                    (str "blob " (.-length bytes) "\u0000")) bytes) "hex"))]
    (when-not (and bytes (<= (.-length bytes) 65536) (= actual (:sha r)))
      (throw (ex-info "Immutable semantic source custody unavailable" {})))
    (let [text (.decode (js/TextDecoder. "utf-8" #js {:fatal true}) bytes)]
      {:path p :revision head :blob (:sha r) :text text :sha256 (sha256 text)})))

(defn infer
  "Single explicit free native-client invocation, preserving actual provenance.
   Model success is transport evidence only; purpose law validates semantics."
  [model request instruction schema]
  (when-not (and (string? model) (re-matches #"opencode/[a-z0-9.-]+-free" model))
    (throw (ex-info "Explicit supported free semantic model required" {})))
  (let [payload {:model model :request request :instruction instruction :response_schema schema}
        serialized (js/JSON.stringify (clj->js payload))
        _ (when (> (.-length (js/Buffer.from serialized "utf8")) 1000000)
            (throw (ex-info "Complete semantic input exceeds transport budget; not truncated" {})))
        run (cp/spawnSync js/process.execPath #js [(path/join here "opencode_classifier.mjs")]
              #js {:input (js/JSON.stringify (clj->js {:endpoint "opencode" :protocol "opencode" :payload payload}))
                   :encoding "utf8" :timeout 90000 :maxBuffer 2097152})
        result (try (js->clj (js/JSON.parse (.-stdout run)) :keywordize-keys true)
                    (catch :default _ {:error "invalid-native-output"}))
        i (:inference result)]
    (if (and (= 0 (.-status run)) (map? (:output result))
             (= "opencode-cli" (:endpoint i)) (= model (:configured_model i) (:reported_model i))
             (= 0 (:cost i)) (string? (:session_id i)) (string? (:response_id i))
             (= (sha256 serialized) (:request_sha256 i))
             (every? #(and (string? %) (re-matches #"[0-9a-f]{64}" %))
                     [(:response_sha256 i) (:events_sha256 i)]))
      {:status :complete :output (:output result) :inference i}
      {:status :uncertain :reason (or (:error result) "native-inference-unavailable")
       :provider-status (:status result) :retry-after-ms (:retry_after_ms result)
       :inference i :unvalidated-output (:unvalidated_output result)})))
