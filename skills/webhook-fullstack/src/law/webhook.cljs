(ns law.webhook
  (:require [malli.core  :as m]
            [malli.error :as me]
            [shape.webhook :as s]))

(defn valid-payload?  [x] (m/validate s/WebhookPayload x))
(defn valid-headers?  [x] (m/validate s/WebhookHeaders x))

(defn explain-payload
  "Returns humanised error map or nil."
  [x]
  (when-not (valid-payload? x)
    (me/humanize (m/explain s/WebhookPayload x))))

(defn coerce-payload
  "Coerce with Malli transformer; returns {:ok v} or {:err e}."
  [raw]
  (let [result (m/coerce s/WebhookPayload raw (m/coercer s/WebhookPayload))]
    (if (m/validate s/WebhookPayload result)
      {:ok result}
      {:err (explain-payload raw)})))
