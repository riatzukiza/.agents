(ns infra.plugins.hmac-guard
  "fp-wrapped plugin: decorates server with :verify-hmac! fn.
   Must be registered before any route that checks signatures."
  (:require [extern.fastify :as fx]
            [extern.crypto  :as cx]))

(defn ^:export plugin
  [secret]
  (let [fp (js/require "fastify-plugin")]
    (fp (fn [server _opts ^js/Function done]
          (fx/decorate!
           server :verify-hmac!
           (fn [raw-body sig]
             (let [expected (str "sha256=" (cx/hmac-sha256-hex secret raw-body))]
               (= expected sig))))
          (done))
        #js {:name "hmac-guard"})))
