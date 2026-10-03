;;; funcs.el --- sandbox private layer helpers -*- lexical-binding: t; -*-
(defun agent-sandbox-open-workspace ()
  "Open the disposable sandbox workspace."
  (interactive)
  (dired "/workspace"))
