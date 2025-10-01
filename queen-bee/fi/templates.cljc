(ns workspace.net.drilling.care.adapters.templates
  "Care adapter for Selmer template rendering
   Simple map → template → string transformations"
  (:require [selmer.parser :as parser]))

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; Render templates with data
(defmethod care ["templates" "render" "inline"]
  [{:keys [template/source template/data] :as m}]
  (assoc m :template/output (parser/render source data)))

(defmethod care ["templates" "render" "file"]
  [{:keys [template/path template/data] :as m}]
  (assoc m :template/output (parser/render-file path data)))

;; Validate template syntax
(defmethod care ["templates" "validate" "syntax"]
  [{:keys [template/source] :as m}]
  (try 
    (parser/parse source)
    (assoc m :template/valid? true)
    (catch Exception e
      (assoc m 
        :template/valid? false
        :template/error (.getMessage e)))))