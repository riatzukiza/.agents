;; SPDX-License-Identifier: GPL-3.0-or-later
(ns test-semantic-repair
  (:require [cljs.test :refer [deftest is run-tests]]
            ["node:crypto" :as crypto] ["fs" :as fs] ["path" :as path]
            [nbb.core :refer [*file*]] [clojure.edn :as edn]
            [test-repair-ack :as fixture]
            [pr-flow.semantic-repair :as semantic]))

(defn sha [s] (.digest (.update (crypto/createHash "sha256") s "utf8") "hex"))
(def model "opencode/glm-5.3-flash-free")
(def raw-root "<!-- coderabbit-ai-generated:v1 -->\n\nFixed in af114f39: The source now denies actor substitutions.\n\n✅ Confirmed as addressed by @writer\n\n<!-- This is an auto-generated reply by CodeRabbit -->")
(def verification "Native source verification: the current actor substitution check denies the request and leaves no open concern.")
(def source-text "(defn admit [expected actual] (= expected actual))")
(def nodes [(assoc fixture/repair :body raw-root) fixture/historical
            (assoc fixture/fresh :body verification)])
(def t (fixture/input nodes))
(def input {:thread t :complete? true
            :sources [{:path "src.clj" :revision fixture/head :blob fixture/old-head
                       :text source-text :sha256 (sha source-text)}]
            :policy {:bot-identities #{fixture/bot} :models #{model} :transport :opencode-cli}})
(defn output [request]
  {:request_id (:request-id request) :purpose semantic/version :judgment "verified-repair"
   :author_repair true :bot_verification true :source_supported true :no_remaining_concern true
   :reason "Source equality rejects the substitution described by the author and independently verified by the native Bot."
   :quotes [{:kind "root" :id "10" :quote "Fixed in af114f39: The source now denies actor substitutions."}
            {:kind "verification" :id "12" :quote verification}
            {:kind "source" :id "src.clj" :quote source-text}]})
(defn evidence [i]
  (let [r (semantic/prepare i sha) o (output r)]
    (assoc (semantic/validate-result r o)
           :execution {:channel :owned-model-execution :purpose semantic/version
                       :request-id (:request-id r) :response-digest (sha (pr-str o))
                       :model model :run-id "session_fixture" :complete? true :tools-used? false
                       :started-at "2026-10-23T10:03:00Z" :completed-at "2026-10-23T10:03:20Z"
                       :inference {:endpoint "opencode-cli" :configured_model model :reported_model model
                                   :session_id "session_fixture" :response_id "response_fixture" :cost 0
                                   :request_sha256 (sha "serialized transport payload")
                                   :response_sha256 (sha "native exported result") :events_sha256 (sha "native events")}})))

(deftest wrapped-raw-author-root-is-semantically-assessed-without-rewriting
  (let [r (semantic/prepare input sha) e (evidence input) p (semantic/proof t e)]
    (is (= :prepared (:status r)))
    (is (= raw-root (get-in r [:payload :native-context :thread :comments :nodes 0 :body])))
    (is (= :candidate (:status e))) (is (= :scoped-semantic-repair (:authority p)))
    (is (= 12 (:verification-id p)))
    (doseq [credit [:approval-credit :coverage-credit :round-credit :check-credit]] (is (false? (credit p))))
    (is (= p (semantic/proof (assoc t :semantic-repair-evidence e :severity :p1 :settled? false) e)))
    (is (= raw-root (get-in t [:comments 0 :body])))))

(deftest native-custody-and-independent-current-bot-are-deterministic
  (doseq [changed [(assoc t :resolved? false) (assoc t :head fixture/old-head)
                   (assoc-in t [:native-context :thread :isResolved] false)
                   (assoc-in t [:native-context :thread :comments :pageInfo :hasNextPage] true)
                   (assoc-in t [:native-context :thread :comments :totalCount] 4)
                   (assoc-in t [:comments 0 :authorized?] false)
                   (assoc-in t [:comments 0 :body] "Different raw body")
                   (fixture/input (assoc nodes 2 (assoc (last nodes) :updatedAt "2026-10-23T10:02:01Z")))
                   (fixture/input (assoc nodes 2 (assoc-in (last nodes) [:pullRequestReview :commit :oid] fixture/old-head)))
                   (fixture/input (assoc nodes 2 (assoc-in (last nodes) [:pullRequestReview :author] fixture/author)))
                   (assoc-in t [:native-context :pr :isDraft] true)
                   (assoc t :issue-comments nil)]]
    (is (= :uncertain (:status (semantic/prepare (assoc input :thread changed) sha))))
    (is (nil? (semantic/proof changed (evidence input)))))
  (is (= :uncertain (:status (semantic/prepare (assoc-in input [:policy :bot-identities] #{}) sha))))
  (is (= :uncertain (:status (semantic/prepare (assoc input :complete? false) sha)))))

(deftest source-custody-and-full-input-binding
  (doseq [i [(assoc input :sources [])
             (assoc-in input [:sources 0 :text] "Changed text without digest")
             (assoc-in input [:sources 0 :revision] fixture/old-head)
             (assoc-in input [:sources 0 :blob] "unknown")
             (assoc-in input [:sources 0 :path] "../src.clj")
             (update input :sources conj (first (:sources input)))]]
    (is (= :uncertain (:status (semantic/prepare i sha)))))
  (let [r (semantic/prepare input sha)]
    (doseq [i [(assoc-in input [:sources 0 :blob] fixture/head)
               (assoc-in input [:thread :native-context :thread :comments :nodes 1 :pullRequestReview :body] "New concern")
               (assoc-in input [:thread :issue-comments] [{:body "A new live question"}])]]
      (is (not= (:request-id r) (:request-id (semantic/prepare i sha))))
      (is (= :uncertain (:status (semantic/validate-result (semantic/prepare i sha) (output r))))))))

(deftest strict-purpose-semantic-judgment-and-exact-citations
  (let [r (semantic/prepare input sha) o (output r)]
    (doseq [bad [nil {} (assoc o :approval true) (assoc o :judgment "informational")
                 (assoc o :request_id (sha "other")) (assoc o :purpose "raw-review/v1")
                 (assoc o :author_repair false) (assoc o :bot_verification false)
                 (assoc o :source_supported false) (assoc o :no_remaining_concern false)
                 (assoc o :judgment "finding") (assoc o :judgment "uncertain")
                 (assoc o :quotes []) (update o :quotes pop)
                 (assoc-in o [:quotes 0 :quote] "Invented evidence")
                 (assoc-in o [:quotes 1 :id] "11")
                 (update o :quotes conj (first (:quotes o)))
                 (assoc o :reason "")]]
      (is (= :uncertain (:status (semantic/validate-result r bad)))))
    (is (= :uncertain (:status (semantic/validate-result (with-meta r nil) o))))))

(deftest owned-execution-and-recollection-required
  (let [e (evidence input)]
    (doseq [changed [(dissoc e :execution) (with-meta e nil)
                     (assoc-in e [:execution :model] "other-model")
                     (assoc-in e [:execution :run-id] "other-session")
                     (assoc-in e [:execution :purpose] "raw-review/v1")
                     (assoc-in e [:execution :request-id] (sha "other"))
                     (assoc-in e [:execution :response-digest] (sha "other"))
                     (assoc-in e [:execution :complete?] false)
                     (assoc-in e [:execution :tools-used?] true)
                     (assoc-in e [:execution :started-at] "2026-10-23T10:01:00Z")
                     (assoc-in e [:execution :completed-at] "2026-10-23T10:02:59Z")
                     (assoc-in e [:execution :inference :cost] 1)
                     (assoc-in e [:execution :inference :reported_model] "other")
                     (assoc-in e [:execution :inference :events_sha256] nil)
                     (assoc-in e [:response :reason] "Changed semantic answer")]]
      (is (nil? (semantic/proof t changed))))
    (is (nil? (semantic/proof (fixture/input (conj nodes (fixture/node 13 fixture/bot "Still unresolved" "2026-10-23T10:04:00Z" fixture/head))) e)))
    (is (nil? (semantic/proof (assoc t :issue-comments [{:body "new concern"}]) e)))))

(deftest oversized-input-refuses-without-truncation
  (let [text (apply str (repeat 750001 "x"))
        i (-> input (assoc-in [:sources 0 :text] text) (assoc-in [:sources 0 :sha256] (sha text)))]
    (is (= :input-size-or-digest (:reason (semantic/prepare i sha))))))

(deftest captured-native-wrapped-roots-with-explicitly-synthetic-model-and-source
  (let [contexts (edn/read-string (fs/readFileSync
                   (path/join (path/dirname *file*) "fixtures/semantic-pr144-native-threads.edn") "utf8"))]
    (doseq [captured contexts]
      (let [native (:native-context captured)
            ns (get-in native [:thread :comments :nodes]) root (first ns) final (last ns)
            head (get-in native [:pr :headRefOid])
            t captured
            i (-> input (assoc :thread t)
                  (assoc-in [:policy :bot-identities] #{(:author final)})
                  (assoc-in [:sources 0 :revision] head))
            r (semantic/prepare i sha)
            o (-> (output r)
                  (assoc :quotes [{:kind "root" :id (str (:databaseId root)) :quote (:body root)}
                                  {:kind "verification" :id (str (:databaseId final)) :quote (subs (:body final) 0 (min 200 (count (:body final))))}
                                  {:kind "source" :id "src.clj" :quote source-text}]))
            e (-> (semantic/validate-result r o)
                  (assoc :execution (:execution (evidence input)))
                  (assoc-in [:execution :request-id] (:request-id r))
                  (assoc-in [:execution :response-digest] (sha (pr-str o)))
                  (assoc-in [:execution :started-at] "2026-10-25T15:00:00Z")
                  (assoc-in [:execution :completed-at] "2026-10-25T15:00:10Z"))]
        ;; Complete native thread/review/issue bytes, synthetic source/model.
        ;; This checks data acceptance only, never actual independent inference.
        (is (= :prepared (:status r)))
        (is (= (:body root) (get-in r [:payload :native-context :thread :comments :nodes 0 :body])))
        (is (= :candidate (:status e)))
        (is (some? (semantic/proof t e)))))))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when (pos? (+ (:fail m) (:error m))) (set! (.-exitCode js/process) 1)))
(run-tests 'test-semantic-repair)
