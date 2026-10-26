;; SPDX-License-Identifier: LGPL-3.0-or-later
(ns pr-flow.kimi-verdict
  "Candidate v2 model-decision protocol. No native publication or admission.")

(defn publication-intent
  "Validate the completed model decision against trusted immutable coordinates.
   A proposed event supplies no native identity, invocation or approval custody."
  [_expected _response]
  {:refusal :not-implemented})
