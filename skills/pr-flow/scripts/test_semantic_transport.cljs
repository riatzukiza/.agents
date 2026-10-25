;; SPDX-License-Identifier: GPL-3.0-or-later
(ns test-semantic-transport
  (:require [cljs.test :refer [deftest is run-tests]]
            [pr-flow.semantic-transport :as transport]))
(deftest immutable-content-custody
  (let [body "λ source\n" bytes (js/Buffer.from body "utf8")
        crypto (js/require "crypto")
        blob (.digest (.update (.update (.createHash crypto "sha1")
                           (str "blob " (.-length bytes) "\u0000")) bytes) "hex")
        head (apply str (repeat 40 "a"))
        response {:type "file" :path "src/source.cljc" :encoding "base64"
                  :sha blob :content (.toString bytes "base64")}
        call (fn [r] (transport/immutable-source (fn [& _] r) "o/r" head "src/source.cljc"))]
    (is (= {:path "src/source.cljc" :revision head :blob blob :text body
            :sha256 (transport/sha256 body)} (call response)))
    (doseq [r [(assoc response :sha (apply str (repeat 40 "b")))
               (assoc response :content (.toString (js/Buffer.from "altered") "base64"))
               (assoc response :path "other.cljc") (assoc response :type "symlink")]]
      (is (thrown? js/Error (call r))))))
(deftest source-coordinates-and-no-paid-model
  (is (transport/source-path? "docs/design.cljc"))
  (doseq [p ["../secret" "/absolute" "a//b" "a?ref=b" "a%2fb" "a\\b" "a b"]]
    (is (not (transport/source-path? p))))
  (is (thrown? js/Error (transport/infer "paid/model" {} "prompt" {}))))
(run-tests)
