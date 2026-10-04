#!/usr/bin/env bash
set -euo pipefail
[[ -f /home/agent/.agent-sandbox-container && "$DISPLAY" == :99 ]] || exit 1
socket=agent-core
emacsclient -s "$socket" -e '(with-current-buffer "*espanso-regression*" (erase-buffer))' >/dev/null
window="$(emacsclient -s "$socket" -e '(catch (quote frame) (dolist (f (frame-list)) (when (equal (frame-parameter f (quote name)) "Sandbox Espanso") (throw (quote frame) (frame-parameter f (quote outer-window-id))))))' | tr -d '"')"
[[ "$window" =~ ^[0-9]+$ ]] || exit 1
trigger=':sandbox-unicode '
for ((i=0; i<${#trigger}; i++)); do
  timeout 5 xdotool windowactivate --sync "$window"
  [[ "$(xdotool getwindowfocus)" == "$window" ]] || exit 1
  timeout 5 xdotool type --clearmodifiers --delay 20 "${trigger:i:1}"
done
sleep 1
result="$(emacsclient -s "$socket" -e '(with-current-buffer "*espanso-regression*" (buffer-string))')"
[[ "$result" == '" μ⟲"' ]] || { echo "unexpected result: $result" >&2; exit 1; }
emacsclient -s "$socket" -e '(progn (set-face-attribute (quote default) nil :family "DejaVu Sans Mono" :height 180 :background "#101722" :foreground "#e8edf5") (menu-bar-mode -1) (tool-bar-mode -1) (catch (quote frame) (dolist (f (frame-list)) (when (equal (frame-parameter f (quote name)) "Sandbox Espanso") (select-frame f) (delete-other-windows) (set-window-buffer (selected-window) "*espanso-regression*") (with-current-buffer "*espanso-regression*" (goto-char (point-min))) (set-window-buffer (split-window-below) (find-file-noselect "/workspace/.artifacts/espanso-keys-new.log")) (throw (quote frame) t)))))' >/dev/null
timeout 5 i3-msg "[id=\"$window\"] fullscreen enable" >/dev/null
sleep 1
import -window root /workspace/.artifacts/03-unicode-runtime.png
emacsclient -s "$socket" -e '(progn (delete-other-windows) (switch-to-buffer (find-file-noselect "/workspace/installer-tests.log")) (goto-char (point-min)) (set-face-attribute (quote default) nil :height 120))' >/dev/null
sleep 1
import -window root /workspace/.artifacts/04-installer-tests.png
