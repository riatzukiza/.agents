(ns domain.webhook
  "Pure business logic. No I/O, no JS interop."
  (:require [law.webhook :as l]))

(defn accept?
  "Returns true if the payload passes all domain guards."
  [payload]
  (l/valid-payload? payload))

(defn build-ack
  [{:event/keys [id]}]
  {:status    :ok
   :event/id  id
   :ts        (js/Date.)})

(defn build-reject
  [event-id reason]
  {:status    :rejected
   :event/id  event-id
   :ts        (js/Date.)
   :reason    reason})
