(ns ^:no-doc expound.alpha2.problems
  (:require [expound.alpha2.paths :as paths]
            [clojure.alpha.spec :as s])
  (:refer-clojure :exclude [type]))

;; can simplify when 
;; https://dev.clojure.org/jira/browse/CLJ-2192 or
;; https://dev.clojure.org/jira/browse/CLJ-2258 are fixed
(defn- adjust-in [form problem]
  ;; Three strategies for finding the value...
  (let [;; 1. Find the original value
        in1 (paths/in-with-kps form (:val problem) (:in problem) [])

        ;; 2. If value is unique, just find that, ignoring the 'in' path
        in2 (let [paths (paths/paths-to-value form (:val problem) [] [])]
              (if (= 1 (count paths))
                (first paths)
                nil))

        ;; 3. Find the unformed value (if there is an unformer)
        in3 (try
              (paths/in-with-kps form
                                 (s/unform (last (:via problem)) (:val problem))
                                 (:in problem) [])
              ;; The unform fails if there is no unformer
              ;; and the unform function could throw any type of
              ;; exception (it's provided by user)
              (catch #?(:cljs :default
                        :clj java.lang.Throwable) _e
                nil))
        new-in (cond in1
                     in1

                     in2
                     in2

                     in3
                     in3

                     (or (= '(apply fn) (:pred problem))
                         (#{:ret} (first (:path problem)))
                         
                         )
                     (:in problem)

                     :else
                     nil)]

    (assoc problem
           :expound/in
           new-in)))

(defn- adjust-path [failure problem]
  (assoc problem :expound/path
         (if (= :instrument failure)
           (vec (rest (:path problem)))
           (:path problem))))

(defn- add-spec [spec problem]
  (assoc problem :spec spec))

;; via is slightly different when using s/assert
(defn fix-via [spec problem]
  (if (= spec (first (:via problem)))
    (assoc problem :expound/via (:via problem))
    (assoc problem :expound/via (into [spec] (:via problem)))))

(defn ^:private missing-spec? [_failure problem]
  (= "no method" (:reason problem)))

(defn ^:private not-in-set? [_failure problem]
  (set? (:pred problem)))

(defn ^:private fspec-exception-failure? [failure problem]
  (and (not= :instrument failure)
       (not= :check-failed failure)
       (= '(apply fn) (:pred problem))))

(defn ^:private fspec-ret-failure? [failure problem]
  (and
   (not= :instrument failure)
   (not= :check-failed failure)
   (= :ret (last (:path problem)))))

(defn ^:private fspec-fn-failure? [failure problem]
  (and
   (not= :instrument failure)
   (not= :check-failed failure)
   (= :fn (last (:path problem)))))

(defn ^:private check-ret-failure? [failure problem]
  (and
   (= :check-failed failure)
   (= :ret (last (:path problem)))))

(defn ^:private check-fn-failure? [failure problem]
  (and (= :check-failed failure)
       (= :fn (last (:path problem)))))

(defn ^:private missing-key? [_failure problem]
  (let [pred (:pred problem)]
    (and (seq? pred)
         (< 2 (count pred))
         (s/valid?
          :expound.spec/contains-key-pred
          (nth pred 2)))))

(defn ^:private insufficient-input? [_failure problem]
  (contains? #{"Insufficient input"} (:reason problem)))

(defn ^:private extra-input? [_failure problem]
  (contains? #{"Extra input"} (:reason problem)))

(defn ^:private ptype [failure problem skip-locations?]
  (cond
    (:expound.spec.problem/type problem)
    (:expound.spec.problem/type problem)

    ;; This is really a location of a failure, not a failure type
    (and (not skip-locations?) (fspec-ret-failure? failure problem))
    :expound.problem/fspec-ret-failure

    (fspec-exception-failure? failure problem)
    :expound.problem/fspec-exception-failure

    ;; This is really a location of a failure, not a failure type
    ;; (compare to check-fn-failure, which is also an fn failure, but
    ;; at a different location)
    (and (not skip-locations?) (fspec-fn-failure? failure problem))
    :expound.problem/fspec-fn-failure

    ;; This is really a location of a failure, not a failure type
    (and (not skip-locations?) (check-ret-failure? failure problem))
    :expound.problem/check-ret-failure

    ;; This is really a location of a failure, not a failure type
    (and (not skip-locations?) (check-fn-failure? failure problem))
    :expound.problem/check-fn-failure

    (insufficient-input? failure problem)
    :expound.problem/insufficient-input

    (extra-input? failure problem)
    :expound.problem/extra-input

    (not-in-set? failure problem)
    :expound.problem/not-in-set

    (missing-key? failure problem)
    :expound.problem/missing-key

    (missing-spec? failure problem)
    :expound.problem/missing-spec

    :else
    :expound.problem/unknown))

;;;;;;;;;;;;;;;;;;;;;;;;;; public ;;;;;;;;;;;;;;;;;;;;;;;;;


(defn annotate [explain-data]
  (let [{:clojure.spec.alpha/keys [problems value args ret fn failure spec]} explain-data
        caller (or (:clojure.alpha.spec.test/caller explain-data) (:orchestra.spec.test/caller explain-data))
        form (if (not= :instrument failure)
               value
               (cond
                 (contains? explain-data :clojure.spec.alpha/ret) ret
                 (contains? explain-data :clojure.spec.alpha/args) args
                 (contains? explain-data :clojure.spec.alpha/fn) fn
                 :else (throw (ex-info "Invalid explain-data" {:explain-data explain-data}))))
        problems' (map (comp (partial adjust-in form)
                             (partial adjust-path failure)
                             (partial add-spec spec)
                             (partial fix-via spec)
                             #(assoc % :expound/form form)
                             #(assoc % :expound.spec.problem/type (ptype failure % false)))
                       problems)]

    (-> explain-data
        (assoc :expound/form form
               :expound/caller caller
               :expound/problems problems'))))

(def type ptype)

;; Must keep this function here because
;; spell-spec uses it
;; https://github.com/bhauman/spell-spec/blob/48ea2ca544f02b04a73dc42a91aa4876dcc5fc95/src/spell_spec/expound.cljc#L20
(def value-in paths/value-in)
