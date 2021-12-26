(ns ^:no-doc expound.util
  (:require [clojure.spec.alpha :as s]))

(def assert-message "Internal Expound assertion failed. Please report this bug at https://github.com/bhb/expound/issues")

(defn nan? [x]
  #?(:clj (and (number? x) (Double/isNaN x))
     :cljs (and (number? x) (js/isNaN x))))

(defn- parent-spec
  "Look up for the parent spec using the spec hierarchy."
  [k]
  (when-let [p (some-> k s/get-spec)]
    (or (when (qualified-ident? p) p)
        (s/form p))))

(defn spec-vals
  "Returns all spec keys or pred "
  ([spec-ident]
   (->> spec-ident
        (iterate parent-spec)
        (take-while some?))))
