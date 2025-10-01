(ns workspace.net.drilling.care.adapters.router
  "Care adapter for Ruuter - dead simple routing for everything
   Routes are data. Matching is simple. No magic, just maps.
   
   This is our universal router for:
   - Care method dispatch
   - Event routing  
   - Queue item routing
   - Command dispatch
   - Any map → handler routing"
  (:require [ruuter.core :as ruuter]))

;; NOTE FROM PAT
;; WTF is this terrible code?!
;; This ain't care dispatch.
;; You need to call 'name' on all three.

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; Route a map through routes
(defmethod care ["router" "route" "single"]
  [{:keys [router/routes router/input] :as m}]
  (if-let [result (ruuter/route routes input)]
    (assoc m 
      :router/matched? true
      :router/result result)
    (assoc m
      :router/matched? false
      :router/result nil)))

;; Try multiple route sets in order
(defmethod care ["router" "route" "fallthrough"]
  [{:keys [router/route-sets router/input] :as m}]
  (loop [sets route-sets]
    (if-let [routes (first sets)]
      (if-let [result (ruuter/route routes input)]
        (assoc m
          :router/matched? true
          :router/result result
          :router/matched-set routes)
        (recur (rest sets)))
      (assoc m
        :router/matched? false))))

;; Build routes from patterns
(defmethod care ["router" "build" "from-patterns"]
  [{:keys [router/patterns] :as m}]
  (let [routes (mapv (fn [{:keys [pattern handler]}]
                       {:path pattern
                        :response handler})
                 patterns)]
    (assoc m :router/routes routes)))

;; Match but don't execute - useful for testing
(defmethod care ["router" "match" "dry-run"]
  [{:keys [router/routes router/input] :as m}]
  (let [matched (filter #(ruuter/matches? (:path %) input) routes)]
    (assoc m
      :router/matched? (seq matched)
      :router/matches matched)))

;; Compose routes from multiple sources
(defmethod care ["router" "compose" "routes"]
  [{:keys [router/route-sources] :as m}]
  (let [routes (apply concat route-sources)]
    (assoc m :router/routes routes)))

;; Examples of actual routing patterns we'll use

(def care-routes
  "Routes for care method dispatch"
  [{:path ["templates" "render" :variant]
    :response :templates/render}
   
   {:path ["fs" :verb :path]
    :response :fs/operation}
   
   {:path ["ssh" "scp" :direction]
    :response :ssh/transfer}
   
   {:path ["shell" "exec" :mode]
    :response :shell/execute}])

(def event-routes
  "Routes for event handling"
  [{:path [:event/type "pressure" :event/severity]
    :response :handle/pressure-event}
   
   {:path [:event/type "temperature" :event/value]
    :response :handle/temperature-event}
   
   {:path [:event/type :any]
    :response :handle/generic-event}])

(def queue-routes
  "Routes for queue item processing"
  [{:path [:queue/type "drilling" :queue/priority "high"]
    :response :process/urgent-drilling}
   
   {:path [:queue/type "drilling" :any]
    :response :process/drilling}
   
   {:path [:queue/type :type]
    :response :process/generic}])

;; Helper to create a router-based care dispatcher
(defn make-care-dispatcher
  "Create a function that routes care maps to handlers"
  [routes]
  (fn [m]
    (let [route-key [(get m :care/adapter)
                     (get m :care/verb)
                     (get m :care/variant)]
          handler (ruuter/route routes route-key)]
      (if handler
        (handler m)
        (assoc m :care/error "No matching route")))))

;; Helper for event routing
(defn make-event-router
  "Create a function that routes events to handlers"
  [routes]
  (fn [event]
    (if-let [handler (ruuter/route routes event)]
      (handler event)
      (println "Unhandled event:" (:event/type event)))))

;; Helper for queue routing
(defn make-queue-processor
  "Create a function that routes queue items to processors"
  [routes]
  (fn [item]
    (if-let [processor (ruuter/route routes item)]
      (processor item)
      (assoc item :queue/status :no-handler))))
