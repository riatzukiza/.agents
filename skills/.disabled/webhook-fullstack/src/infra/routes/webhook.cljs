(ns infra.routes.webhook
  "Route plugin — NOT fp-wrapped (stays scoped).
   Consumes: content-type-parser, hmac-guard, ledger-writer decorations."
  (:require [domain.webhook :as d]
            [law.webhook    :as l]))

(defn ^:export plugin
  []
  (fn [server _opts ^js/Function done]
    (.post server "/webhook"
           #js {:schema #js {:body #js {:type "object"}}}
           (fn ^:async [req reply]
             (let [raw-body (.-raw    (.-body req))
                   parsed   (.-parsed (.-body req))
                   sig      (.. req -headers -x-signature-256)
                   svr      (.-server req)]

               (when-not ((.-verifyHmac! svr) raw-body sig)
                 (-> reply (.code 401) (.send #js {:error "invalid signature"}))
                 (return))

               (let [{:keys [ok err]}
                     (l/coerce-payload (js->clj parsed :keywordize-keys true))]
                 (if err
                   (-> reply (.code 422) (.send (clj->js {:error err})))
                   (let [ack (if (d/accept? ok)
                               (d/build-ack ok)
                               (d/build-reject (:event/id ok) :domain-rejection))]
                     (await ((.-appendEvent! svr)
                             {:ledger/id      (random-uuid)
                              :ledger/ts      (js/Date.)
                              :ledger/type    (:event/type ok)
                              :ledger/payload ok}))
                     (.send reply (clj->js ack))))))))
    (done)))
