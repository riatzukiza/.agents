(ns pr-flow.inventory
  "Reviewer inventory loaded from reviewers.edn. Code asks this namespace which
   reviewer emits a format (a detector keyword); it never names a reviewer."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            #?(:cljs ["fs" :as fs])
            #?(:cljs ["path" :as path])
            #?(:cljs [nbb.core :as nbb])))

(def ^:private here #?(:clj *file* :cljs nbb/*file*))

(def data
  (edn/read-string
   #?(:clj (slurp (io/file (.getParentFile (io/file here)) "reviewers.edn"))
      :cljs (str (fs/readFileSync (path/join (path/dirname here) "reviewers.edn") "utf8")))))

(def reviewers (:reviewers data))

(def eligible
  "Every reviewer that counts toward quorum and unanimity."
  (set (for [[id r] reviewers :when (:counts r)] id)))

(def identities
  "{reviewer #{exact bot logins}}."
  (into {} (for [[id r] reviewers] [id (set (:identities r))])))

(defn has? [reviewer detector]
  (contains? (get-in reviewers [reviewer :detectors] #{}) detector))

(defn with-detector
  "The single reviewer emitting `detector`, or nil. Two owners is a data error."
  [detector]
  (let [ids (sort (for [[id r] reviewers :when (contains? (:detectors r) detector)] id))]
    (when (> (count ids) 1)
      (throw (ex-info "Reviewer inventory assigns one detector to two reviewers" {:detector detector :reviewers ids})))
    (first ids)))

(defn check-reviewer
  "Reviewer whose output this check row is, from exact tuples then bare names."
  [{:keys [name workflow]}]
  (or (first (for [[id r] reviewers :when (contains? (get-in r [:checks :tuples] #{}) [name workflow])] id))
      (first (for [[id r] reviewers :when (contains? (get-in r [:checks :names] #{}) name)] id))))

(defn check-names [reviewer] (get-in reviewers [reviewer :checks :names] #{}))
(defn primary-check-name [reviewer] (first (sort (check-names reviewer))))

(defn request-mention [reviewer] (get-in reviewers [reviewer :request :mention]))
(defn request-pattern [reviewer]
  (when-let [p (get-in reviewers [reviewer :request :pattern])] (re-pattern (str "(?i)" p))))
(def requestable (set (for [[id r] reviewers :when (get-in r [:request :mention])] id)))

(defn waived?
  "True when `repo` (owner/name) appears, by full name or bare name, in the
   reviewer's :waived-in set."
  [reviewer repo]
  (let [repo (str/lower-case (str repo))
        bare (last (str/split repo #"/"))
        waivers (set (map str/lower-case (get-in reviewers [reviewer :waived-in] #{})))]
    (boolean (or (waivers repo) (and (not (str/includes? (str bare) "/")) (some #(and (not (str/includes? % "/")) (= % bare)) waivers))))))

(defn waived-for [repo] (set (filter #(waived? % repo) (keys reviewers))))
(defn eligible-for [repo] (into #{} (remove #(waived? % repo)) eligible))

(def all-logins (set (mapcat val (merge identities (:non-reviewer-identities data)))))

(def default-request
  (first (sort (for [[id r] reviewers :when (get-in r [:request :default])] id))))
