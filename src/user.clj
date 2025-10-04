(ns user
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.walk :as walk])

  (:import (java.time Instant)
           (java.util UUID)))

(def purple 84)

(defn greet [name]
  (str "Hello, " (str/capitalize name) "! Welcome back, " name "!"))

(greet "Pat")