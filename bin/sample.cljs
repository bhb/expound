(ns sample
  (:require [expound.alpha :as expound])
  )

(expound/def ::foo int? "hi")

(println "Hello world!")
