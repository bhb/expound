(ns hooks.checking
  (:require [clj-kondo.hooks-api :as api]))

(defn checking [{:keys [:node]}]
  (let [[name num-tests binding-vec & body] (rest (:children node))
        bindings (->> (:children binding-vec)
                      (partition 2)
                      (mapcat (fn [[k v]]
                                (if (contains? #{:let
                                                 :parallel} (:k k))
                                  (:children v)
                                  [k v])))
                      vec)
        new-node (api/list-node
                  (list*
                   (api/token-node 'let*)
                   (api/vector-node bindings)
                   name
                   num-tests
                   body))]
    {:node new-node}))
