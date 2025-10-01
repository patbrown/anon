(ns workspace.net.drilling.care.adapters.sente
  "Care adapter for Sente - WebSocket + Ajax fallback with CSRF protection
   Symmetric client/server messaging with care dispatch on both sides"
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :as sente-http-kit]))

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; SERVER SIDE

;; Create server channel socket
(defmethod care ["sente" "server" "create"]
  [{:keys [sente/user-id-fn sente/csrf-token-fn sente/options] :as m}]
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket-server!
          (sente-http-kit/get-sch-adapter)
          (merge {:user-id-fn (or user-id-fn (fn [req] (:client-id req)))
                  :csrf-token-fn (or csrf-token-fn (fn [req] (:csrf-token req)))}
            options))]
    (assoc m
      :sente/ch-recv ch-recv ; ChannelSocket's receive channel
      :sente/send-fn send-fn ; Send function
      :sente/connected-uids connected-uids ; Watchable atom of connected users
      :sente/ajax-post-fn ajax-post-fn ; Ring handler for POST
      :sente/ajax-get-fn ajax-get-or-ws-handshake-fn))) ; Ring handler for GET

;; Start server event router
(defmethod care ["sente" "server" "start-router"]
  [{:keys [sente/ch-recv sente/event-handler] :as m}]
  (let [stop-fn (sente/start-server-chsk-router!
                  ch-recv
                  (or event-handler 
                    (fn [event] (care (assoc m :sente/event event)))))]
    (assoc m 
      :sente/router-stop-fn stop-fn
      :sente/router-running? true)))

;; Send to specific user
(defmethod care ["sente" "server" "send"]
  [{:keys [sente/send-fn sente/user-id sente/event] :as m}]
  (send-fn user-id event)
  (assoc m :sente/sent? true))

;; Broadcast to all connected users
(defmethod care ["sente" "server" "broadcast"]
  [{:keys [sente/send-fn sente/connected-uids sente/event] :as m}]
  (doseq [uid (:any @connected-uids)]
    (send-fn uid event))
  (assoc m :sente/broadcast-count (count (:any @connected-uids))))

;; CLIENT SIDE (ClojureScript)

;; Create client channel socket
(defmethod care ["sente" "client" "create"]
  [{:keys [sente/path sente/csrf-token sente/options] :as m}]
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket-client!
          (or path "/chsk")
          csrf-token
          (merge {:type :auto} options))]
    (assoc m
      :sente/chsk chsk ; ChannelSocket object
      :sente/ch-recv ch-recv ; Receive channel 
      :sente/send-fn send-fn ; Send function
      :sente/state state))) ; Watchable atom of connection state

;; Start client event router
(defmethod care ["sente" "client" "start-router"]
  [{:keys [sente/ch-recv sente/event-handler] :as m}]
  (let [stop-fn (sente/start-client-chsk-router!
                  ch-recv
                  (or event-handler
                    (fn [event] (care (assoc m :sente/event event)))))]
    (assoc m
      :sente/router-stop-fn stop-fn
      :sente/router-running? true)))

;; Send event to server
(defmethod care ["sente" "client" "send"]
  [{:keys [sente/send-fn sente/event sente/timeout sente/callback] :as m}]
  (if callback
    (send-fn event (or timeout 8000) callback)
    (send-fn event))
  (assoc m :sente/sent? true))

;; SHARED EVENT DISPATCH

;; Route Sente events through care
(defmethod care ["sente" "event" "dispatch"]
  [{:keys [sente/event sente/routes] :as m}]
  (let [{:keys [id ?data event]} event
        route-key (if (vector? id) id [id])
        handler (get routes route-key)]
    (if handler
      (handler (assoc m :event/id id :event/data ?data))
      (assoc m :event/unhandled id))))

;; Example routes for Sente events
(def sente-routes
  {[:chsk/state] handle-connection-state
   [:chsk/recv] handle-received-message
   [:drilling/pressure] handle-pressure-event
   [:drilling/alert] handle-drilling-alert})

;; Helper to create event handler with care dispatch
(defn make-sente-handler
  "Create an event handler that routes through care"
  [routes]
  (fn [{:keys [id ?data] :as event}]
    (care {:care/adapter "sente"
           :care/verb "event"
           :care/variant "dispatch"
           :sente/event event
           :sente/routes routes})))