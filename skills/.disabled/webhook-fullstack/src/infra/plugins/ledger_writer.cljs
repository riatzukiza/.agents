(ns infra.plugins.ledger-writer
  "fp-wrapped plugin: decorates server with :append-event! fn.
   Appends LedgerEvent maps to an EDN file (one map per line)."
  (:require [extern.fastify :as fx]
            [malli.core     :as m]
            [shape.webhook  :as s]))

(defn- event->edn-str [evt] (str (pr-str evt) "\n"))

(defn ^:export plugin
  [{:keys [ledger-path] :or {ledger-path "./ledger.edn"}}]
  (let [fp (js/require "fastify-plugin")
        fs (js/require "node:fs/promises")]
    (fp (fn [server _opts ^js/Function done]
          (fx/decorate!
           server :append-event!
           (fn ^:async [evt]
             (when (m/validate s/LedgerEvent evt)
               (await (.appendFile fs ledger-path (event->edn-str evt))))))
          (done))
        #js {:name "ledger-writer"})))
