(ns expound.util)

(defn nan? [x]
  #?(:clj (and (number? x) (Double/isNaN x))
     :cljs (and (number? x) (js/isNaN x))))
