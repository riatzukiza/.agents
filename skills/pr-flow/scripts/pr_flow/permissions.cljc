;; SPDX-License-Identifier: GPL-3.0-or-later
(ns pr-flow.permissions)

(defn writer-role?
  "Return the matching native repository-writer role, otherwise nil.
   Native identity and permission reads remain the caller's responsibility."
  [role]
  (#{"admin" "maintain" "write"} role))
