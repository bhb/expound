(ns sample
  (:require [expound.alpha :as expound :include-macros true])
  )

(expound/def ::foo int? "hi")

(println "Hello world!")
