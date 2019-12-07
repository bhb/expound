(ns expound.comparison
  "Generate markdown for comparison doc"
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [marge.core :as marge]
            [clojure.string :as string]))

(def examples
  [
   {
    :header "Nested data structures"
    :description "If the invalid value is nested, Expound will help locate the problem"
    :specs `((s/def :db/id pos-int?)
             (s/def :db/ids (s/coll-of :db/id))
             (s/def :app/request (s/keys :req-un [:db/ids]))
             )
    :spec :app/request
    :value {:ids [123 "456" 789]}
    }
   {
    :header "Missing keys"
    :description "If a key is missing from a map, Expound will display the associated spec"
    :specs `((s/def :address.west-coast/city string?)
             (s/def :address.west-coast/state #{"CA" "OR" "WA"})
             (s/def :app/address (s/keys :req-un [:address.west-coast/city :address.west-coast/state]))
             )
    :spec :app/address
    :value {}
    }
   {
    :header "Set-based specs"
    :description "If a value doesn't match a set-based spec, Expound will list the possible values"
    :specs `((s/def :address.west-coast/city string?)
             (s/def :address.west-coast/state #{"CA" "OR" "WA"})
             (s/def :app/address (s/keys :req-un [:address.west-coast/city :address.west-coast/state]))
             )
    :spec :app/address
    :value {:city "Seattle" :state "ID"}
    }
   {
    :header "Grouping"
    :description "Expound will group alternatives"
    :specs `((s/def :address.west-coast/zip (s/or :str string? :num pos-int?))
             )
    :spec :address.west-coast/zip
    :value :98109
    }
   {
    :header "Predicate descriptions"
    :description "If you provide a predicate description, Expound will display them"
    :specs '((def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
             (defn valid-email? [s] (re-matches email-regex s))
             (s/def :app.user/email (s/and string? valid-email?))
             (expound/defmsg :app.user/email "should be a valid email address")
             )
    :spec :app.user/email
    :value "@example.com"
    }

   {
    :header "Too few elements in a sequence"
    :description "If you are missing elements, Expound will describe what must come next"
    :specs `(
             (s/def :app/ingredient (s/cat :quantity number? :unit keyword?))
             )
    :spec :app/ingredient
    :value [100]
    }

   {
    :header "Too many elements in a sequence"
    :description "If you have extra elements, Expound will point out which elements should be removed"
    :specs `(
             (s/def :app/ingredient (s/cat :quantity number? :unit keyword?))
             )
    :spec :app/ingredient
    :value [100 :teaspoon :sugar]
    }
   ]
  )

(defn example-with-output [example]
  (doseq [form (:specs example)]
    (eval form)
    )
  (merge
   example
   {
    :spec-output (s/explain-str (:spec example) (:value example))
    :expound-output (expound/expound-str (:spec example) (:value example) {:print-specs? false})
    })
  )

(defn code-block [forms]
  [:code
   {:clojure
    (str (->> forms
              (map pr-str )
              (map #(string/replace % "clojure.spec.alpha" "s"))
              (string/join "\n")
              )
         )}])

(defn markdown-all [sections]
  (->> sections
       (mapcat identity)
       (map marge/markdown)
       (string/join "\n\n")))

(defn formatted-example [example]
  (let [{:keys [spec-output expound-output specs value header description]} (example-with-output example)]
    [
     [:hr]
     [:h3 header]
     [:p description]

     [:h4 "Specs"]
     (code-block specs)
     
     [:h4 "Value"]
     (code-block [value])

     [:h4 "`clojure.spec` message"]
     [:code (string/trim-newline spec-output)]

     [:h4 "Expound message"]
     [:code (string/trim-newline expound-output)]]))

(println
 (str
  (marge/markdown [:h1 "Comparison"])
  "\n"
  (marge/markdown
   [:p "Expound's error messages are more verbose than the `clojure.spec` messages, which can help you quickly determine why a value fails a spec. Here are some examples."]
   )
  "\n\n"
  (markdown-all (map formatted-example examples))))
