(ns net.drilling.authors.claude.simple-care-ring
  "The simplest possible CARE Ring middleware.
   Just the core idea, nothing extra."
  (:require [net.drilling.modules.care.core :as care]
            [ring.adapter.jetty :as jetty]))

;; =============================================================================
;; The Core Idea: Ring handlers return CARE maps instead of doing side effects
;; =============================================================================

;; Your app state
(def state (atom {:counter 0}))

;; A CARE method that handles incrementing
(defmethod care/care-mm ["counter" "increment" "default"]
  [{:keys [state] :as m}]
  (-> m
    (update-in [:state :counter] inc)
    (assoc :response {:status 200 
                      :body {:counter (inc (:counter state))}})))

;; A CARE method that handles getting the count
(defmethod care/care-mm ["counter" "get" "default"]
  [{:keys [state] :as m}]
  (assoc m :response {:status 200
                      :body {:counter (:counter state)}}))

;; The simplest possible CARE middleware
(defn wrap-care-simple
  "Makes Ring handlers that return CARE maps work."
  [handler state-atom]
  (fn [request]
    ;; Handler returns a CARE map
    (let [care-map (handler request)]
      
      ;; If it's a CARE map, process it
      (if (:care/adapter care-map)
        (let [;; Add current state
              care-map (assoc care-map :state @state-atom)
              
              ;; Run through CARE
              result (care/care-mm care-map)]
          
          ;; Update state if it changed
          (when (not= @state-atom (:state result))
            (reset! state-atom (:state result)))
          
          ;; Return the response
          (:response result))
        
        ;; Not a CARE map - just return it
        care-map))))

;; Your handler - just returns data!
(defn my-handler [request]
  (case (:uri request)
    "/increment" {:care/adapter :counter
                  :care/verb :increment}
    
    "/count" {:care/adapter :counter
              :care/verb :get}
    
    ;; Default
    {:status 404 :body "Not found"}))

;; Wire it up
(def app
  (wrap-care-simple my-handler state))

;; Start server
(defn start []
  (jetty/run-jetty app {:port 3000 :join? false})
  (println "Try: curl http://localhost:3000/count")
  (println "     curl -X POST http://localhost:3000/increment"))

(comment
  ;; Run this:
  (start)
  
  ;; Then in terminal:
  ;; curl http://localhost:3000/count
  ;; => {"counter": 0}
  
  ;; curl -X POST http://localhost:3000/increment  
  ;; => {"counter": 1}
  
  ;; curl http://localhost:3000/count
  ;; => {"counter": 1}
  )