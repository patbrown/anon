(ns user
  (:require [clojure.repl.deps :as deps]))

(deps/add-libs '{no.cjohansen/dataspex {:mvn/version "2025.06.8"}})
;;(deps/add-libs '{ring/ring {:mvn/version "1.15.3"}})???
(require '[dataspex.core :as dsp])

(defonce p (atom {:messages []}))
(defonce f (atom {:messages []}))
(defonce scratchpad (atom {:messages []}))
(defonce thoughts (atom {:messages []}))


(def comms-map
  {:p p
   :f f
   :s scratchpad
   :t thoughts})

(defn message! [person-shortcode message]
  (let [a (get comms-map  person-shortcode)]
    (swap! a (fn [m] (update m :messages conj message)))))

(def message message!)

(defn think! [thought]
  (swap! thoughts (fn [m] (update m :messages conj thought))))

(def think think!)

(defn note! [note-keyword message]
  (swap! scratchpad assoc note-keyword message))

(defn get-messages [person-shortcode]
  (:messages @(get comms-map person-shortcode)))

(defn get-last-message [person-shortcode]
  (last (get-messages person-shortcode)))

(defn scratchpad-keys []
  (keys @scratchpad))

(keys @scratchpad)

(def something-random {:care (slurp "src/care.cljc")
                       :care-tags (slurp "src/care_tags.cljc")
                       :care-tests (slurp "test/care_test.cljc")
                       :care-tags-tests (slurp "test/care_tags_test.cljc")})
