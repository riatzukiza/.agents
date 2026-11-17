(ns test-inventory
  (:require [cljs.test :refer [deftest is testing run-tests]]
            [pr-flow.inventory :as inv]
            [pr-flow.law :as law]))

(def head "abcdef0123456789abcdef0123456789abcdef01")

(deftest inventory-is-data
  (is (= #{"coderabbit" "codex" "mimo" "kimi" "antigravity"} (set (keys inv/reviewers))))
  (is (every? #(contains? inv/eligible %) (keys inv/reviewers)) "all reviewers remain in the inventory; evidence admission is separate")
  (is (= "antigravity" (inv/check-reviewer {:name "review / review" :workflow "Antigravity evidence review"}))))

(deftest retired-recruitment-preserves-evidence
  (is (not (inv/active "mimo")))
  (is (inv/eligible "mimo"))
  (is (= "codex" inv/default-request))
  (is (= "mimo" (law/trusted-reviewer {:user {:login "eta-mu-ai[bot]" :type "Bot"}} inv/identities)))
  (is (not (contains? (:review-participants (law/reviewer-availability {:head head})) "mimo")))
  (is (contains? (:review-participants (law/reviewer-availability {:head head :mandatory #{"mimo"}})) "mimo")))

(deftest retirement-does-not-rewrite-historical-rounds
  (let [old "0000000000000000000000000000000000000000"
        configured #{"codex" "mimo"}
        participants #{"codex"}
        past [{:reviewer "codex" :commit_id old :submitted_at "2026-11-01T00:00:00Z"}]
        current {:reviewer "codex" :commit_id head :submitted_at "2026-11-17T00:00:00Z"}]
    (is (= 0 (law/available-review-rounds past [] "code" configured participants head)))
    (is (= 1 (law/available-review-rounds (conj past current) [] "code" configured participants head)))))

(deftest waivers
  (is (inv/waived? "kimi" "open-hax/proxx"))
  (is (not (inv/waived? "kimi" "open-hax/eta-mu")))
  (doseq [r ["riatzukiza/foresight" "riatzukiza/epiphany" "open-hax/axxium" "riatzukiza/axxium"]]
    (is (inv/waived? "mimo" r) r))
  (is (not (inv/waived? "mimo" "open-hax/proxx")))
  (testing "a waived reviewer's failing check no longer blocks"
    (let [chk {:name "Review pull request with OpenCode" :workflow "OpenCode Kimi PR Review"
               :state "FAILURE" :headSha head}
          base {:head head :snapshot-head head :threads [] :rounds 5 :checks [chk]
                :required-reviewers #{"kimi"} :approved-heads {"codex" #{head}}}]
      (is (not (:pass? (law/merge-gate base))))
      (is (:pass? (law/merge-gate (assoc base :waived #{"kimi"})))))))

(deftest artifact-verdicts
  (let [ok {:base "b" :head head :scope_complete true :status "clean" :findings []}]
    (is (:positive? (law/artifact-verdict head ok)))
    (is (not (:positive? (law/artifact-verdict "0000000000000000000000000000000000000000" ok))))
    (is (= :incomplete-scope (:reason (law/artifact-verdict head (assoc ok :scope_complete false)))))
    (is (= :findings (:reason (law/artifact-verdict head (assoc ok :status "findings" :findings [{:title "x"}])))))
    (testing "clean artifact never approves without source verification"
      (let [ev (law/merge-artifact-evidence {:approved-heads {"antigravity" #{head}}
                                                  :approval-evidence {"antigravity" {:head head}}}
                                                 {"antigravity" (law/artifact-verdict head ok)})]
        (is (empty? (get-in ev [:approved-heads "antigravity"])))
        (is (nil? (get-in ev [:approval-evidence "antigravity"])))
        (is (not (:pass? (law/merge-gate (merge {:head head :snapshot-head head :threads [] :rounds 5 :checks []}
                                           (select-keys ev [:approved-heads]))))))))
    (testing "findings block unless waived"
      (let [v (law/artifact-verdict head (assoc ok :status "findings" :findings [{:title "x"}]))
            ev (law/merge-artifact-evidence {:approved-heads {"codex" #{head}}} {"antigravity" v})
            g {:head head :snapshot-head head :threads [] :rounds 5 :checks []
               :approved-heads (:approved-heads ev) :artifact-findings (:artifact-findings ev)}]
        (is (not (:pass? (law/merge-gate g))))
        (is (:pass? (law/merge-gate (assoc g :waived #{"antigravity"}))))))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))
(run-tests 'test-inventory)
