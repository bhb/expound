(ns expound.specs
  (:require #?(:cljs [expound.alpha :as ex :include-macros true]
               :clj [expound.alpha :as ex])))

;;;; public specs ;;;;;;

(ex/def ::bool boolean? "should be either true or false")
#?(:clj (ex/def ::bytes bytes? "should be an array of bytes"))
(ex/def ::double double? "should be a double")
(ex/def ::ident ident? "should be an identifier (a symbol or keyword)")
(ex/def ::indexed indexed? "should be an indexed collection")
(ex/def ::int int? "should be an integer")
(ex/def ::kw keyword? "should be a keyword")
(ex/def ::map map? "should be a map")
(ex/def ::nat-int nat-int? "should be an integer equal to, or greater than, zero")
(ex/def ::neg-int neg-int? "should be a negative integer")
(ex/def ::pos-int pos-int? "should be a positive integer")
(ex/def ::qualified-ident qualified-ident? "should be an identifier (a symbol or keyword) with a namespace")
(ex/def ::qualified-kw qualified-keyword? "should be a keyword with a namespace")
(ex/def ::qualified-sym qualified-symbol? "should be a symbol with a namespace")
(ex/def ::seqable seqable? "should be a seqable collection")
(ex/def ::simple-ident simple-ident? "should be an identifier (a symbol or keyword) with no namespace")
(ex/def ::simple-kw simple-keyword? "should be a keyword with no namespace")
(ex/def ::simple-sym simple-symbol? "should be a symbol with no namespace")
(ex/def ::str string? "should be a string")
(ex/def ::sym symbol? "should be a symbol")
(ex/def ::uri uri? "should be a URI")
(ex/def ::uuid uuid? "should be a UUID")
(ex/def ::vec vector? "should be a vector")

(def ^:no-doc public-specs
  [::bool #?(:clj ::bytes) ::double ::ident ::indexed ::int ::kw
   ::map ::nat-int ::neg-int ::pos-int ::qualified-ident
   ::qualified-kw ::qualified-sym ::seqable ::simple-ident
   ::simple-kw ::simple-sym ::str ::sym ::uuid ::uri ::vec])
