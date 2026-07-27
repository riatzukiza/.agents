(ns client.app
  "Minimal ClojureScript client — posts a webhook payload to the server."
  (:require [applied-science.js-interop :as j]))

(defn post-webhook!
  ^:async [url payload]
  (let [resp (await (js/fetch url
                    #js {:method  "POST"
                         :headers #js {"Content-Type" "application/json"}
                         :body    (js/JSON.stringify (clj->js payload))}))]
    (js->clj (await (.json resp)) :keywordize-keys true)))

(defn ^:export init []
  (js/console.log "client/app initialised"))
