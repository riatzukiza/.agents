(ns infra.server
  "Assembles and starts the Fastify server.
   fp-wrapped shared plugins MUST register before route plugins."
  (:require [extern.fastify                    :as fx]
            [infra.plugins.content-type-parser :as ctp]
            [infra.plugins.hmac-guard          :as hmac]
            [infra.plugins.ledger-writer       :as lw]
            [infra.routes.webhook              :as wh]))

(defn ^:async start!
  [{:keys [port secret ledger-path]
    :or   {port 3000 ledger-path "./ledger.edn"}}]
  (let [server (fx/make-server {:logger? true})]
    ;; shared fp-wrapped plugins first
    (await (fx/register! server (ctp/plugin) {}))
    (await (fx/register! server (hmac/plugin secret) {}))
    (await (fx/register! server (lw/plugin {:ledger-path ledger-path}) {}))
    ;; scoped route plugins last
    (await (fx/register! server (wh/plugin) {}))
    (await (.listen server #js {:port port :host "0.0.0.0"}))
    (js/console.log (str "Listening on :" port))))
