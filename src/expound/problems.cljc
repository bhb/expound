(ns expound.problems
  (:require [expound.paths :as paths]
            [clojure.spec.alpha :as s]))

(defn- adjust-in [form problem]
  (assoc problem :expound/in (paths/in-with-kps form (:in problem) [])))

(defn- adjust-path [failure problem]
  (assoc problem :expound/path
         (if (= :instrument failure)
           (vec (rest (:path problem)))
           (:path problem))))

(defn- add-spec [spec problem]
  (assoc problem :spec spec))

(defn add-caller [explain-data])

;;;;;;;;;;;;;;;;;;;;;;;;;; public ;;;;;;;;;;;;;;;;;;;;;;;;;

(defn leaf-only
  "Given a collection of problems, returns only those problems with data on the 'leaves' of the data"
  [problems]
  (let [paths-to-data (into #{} (map :expound/in problems))]
    (remove
     (fn [problem]
       (some
        (fn [path]
          (paths/prefix-path? (:expound/in problem) path))
        paths-to-data))
     problems)))

(defn annotate [explain-data]
  (let [{:keys [::s/problems ::s/value ::s/args ::s/ret ::s/fn ::s/failure ::s/spec]} explain-data
        caller (or (:clojure.spec.test.alpha/caller explain-data) (:orchestra.spec.test/caller explain-data))
        form (if (not= :instrument failure)
               value
               (cond
                 (contains? explain-data ::s/ret) ret
                 (contains? explain-data ::s/fn) fn
                 (contains? explain-data ::s/args) args))
        problems' (map (comp (partial adjust-in form)
                             (partial adjust-path failure)
                             (partial add-spec spec))
                       problems)]
    (assoc explain-data
           :expound/form form
           :expound/caller caller
           :expound/problems problems')))
