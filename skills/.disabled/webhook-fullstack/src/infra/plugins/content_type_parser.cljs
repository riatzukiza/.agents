(ns infra.plugins.content-type-parser
  "fp-wrapped plugin: registers raw-body parser for application/json.
   Preserves raw string alongside parsed body for HMAC verification."
  (:require [extern.fastify :as fx]))

(defn ^:export plugin
  []
  (let [fp (js/require "fastify-plugin")]
    (fp (fn [server _opts ^js/Function done]
          (.addContentTypeParser
           server "application/json"
           #js {:parseAs "string"}
           (fn [_req body done]
             (try
               (done nil #js {:raw    body
                              :parsed (js/JSON.parse body)})
               (catch :default e
                 (done e nil)))))
          (done))
        #js {:name "content-type-parser"})))
