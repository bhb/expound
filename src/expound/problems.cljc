(ns expound.problems
  (:require [expound.paths :as paths]))

(defn adjust-in [form problem]
  (assoc problem :expound/in (paths/in-with-kps form (:in problem) [])))

(defn adjust-path [failure problem]
  (assoc problem :expound/path
         (if (= :instrument failure)
           (vec (rest (:path problem)))
           (:path problem))))

(defn add-spec [spec problem]
  (assoc problem :spec spec))

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

(defn annotate [form failure spec problems]
  (map (comp (partial adjust-in form)
             (partial adjust-path failure)
             (partial add-spec spec))
       problems))
