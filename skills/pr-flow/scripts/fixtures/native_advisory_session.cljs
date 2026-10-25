;; SPDX-License-Identifier: GPL-3.0-or-later
;; Synthetic host fixture. Uses the real collector and bounded HTTP child.
(ns native-advisory-session
  (:require ["node:child_process" :as cp] ["node:path" :as path]
            [nbb.core :refer [*file*]] [test-native-context :as fixture]
            [pr-flow.fresh-review :as fresh]))
(let [[endpoint mode] *command-line-args*
      current (atom nil) records (atom [])
      review (assoc fixture/native-review :body "<v93>LGTM! Keep the genuine correction request.</v93>")
      deps {:read-json (fn [& args]
                         (when (= ["api" (str "repos/" fixture/repo "/issues/" fixture/number "/comments")
                                   "--paginate" "--slurp"] (vec args))
                           (reset! current (:deps (fixture/body-readers [[review]] [(fixture/native-review-node review)]))))
                         (apply (:read-json @current) args))
            :authorized-author? (fn [_ _] true) :read-history (fn [_] [])
            :policy-source (fn [] "synthetic-policy") :record! #(swap! records conj %)}
      parent (fresh/parent deps)
      attempt ((:begin! parent) {:repository fixture/repo :pr-number (js/Number fixture/number) :review-id 101}
               "synthetic-model" :chat)
      _ (when-not (= :pending (:status attempt)) (throw (ex-info "Fixture could not start" {})))
      transport (cp/spawnSync js/process.execPath
                  #js [(path/resolve (path/dirname *file*) "../pr_flow/classifier_transport.mjs")]
                  #js {:encoding "utf8" :timeout 65000 :maxBuffer 2097152
                       :input (js/JSON.stringify (clj->js {:endpoint endpoint :protocol "chat" :payload (:payload attempt)}))})
      response (when (= 0 (.-status transport))
                 (js->clj (js/JSON.parse (.-stdout transport)) :keywordize-keys true))
      response (if (= mode "altered-digest") (assoc-in response [:inference :request_sha256] "altered") response)
      result ((:consume! attempt) response)
      repeated ((:consume! attempt) response)]
  (println (js/JSON.stringify (clj->js {:result result :repeated repeated :phases (mapv :phase @records)}) nil 2)))
