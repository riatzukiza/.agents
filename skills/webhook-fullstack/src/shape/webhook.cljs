(ns shape.webhook
  "All data shapes for the webhook domain.
   No I/O. No dependencies outside shape.* and law.*.")

;; ── Incoming ────────────────────────────────────────────────────────────────

(def WebhookPayload
  [:map
   [:event/id      :uuid]
   [:event/type    :keyword]
   [:event/source  :string]
   [:event/ts      inst?]
   [:event/body    :map]])

(def WebhookHeaders
  [:map
   [:x-signature   {:optional true} :string]
   [:content-type  :string]])

;; ── Outgoing ────────────────────────────────────────────────────────────────

(def AckResponse
  [:map
   [:status    [:enum :ok :rejected]]
   [:event/id  :uuid]
   [:ts        inst?]])

;; ── Event ledger entries ────────────────────────────────────────────────────

(def LedgerEvent
  [:map
   [:ledger/id      :uuid]
   [:ledger/parent  {:optional true} :uuid]
   [:ledger/ts      inst?]
   [:ledger/type    :keyword]
   [:ledger/payload :map]])
