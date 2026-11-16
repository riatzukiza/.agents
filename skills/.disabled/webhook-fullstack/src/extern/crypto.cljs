(ns extern.crypto
  "HMAC-SHA256 boundary — used only by infra.plugins.hmac-guard.")

(def ^:private crypto (js/require "node:crypto"))

(defn hmac-sha256-hex
  "Returns hex string or nil on error."
  [^string secret ^string body]
  (try
    (-> (.createHmac crypto "sha256" secret)
        (.update body)
        (.digest "hex"))
    (catch :default _ nil)))
