(ns user
  (:require [clojure.spec.alpha :as s]
            [orchestra.spec.test :as st]
            [expound.alpha :as expound]
            [speculative.instrument :as i]))

(defn setup []
  (set! s/*explain-out* expound/printer)
  (st/instrument)
  (i/instrument)
  )

(defn unsetup []
  (set! s/*explain-out* s/explain-printer)
  (st/unstrument)
  (i/unstrument)
  )

(comment
  (setup))
