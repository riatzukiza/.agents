;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.permissions)

(defn writer-role?
  "Return the matching native repository-writer role, otherwise nil.
   Native identity and permission reads remain the caller's responsibility."
  [role]
  (#{"admin" "maintain" "write"} role))

(defn observed-role
  "Normalize a native collaborator permission response. GitHub reports the
   maintain role in role_name even when permission is write. Custom role names
   fall back to their native base permission; unknown roles grant nothing."
  [response]
  (or (writer-role? (:role_name response))
      (writer-role? (:permission response))))
