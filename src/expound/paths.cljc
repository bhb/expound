(ns ^:no-doc expound.paths
  (:require [clojure.spec.alpha :as s]
            [expound.util :as util]))

;;;;;; specs ;;;;;;

(s/def :expound/path (s/or
                      :not-found #{::not-found-path}
                      :found sequential?))

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

(defn fn-equal [x y]
  (and (fn? x)
       (fn? y)
       (= (pr-str x)
          (pr-str y))))

(defn both-nan? [x y]
  (and (util/nan? x)
       (util/nan? y)))

(defn equalish? [x y]
  (or
   (= x y)
   (fn-equal x y)
   (both-nan? x y)))

(defn in-with-kps-maps-as-seqs [form val in in']
  (let [[k & rst] in
        [idx & rst2] rst]
    (cond
      (= ::not-found form)
      ::not-found

      (and (empty? in)
           (equalish? form val))
      in'

      ;; detect a `:in` path that points to a key/value pair in a coll-of spec
      (and (map? form)
           (nat-int? k)
           (< k (count (seq form))))
      (in-with-kps* (nth (seq form) k) val rst (conj in' (->KeyValuePathSegment k)))

      (and (map? form)
           (nat-int? k)
           (int? idx)
           (< k (count (seq form)))
           (< idx (count (nth (seq form) k))))
      (in-with-kps* (nth (nth (seq form) k) idx) val rst2 (conj in' (->KeyValuePathSegment k) idx))

      :else
      ::not-found)))

(defn in-with-kps-fuzzy-match-for-regex-failures [form val in in']
  (if (= form ::not-found)
    form
    (let [[k & rst] in]
      (cond
        ;; not enough input
        (and (empty? in)
             (seqable? form)
             (= val '()))
        in'

        ;; too much input
        (and (empty? in)
             (and (seq? val)
                  (= form
                     (first val))))
        in'

        (and (nat-int? k) (seqable? form))
        (in-with-kps* (nth (seq form) k ::not-found) val rst (conj in' k))

        :else
        ::not-found))))

(defn in-with-kps-ints-are-keys [form val in in']
  (if (= form ::not-found)
    form
    (let [[k & rst] in]
      (cond
        (and (empty? in)
             (equalish? form val))
        in'

        (associative? form)
        (in-with-kps* (get form k ::not-found) val rst (conj in' k))

        (and (int? k) (seqable? form))
        (in-with-kps* (nth (seq form) k ::not-found) val rst (conj in' k))

        :else
        ::not-found))))

(defn in-with-kps-ints-are-key-value-indicators [form val in in']
  (if (= form ::not-found)
    form
    (let [[k & rst] in
          [idx & rst2] rst]
      (cond
        (and (empty? in)
             (equalish? form val))
        in'

        ;; detect a `:in` path that points at a key in a map-of spec
        (and (map? form)
             (= 0 idx))
        (in-with-kps* k val rst2 (conj in' (->KeyPathSegment k)))

        ;; detect a `:in` path that points at a value in a map-of spec
        (and (map? form)
             (= 1 idx))
        (in-with-kps* (get form k ::not-found) val rst2 (conj in' k))

        :else
        ::not-found))))

(defn in-with-kps* [form val in in']
  (if (fn? form)
    in'
    (let [br1 (in-with-kps-ints-are-key-value-indicators form val in in')]
      (if (not= ::not-found br1)
        br1
        (let [br2 (in-with-kps-maps-as-seqs form val in in')]
          (if (not= ::not-found br2)
            br2
            (let [br3 (in-with-kps-ints-are-keys form val in in')]
              (if (not= ::not-found br3)
                br3
                (let [br4 (in-with-kps-fuzzy-match-for-regex-failures form val in in')]
                  (if (not= ::not-found br4)
                    br4
                    ::not-found))))))))))

(defn paths-to-value [form val path paths]
  (cond
    (= form val)
    (conj paths path)

    (or (sequential? form)
        (set? form))
    (reduce
     (fn [ps [x i]]
       (paths-to-value x val (conj path i) ps))
     paths
     (map vector form (range)))

    (map? form) (reduce
                 (fn [ps [k v]]
                   (->> ps
                        (paths-to-value k val (conj path (->KeyPathSegment k)))
                        (paths-to-value v val (conj path k))))
                 paths
                 form)

    :else paths))

(defn in-with-kps [form val in in']
  (let [res (in-with-kps* form val in in')]
    (if (= ::not-found res)
      ::not-found-path
      res)))

(declare compare-paths)

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
    (compare-paths x y)

    :else
    (compare x y)))

(defn compare-paths [path1 path2]
  (->> (map compare-path-segment path1 path2)
       (remove #{0})
       first))
