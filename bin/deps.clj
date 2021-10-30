(require '[clojure.xml :as xml] )

(let [deps (->> (xml/parse "../pom.xml")                               ; read pom
                :content                                               ; get top-level tags
                (filter #(= (:tag %) :dependencies))                   ; find dependencies
                first                                                  ; get tag
                :content                                               ; get children
                (map :content)                                         ; get children's children
                (remove (fn [dep-tags]                                 ; find anything not in 'test' scope
                          (some #(and (= (:tag %) :scope)
                                      (= (:content %) ["test"])) dep-tags)))
                (map (fn [dep-tags]                                    ; pull out group/name of dependency
             [(:content
                 (first
                  (filter #(= (:tag %) :groupId) dep-tags)))
              (:content
                (first
                 (filter #(= (:tag %) :artifactId) dep-tags)))]))

                sort                                                   ; sort
                )]
  (doseq [dep deps]
    (prn dep)))
