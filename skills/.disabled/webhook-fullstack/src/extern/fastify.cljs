(ns extern.fastify
  "Typed boundary to Fastify and its plugin ecosystem.
   Returns plain CLJS maps — never raw JS objects.")

(def ^:private Fastify (js/require "fastify"))
(def ^:private fp      (js/require "fastify-plugin"))

(defn make-server
  [{:keys [logger? trust-proxy?]
    :or   {logger? true trust-proxy? false}}]
  (Fastify #js {:logger       logger?
                :trustProxy   trust-proxy?
                :ajv          #js {:customOptions #js {:strict false}}}))

(defn register!
  [server plugin opts]
  (.register server plugin (clj->js opts)))

(defn decorate!
  [server k v]
  (.decorate server (name k) v))

(defn add-hook!
  [server hook-name handler]
  (.addHook server (name hook-name) handler))
