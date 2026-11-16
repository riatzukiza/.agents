;; SPDX-License-Identifier: GPL-3.0-or-later
(ns native-test
  (:require [cljs.test :refer-macros [deftest is run-tests]]
            [ultra-code.native :as n]
            ["node:crypto" :as crypto]
            ["node:fs" :as fs]
            ["node:os" :as os]
            ["node:path" :as path]
            ["node:child_process" :as cp]))
(def h (apply str (repeat 64 "a")))
(defn digest [x] (-> (crypto/createHash "sha256") (.update (pr-str x) "utf8") (.digest "hex")))
(def review-out {:scope-complete true :findings []})
(def base {:snapshot h :input-hash h :round 1 :capacity 3 :limited? false
           :inputs {:prompt "prompt" :roles "roles" :schemas "schemas" :packets "packets" :gates "gates" :model "model"}
           :output-hashes {"review" (digest review-out)}
           :jobs [{:id "impl" :kind :gate :deps [] :writes [] :attempt 1}
                  {:id "review" :kind :review :deps ["impl"] :writes [] :attempt 1}
                  {:id "gate" :kind :gate :deps ["impl"] :writes [] :attempt 1}
                  {:id "skeptic" :kind :skeptic :deps ["review"] :writes [] :attempt 1 :author-job "review"}]
           :receipts [{:job-id "impl" :attempt 1 :snapshot h :input-hash h :round 1 :context-id "i" :status :complete :output {:passed true}}
                      {:job-id "review" :attempt 1 :snapshot h :input-hash h :round 1 :context-id "r" :context-mode :fresh :spawn-ref "spawn-r" :status :complete :output review-out}
                      {:job-id "gate" :attempt 1 :snapshot h :input-hash h :round 1 :context-id "g" :status :complete :output {:passed true}}
                      {:job-id "skeptic" :attempt 1 :snapshot h :input-hash h :round 1 :context-id "s" :context-mode :fresh :spawn-ref "spawn-s" :status :complete :upstream-hashes {"review" (digest review-out)} :output {:scope-complete true :additional-findings [] :votes []}}]})
(defn outcome [x] (:status (n/qualify x)))
(def finding {:id "f" :title "claim" :detail "evidence"})
(def vote {:finding-id "f" :verdict :confirmed :reason "verified"})
(defn with-finding []
  (let [out {:scope-complete true :findings [finding]}]
    (-> base (assoc-in [:receipts 1 :output] out)
        (assoc-in [:output-hashes "review"] (digest out))
        (assoc-in [:receipts 3 :upstream-hashes "review"] (digest out))
        (assoc-in [:receipts 3 :output :votes] [vote]))))
(deftest clean-needs-independent-skeptic
  (is (= :complete-clean (outcome base)))
  (is (= :incomplete (outcome (-> base (update :jobs pop) (update :receipts pop)))))
  (is (= :incomplete (outcome (update base :receipts pop))))
  (is (= :incomplete (outcome (assoc-in base [:receipts 3 :output :scope-complete] false))))
  (is (= :incomplete (outcome (assoc-in base [:receipts 3 :output :votes] [vote]))))
  (is (= :findings (outcome (assoc-in base [:receipts 3 :output :additional-findings] [finding])))))
(deftest exact-finding-coverage
  (let [b (with-finding)]
    (is (= :findings (outcome b)))
    (is (= :complete-clean (outcome (assoc-in b [:receipts 3 :output :votes 0 :verdict] :refuted))))
    (is (= :incomplete (outcome (assoc-in b [:receipts 3 :output :votes] []))))
    (is (= :incomplete (outcome (assoc-in b [:receipts 3 :output :votes] [vote vote]))))
    (is (= :incomplete (outcome (assoc-in b [:receipts 3 :output :votes 0 :verdict] :uncertain))))
    (is (= :incomplete (outcome (assoc-in b [:receipts 3 :output :votes 0 :reason] ""))))
    (is (= :incomplete (outcome (assoc-in b [:receipts 3 :output :votes 0 :finding-id] "other"))))))
(deftest author-output-binding
  (let [b (with-finding) changed {:scope-complete true :findings [(assoc finding :detail "changed")]}]
    (is (= :stale (outcome (-> b (assoc-in [:receipts 1 :output] changed) (assoc-in [:output-hashes "review"] (digest changed))))))
    (is (= :incomplete (outcome (update-in b [:receipts 3] dissoc :upstream-hashes))))))
(deftest failures-and-identities
  (is (= :incomplete (outcome (assoc base :jobs []))))
  (is (= :incomplete (outcome (assoc base :limited? true))))
  (is (= :incomplete (outcome (assoc-in base [:receipts 1 :status] :timeout))))
  (is (= :incomplete (outcome (assoc-in base [:receipts 1 :output] {}))))
  (is (= :failed (outcome (assoc-in base [:receipts 2 :output :passed] false))))
  (is (= :failed (outcome (assoc-in base [:receipts 0 :status] :failed))))
  (is (= :failed (outcome (assoc-in base [:receipts 1 :exit-code] 7))))
  (doseq [[k v] [[:snapshot (apply str (repeat 64 "b"))] [:input-hash (apply str (repeat 64 "b"))] [:round 2] [:attempt 2]]]
    (is (= :stale (outcome (assoc-in base [:receipts 1 k] v)))))
  (is (= :incomplete (outcome (assoc-in base [:receipts 1 :context-id] "s"))))
  (is (= :incomplete (outcome (assoc-in base [:receipts 3 :context-id] "r"))))
  (is (= :incomplete (outcome (update-in base [:receipts 1] dissoc :spawn-ref))))
  (is (= :incomplete (outcome (update base :receipts conj (first (:receipts base))))))
  (is (= :incomplete (outcome (update base :jobs conj (first (:jobs base)))))))
(deftest dag-and-ready
  (is (= :incomplete (outcome (assoc-in base [:jobs 0 :deps] ["missing"]))))
  (is (= :incomplete (outcome (assoc-in base [:jobs 0 :deps] ["review"]))))
  (is (= :incomplete (outcome (assoc-in base [:jobs 0 :writes] ["../escape"]))))
  (let [b (-> base (assoc-in [:jobs 0 :kind] :implement) (assoc-in [:jobs 0 :writes] ["src/a"]) (update :jobs conj {:id "other" :kind :implement :deps [] :writes ["src/a/b"] :attempt 1}))]
    (is (some #(= :write-conflict (:code %)) (:problems (n/qualify b))))
    (is (not-any? #(= :write-conflict (:code %)) (:problems (n/qualify (assoc-in b [:jobs 4 :deps] ["impl"]))))) )
  (is (= ["impl"] (:ready (n/qualify (assoc base :receipts [] :capacity 1)))))
  (is (= [] (:ready (n/qualify (assoc base :receipts [(assoc (first (:receipts base)) :status :running)] :capacity 1)))))
  (is (= :incomplete (outcome (assoc-in base [:jobs 0 :deps] true))))
  (is (= :incomplete (outcome (assoc-in base [:receipts 1 :output :findings] true)))))
(deftest multiple-skeptics
  (let [b (with-finding)
        j (assoc (get (:jobs b) 3) :id "skeptic2")
        r (assoc (get (:receipts b) 3) :job-id "skeptic2" :context-id "s2" :spawn-ref "spawn-s2")
        b2 (-> b (update :jobs conj j) (update :receipts conj r))]
    (is (= :findings (outcome b2)))
    (is (= :incomplete (outcome (assoc-in b2 [:receipts 4 :output :votes] []))))
    (is (= :incomplete (outcome (assoc-in b2 [:receipts 4 :context-id] "s"))))
    (is (= :incomplete (outcome (assoc-in b2 [:receipts 4 :upstream-hashes] {"other" h}))))))
(defn frozen []
  (let [out {:scope-complete true :findings []}]
    (-> base
        (update :jobs #(mapv (fn [j] (if (contains? #{"review" "gate"} (:id j)) (assoc j :deps []) j)) (filterv (fn [j] (not= "impl" (:id j))) %)))
        (update :receipts #(filterv (fn [r] (not= "impl" (:job-id r))) %))
        (assoc-in [:receipts 0 :output] out)
        (assoc-in [:receipts 2 :upstream-hashes "review"] (digest out))
        (assoc-in [:output-hashes "review"] (digest out)))))
(deftest frozen-review-regressions
  (let [b (frozen)]
    (is (= :complete-clean (outcome b)))
    (is (= :incomplete (outcome (assoc-in b [:receipts 0 :output :scope-complete] false))))
    (is (= :incomplete (outcome (update-in b [:receipts 0 :output] dissoc :scope-complete))))
    (is (= :incomplete (outcome (-> base (assoc-in [:jobs 0 :kind] :implement) (assoc-in [:receipts 0 :output] {:ok true})))))
    (is (= :incomplete (outcome (assoc-in b [:receipts 2 :spawn-ref] "spawn-r"))))
    (let [job {:id "other-gate" :kind :gate :deps [] :writes [] :attempt 1}
          failed (-> b (assoc-in [:receipts 1 :status] :failed) (update :jobs conj job))]
      (is (some #{"other-gate"} (:ready (n/qualify failed)))))))
(deftest cli-io-regressions
  (let [d (fs/mkdtempSync (path/join (os/tmpdir) "ultra-check-"))
        plan (path/join d "plan.edn") receipts (path/join d "receipts.edn") result (path/join d "result.edn")
        run (fn [& args] (cp/spawnSync (.-execPath js/process) (clj->js (concat [(aget (.-argv js/process) 1) "-cp" "src" "scripts/native.cljs" "check" plan receipts] args)) #js {:encoding "utf8"}))]
    (try
      (fs/copyFileSync "tests/fixtures/plan.edn" plan)
      (fs/copyFileSync "tests/fixtures/receipts.edn" receipts)
      (fs/appendFileSync receipts "\n{:status :failed}\n")
      (is (= 2 (.-status (run))))
      (fs/copyFileSync "tests/fixtures/receipts.edn" receipts)
      (fs/appendFileSync plan "\n garbage )")
      (is (= 2 (.-status (run))))
      (fs/copyFileSync "tests/fixtures/plan.edn" plan)
      (fs/writeFileSync result "preserve")
      (is (= 2 (.-status (run result))))
      (is (= "preserve" (fs/readFileSync result "utf8")))
      (let [before (fs/readFileSync plan "utf8") rb (fs/readFileSync receipts "utf8") link (path/join d "alias") fresh (path/join d "fresh")]
        (is (= 2 (.-status (run plan))))
        (is (= before (fs/readFileSync plan "utf8")))
        (is (= 2 (.-status (run receipts))))
        (is (= rb (fs/readFileSync receipts "utf8")))
        (fs/symlinkSync plan link)
        (is (= 2 (.-status (run link))))
        (is (= before (fs/readFileSync plan "utf8")))
        (is (= 0 (.-status (run fresh))))
        (is (re-find #":complete-clean" (fs/readFileSync fresh "utf8"))))
      (finally (fs/rmSync d #js {:recursive true :force true})))))
(deftest readiness-transitive-ancestors
  (let [b (-> (frozen)
              (update :jobs into [{:id "middle" :kind :gate :deps ["gate"] :writes [] :attempt 1}
                                  {:id "downstream" :kind :gate :deps ["middle"] :writes [] :attempt 1}
                                  {:id "independent" :kind :gate :deps [] :writes [] :attempt 1}])
              (update :receipts conj {:job-id "middle" :attempt 1 :snapshot h :input-hash h :round 1
                                      :context-id "middle-context" :status :complete :output {:passed true}}))]
    (doseq [broken [(assoc-in b [:receipts 1 :exit-code] 9)
                    (assoc-in b [:receipts 1 :status] :timeout)
                    (assoc-in b [:receipts 1 :snapshot] (apply str (repeat 64 "b")))
                    (assoc-in b [:receipts 1 :output] {})]]
      (is (= ["independent"] (:ready (n/qualify broken)))))
    (is (= ["downstream" "independent"] (:ready (n/qualify b))))))
(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))

(run-tests)
