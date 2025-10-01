(ns workspace.net.drilling.care.adapters.http
  "Care adapter for http-kit - just the server, nothing else
   One pattern: run-server with handler and options"
  (:require [org.httpkit.server :as server]))

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; Start server
(defmethod care ["http" "server" "start"]
  [{:keys [http/handler http/port http/options] :as m}]
  (let [opts (merge {:port (or port 8080)} options)
        stop-fn (server/run-server handler opts)]
    (assoc m 
      :http/stop-fn stop-fn
      :http/running? true
      :http/port (:port opts))))

;; Stop server
(defmethod care ["http" "server" "stop"]
  [{:keys [http/stop-fn http/timeout] :as m}]
  (when stop-fn
    (stop-fn :timeout (or timeout 100)))
  (assoc m
    :http/running? false
    :http/stop-fn nil))

;; Simple handler wrapper for care
(defmethod care ["http" "handler" "wrap"]
  [{:keys [http/care-handler] :as m}]
  (let [handler (fn [request]
                  (care (assoc care-handler :http/request request)))]
    (assoc m :http/handler handler)))