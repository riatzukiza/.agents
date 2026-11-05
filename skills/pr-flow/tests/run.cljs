;; SPDX-License-Identifier: GPL-3.0-or-later
;; nbb -cp scripts tests/run.cljs
;; These existing suites carry their own cljs.test/run-tests and failure exit code.
(ns tests.run
  (:require [test-raw-review]
            [test-kimi-verdict]))
