(ns ^:no-doc expound.ansi
  (:require [clojure.string :as string]))

;; Copied from strictly-specking, since I see no reason
;; to deviate from the colors displayed in figwheel
;; https://github.com/bhauman/strictly-specking/blob/f102c9bd604f0c238a738ac9e2b1f6968fdfd2d8/src/strictly_specking/ansi_util.clj

(def sgr-code
  "Map of symbols to numeric SGR (select graphic rendition) codes."
  {:none        0
   :bold        1
   :underline   3
   :blink       5
   :reverse     7
   :hidden      8
   :strike      9
   :black      30
   :red        31
   :green      32
   :yellow     33
   :blue       34
   :magenta    35
   :cyan       36
   :white      37
   :fg-256     38
   :fg-reset   39
   :bg-black   40
   :bg-red     41
   :bg-green   42
   :bg-yellow  43
   :bg-blue    44
   :bg-magenta 45
   :bg-cyan    46
   :bg-white   47
   :bg-256     48
   :bg-reset   49})

(def ^:dynamic *enable-color* false)

(defn esc
  "Returns an ANSI escope string which will apply the given collection of SGR
  codes."
  [codes]
  (let [codes (map sgr-code codes codes)
        codes (string/join \; codes)]
    (str \u001b \[ codes \m)))

(defn escape
  "Returns an ANSI escope string which will enact the given SGR codes."
  [& codes]
  (esc codes))

(defn sgr
  "Wraps the given string with SGR escapes to apply the given codes, then reset
  the graphics."
  [string & codes]
  (str (esc codes) string (escape :none)))

(def ansi-code? sgr-code)

(def ^:dynamic *print-styles*
  {:highlight   [:bold]
   :good        [:green]
   :good-pred   [:green]
   :good-key    [:green]
   :bad         [:red]
   :bad-value   [:red]
   :error-key   [:red]
   :focus-key   [:bold]
   :correct-key [:green]
   :header      [:cyan]
   :footer      [:cyan]
   :warning-key [:bold]
   :focus-path  [:magenta]
   :message     [:magenta]
   :pointer     [:magenta]
   :none        [:none]})

(defn resolve-styles [styles]
  (if-let [res (not-empty
                (mapcat #(or
                          (when-let [res (*print-styles* %)]
                            res)
                          [%])
                        styles))]
    res
    ;; fall back to bright
    [:bold]))

(defn color [s & styles]
  (if *enable-color*
    (apply sgr s (resolve-styles styles))
    s))
