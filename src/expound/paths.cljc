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

(declare in-with-kps*)

(defn in-with-kps-maps-are-seqs [form val in in']
  (let [[k & rst] in
        [idx & rst2] rst]
    (cond
      (= ::not-found form)
      ::not-found

      (or
       (= val form)
       (= val (list form)))
      in'

      (empty? in)
      in'

      ;; detect a `:in` path that points to a key/value pair in a coll-of spec
      (and (map? form)
           (int? k)
           (< k (count (seq form)))
           #_(= val (nth (seq form) k))
           #_(empty? rst))
      (in-with-kps* (nth (seq form) k) val rst (conj in' (->KeyValuePathSegment k)))

      (and (map? form)
           (int? k)
           (int? idx)
           (or
            (= val (nth (nth (seq form) k) idx))
            (= val (list (nth (nth (seq form) k) idx))))
           #_(empty? rst))
      (in-with-kps* (nth (nth (seq form) k) idx) val rst2 (conj in' (->KeyValuePathSegment k) idx))

      (associative? form)
      (in-with-kps* (get form k ::not-found) val rst (conj in' k))

      (int? k)
      (in-with-kps* (nth form k ::not-found) val rst (conj in' k)))))

(defn in-with-kps-default [form val in in']
  (if (= form ::not-found)
    form
    (let [[k & rst] in
          [idx & rst2] rst]
      (cond
        (empty? in)
        in'

        ;; detect a `:in` path that points at a key in a map-of spec
        (and (map? form)
             (= 0 idx)
             (or
              ;; TODO - this seemed attractive, but doesn't fork for nested map

              (= val k)
              (not (and (associative? (get form k ::not-found))
                        (contains? (get form k ::not-found) idx)))))
        (conj in' (->KeyPathSegment k))

        ;; detect a `:in` path that points at a value in a map-of spec
        (and (map? form)
             (= 1 idx)
             ;; TODO - at some point, I wanted to check for the value
             ;; here, but that can't actually work, since the value
             ;; might be nested more deeply

             (or
              (= val (get form k ::not-found))
              (not (and (associative? (get form k ::not-found))
                        (contains? (get form k ::not-found) idx)))))
        (in-with-kps* (get form k ::not-found) val rst2 (conj in' k))

        ;; detect a `:in` path that points to a key/value pair in a coll-of spec
        (and (map? form)
             (int? k)
             (< k (count (seq form)))
             (= val (nth (seq form) k))
             #_(empty? rst))
        (in-with-kps* (nth (seq form) k) val rst (conj in' (->KeyValuePathSegment k)))

        (and (map? form)
             (int? k)
             (int? idx)
             (or
              (= val (nth (nth (seq form) k) idx))
              (= val (list (nth (nth (seq form) k) idx))))
             #_(empty? rst))
        (in-with-kps* (nth (nth (seq form) k) idx) val rst2 (conj in' (->KeyValuePathSegment k) idx))

        (associative? form)
        (in-with-kps* (get form k ::not-found) val rst (conj in' k))

        (int? k)
        (in-with-kps* (nth form k ::not-found) val rst (conj in' k))))))

(defn in-with-kps* [form val in in']
  ;; TODO cache results to not do this twice
  (if (not= ::not-found (in-with-kps-default form val in in'))
    (in-with-kps-default form val in in')
    (if (not= ::not-found (in-with-kps-maps-are-seqs form val in in'))
      (in-with-kps-maps-are-seqs form val in in')
      ::not-found)))

(defn in-with-kps [form val in in']
  ;; TODO cache results to not do this twice
  (if (= ::not-found (in-with-kps* form val in in'))
    (throw (ex-info "Can't convert path" {:form form
                                          :val val
                                          :in in
                                          :in' in'}))
    (in-with-kps* form val in in')))

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
