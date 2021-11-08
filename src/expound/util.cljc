(ns ^:no-doc expound.util
  (:require [clojure.spec.alpha :as s]))

(def assert-message "Internal Expound assertion failed. Please report this bug at https://github.com/bhb/expound/issues")

(defn nan? [x]
  #?(:clj (and (number? x) (Double/isNaN x))
     :cljs (and (number? x) (js/isNaN x))))

;; some utils for spec walking

(defn- accept-ident [x]
  (when (qualified-ident? x)
    x))

(defn- parent-spec
  "Look up for the parent spec using the spec hierarchy."
  [k]
  (or (accept-ident (s/get-spec k))
      (some-> k s/get-spec s/form)))

(defn spec-vals
  "Returns all spec keys or pred "
  ([spec-ident]
   (->> spec-ident
        (iterate parent-spec)
        (take-while some?))))
