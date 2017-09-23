(ns expound.paths
  (:require [clojure.spec.alpha :as s]))

;;;;;; specs ;;;;;;

(s/def :expound/path sequential?)

;;;;;; types ;;;;;;

(defrecord KeyPathSegment [key])

(defrecord KeyValuePathSegment [idx])

;;;;;;;;;;;;;;;;;;;

(defn kps? [x]
  (instance? KeyPathSegment x))

(defn kvps? [x]
  (instance? KeyValuePathSegment x))

(s/fdef prefix-path?
        :args (s/cat
               :partial-path :expound/path
               :partial-path :expound/path)
        :ret boolean?)
(defn prefix-path?
  "True if partial-path is a prefix of full-path."
  [partial-path full-path]
  (and (< (count partial-path) (count full-path))
       (= partial-path
          (subvec full-path 0 (count partial-path)))))

(s/fdef kps-path?
        :args (s/cat :x any?)
        :ret boolean?)
(defn kps-path?
  "True if path points to a key"
  [x]
  (boolean (and (vector? x)
                (kps? (last x)))))

(s/fdef kvps-path?
        :args (s/cat :x any?)
        :ret boolean?)
(defn kvps-path?
  "True if path points to a key/value pair"
  [x]
  (boolean (and (vector? x)
                (some kvps? x))))

(defn in-with-kps [form in in']
  (let [[k & rst] in
        [idx & rst2] rst]
    (cond
      (empty? in)
      in'

      ;; detect a `:in` path that points at a key in a map-of spec
      (and (map? form)
           (= 0 idx)
           (not (and (associative? (get form k ::not-found))
                     (contains? (get form k ::not-found) idx))))
      (conj in' (->KeyPathSegment k))

      ;; detect a `:in` path that points at a value in a map-of spec
      (and (map? form)
           (= 1 idx)
           (not (and (associative? (get form k ::not-found))
                     (contains? (get form k ::not-found) idx))))
      (recur (get form k ::not-found) rst2 (conj in' k))

      ;; detech a `:in` path that points to a key/value pair in a coll-of spec
      (and (map? form) (int? k) (empty? rst))
      (conj in' (->KeyValuePathSegment k))

      (associative? form)
      (recur (get form k ::not-found) rst (conj in' k))

      (int? k)
      (recur (nth form k ::not-found) rst (conj in' k)))))

(defn compare-path-segment [x y]
  (cond
    (and (int? x) (kvps? y))
    (compare x (:idx y))

    (and (kvps? x) (int? y))
    (compare (:idx x) y)

    (and (kps? x) (not (kps? y)))
    -1

    (and (not (kps? x)) (kps? y))
    1

    (and (vector? x) (vector? y))
    (first (filter #(not= 0 %) (map compare-path-segment x y)))

    :else
    (compare x y)))

(defn compare-paths [path1 path2]
  (first (filter #(not= 0 %) (map compare-path-segment path1 path2))))
