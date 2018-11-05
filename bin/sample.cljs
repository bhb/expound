(ns sample
  (:require [expound.alpha :as expound :include-macros true]))

(expound/def ::foo int? "some kind of integer")

(expound/expound ::foo "test str")
