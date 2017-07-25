(ns user
  (:require [clojure.spec.alpha :as s]
            [orchestra.spec.test :as st]
            [expound.alpha :as expound]))

(defn setup []
  (set! s/*explain-out* expound/printer)
  (st/instrument))

(comment
  (setup))
