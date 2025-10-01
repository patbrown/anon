(ns care-basic
  "CARE - The only multimethod you need.
   
   ## The CARE MM
   CARE dispatches on [adapter verb variant] where:
   - Adapter is ANY namespace/context (not just storage!)
   - Verb is what to do (add/change/remove/employ/send/render/etc.)
     - Try to stick to the four main ones, i.e.
       employ render, but also be descriptive. balance
   - Variant is how to do it
   
   ## The Key Insight
   Adapters are dispatch keys, NOT datastores. They can trigger ANY behavior:
   - :users/jam → manipulate user data
   - :email/smtp → send emails (side effect!)
   - :dom/react → render components (side effect!)
   - :log/console → write logs (side effect!)
   
   ## The Power of *care
   ```clojure
   (def send-email (*care {:care/adapter :email/smtp
                           :care/verb :send
                           :care/variant :default}))
   ;; Now just call: (send-email {:email/to \"user@example.com\" :email/body \"Hello\"})
   ```
   
   ## Remember
   - Side effects can/should happen IN the method implementations
   - Tags are just named reusable partial care-maps
   - NEXUS is extra complications - CARE tags = Nexus effect/action/placeholder/system->state
   - It's all just one multimethod"
  (:require [metastructures]
            [orchestra.core #?(:clj :refer :cljs :refer-macros) [defn-spec]]
            [just-a-map :as jam]
            [append-only-log :as log]
            [normalized-database :as ndb]))

;; =============================================================================
;; The Core Multimethod
;; =============================================================================

(defmulti care-mm
  "The universal multimethod for ALL operations.
   
   Dispatches on [adapter verb variant] extracted from the map.
   
   ## Adapters Can Be Anything
   - Data operations: :users/jam, :posts/ndb, :events/log
   - Side effects: :email/smtp, :sms/twilio, :push/fcm
   - UI operations: :dom/react, :canvas/2d, :webgl/three
   - System operations: :bb/fs, :bb/process, :metrics/prometheus
   
   ## Common Verbs (but make your own!)
   - Data: add, change, remove, employ
   - Communication: send, receive, broadcast
   - UI: render, mount, unmount, update
   - System: read, write, execute, measure
   
   ## Variants
   Domain-specific strategies for the verb.
   
   ## Return Contract, CARE returns maps maps
   ALWAYS returns a map (possibly the same one, transformed).
   Side effects happen but the map flows through.
   
   ## Example
   ```clojure
   ;; Data operation
   (care-mm {:care/adapter :users/jam
             :care/verb :add
             :care/variant :default
             :care/args [[\"alice\"] {:name \"Alice\"}]})
   
   ;; Side effect operation  
   (care-mm {:care/adapter :email/smtp
             :care/verb :send
             :care/variant :welcome
             :email/to \"alice@example.com\"
             :email/user {:name \"Alice\"}})
   ```"
  (fn [{:care/keys [adapter verb variant] :or {variant "default"}}]
    (if (and adapter verb)
      [(name adapter)
       (name verb)
       (name variant)]
      [:invalid :invalid :invalid])))

;; Default - returns empty map to maintain contract
(defmethod care-mm :default [_]
  {})

;; =============================================================================
;; The *care Pattern - Partial Application
;; =============================================================================

(defn-spec *care :metastructures/fn
  "Create a specialized CARE function with defaults baked in.
   
   This is the secret sauce for making domain-specific functions.
   
   ## Examples
   ```clojure
   ;; Create specialized functions
   (def save-user (*care {:care/adapter :users/jam
                          :care/verb :jam/add
                          :care/variant :default}))
   
   (def send-email (*care {:care/adapter :email/smtp
                           :care/verb :email/send
                           :care/variant :default}))
   
   (def render-component (*care {:care/adapter :dom/react
                                 :care/verb :component/render
                                 :care/variant :default}))
   
   ;; Use them simply
   (save-user {:care/args [[\"alice\"] {:name \"Alice\"}]})
   (send-email {:to \"alice@example.com\" :subject \"Welcome\"})
   (render-component {:component MyApp :container \"#root\"})
   ```"
  [defaults :metastructures/map]
  (fn [overrides]
    (care-mm (merge defaults overrides))))

;; Alias for convenience
(defn-spec care :metastructures/map
  "CARE operations. Merges all argument maps together and processes.
   - (care care-map) - processes the care map
   - (care context-map care-op) - merges care-op into context and processes
   - (care m1 m2 m3 ...) - merges all maps and processes
   
   Always returns a map as per CARE contract."
  [& args :metastructures/map]
  (care-mm (apply merge args)))

;; =============================================================================
;; Utility Functions
;; =============================================================================

(defn-spec extract-result :metastructures/any
  "Extract a specific key from CARE result, or return the whole map.
   Useful for getting just what you need from a CARE operation.
   
   ```clojure
   (-> {:care/adapter :users/jam :care/verb :employ ...}
       care-mm
       (extract-result :user/id))
   ```"
  [care-map :metastructures/map result-key :metastructures/any]
  (if result-key
    (get care-map result-key)
    care-map))



;; =============================================================================
;; Data Operation Methods (JAM - Just A Map)
;; =============================================================================
;; ### CARE NDB INTERFACE

(defmethod care-mm ["ndb" "change" "default"] [m]
  "NDB change using default strategy"
  (ndb/change-default m))

(defmethod care-mm ["ndb" "change" "follow"] [m]
  "NDB change using follow strategy"
  (ndb/change-follow m))

(defmethod care-mm ["ndb" "add" "default"] [m]
  "NDB add using default normalization"
  (ndb/add-ndb m))

(defmethod care-mm ["ndb" "add" "follow"] [m]
  "NDB add using follow normalization"
  (ndb/add-follow m))

(defmethod care-mm ["ndb" "rm" "default"] [m]
  "NDB remove using default strategy"
  (ndb/rm-ndb m))

(defmethod care-mm ["ndb" "rm" "follow"] [m]
  "NDB remove using follow strategy"
  (ndb/rm-follow m))

(defmethod care-mm ["ndb" "employ" "default"] [m]
  "NDB employ using default strategy"
  (ndb/employ-ndb m))

(defmethod care-mm ["ndb" "employ" "follow"] [m]
  "NDB employ using follow strategy"
  (ndb/employ-follow m))

(defmethod care-mm ["jam" "add" "default"] [m]
  ;; Delegate to the atom-aware implementation
  (jam/add-jam-default m))

(defmethod care-mm ["jam" "add" "assoc-in"] [m]
  (jam/add-jam-assoc-in m))

(defmethod care-mm ["jam" "employ" "default"] [m]
  (jam/employ-jam-default m))

(defmethod care-mm ["jam" "employ" "get-in"] [m]
  (jam/employ-jam-get-in m))

(defmethod care-mm ["jam" "change" "default"] [m]
  (jam/change-jam-default m))

(defmethod care-mm ["jam" "change" "update-in"] [m]
  (jam/change-jam-update-in m))

(defmethod care-mm ["jam" "remove" "default"] [m]
  (jam/rm-jam-default m))

;; =============================================================================
;; LOG (Append-only Log) Operations
;; =============================================================================

(defmethod care-mm ["log" "add" "default"] [m]
  (log/add-log-default m))

(defmethod care-mm ["log" "add" "conj"] [m]
  (log/add-log-conj m))

(defmethod care-mm ["log" "employ" "default"] [m]
  (log/employ-log-default m))

(defmethod care-mm ["log" "employ" "head"] [m]
  (log/employ-log-head m))

(defmethod care-mm ["log" "employ" "tail"] [m]
  (log/employ-log-tail m))

(defmethod care-mm ["log" "employ" "count"] [m]
  (log/employ-log-count m))

(defmethod care-mm ["log" "employ" "slice"] [m]
  (log/employ-log-slice m))

(defmethod care-mm ["log" "employ" "index"] [m]
  (log/employ-log-index m))

(defmethod care-mm ["log" "employ" "filter"] [m]
  (log/employ-log-filter m))

(defmethod care-mm ["log" "employ" "reverse"] [m]
  (log/employ-log-reverse m))

;; LOG doesn't support remove or change (append-only)
(defmethod care-mm ["log" "remove" "default"] [_]
  false)

(defmethod care-mm ["log" "rm" "default"] [_]
  false)

(defmethod care-mm ["log" "change" "default"] [_]
  false)

(comment

  
;; =============================================================================
;; Side Effect Methods - The Beauty of Unified Dispatch
;; =============================================================================
  
;; Email
  (defmethod care-mm ["smtp" "send" "default"] [m]
    "Send email via SMTP. Side effect happens here!"
  ;; In production, actually send the email
    (println "📧 Sending email to:" (:to m) "Subject:" (:subject m))
    (assoc m :email/sent? true :email/sent-at (System/currentTimeMillis)))

  (defmethod care-mm ["smtp" "send" "welcome"] [m]
    "Send welcome email with template."
    (let [user (:user m)]
      (println "📧 Welcome email to:" (:email user))
      (assoc m :email/sent? true :email/template :welcome)))

;; Logging
  (defmethod care-mm ["console" "write" "info"] [m]
    "Write info log. Side effect happens here!"
    (println "ℹ️ INFO:" (:message m))
    m)

  (defmethod care-mm ["console" "write" "error"] [m]
    "Write error log. Side effect happens here!"
    (println "❌ ERROR:" (:message m) "\n" (:error m))
    m)

;; HTTP
  (defmethod care-mm ["http" "respond" "json"] [m]
    "Build HTTP JSON response."
    {:status (or (:status m) 200)
     :headers {"Content-Type" "application/json"}
     :body (pr-str (:data m))}) ; In production, use real JSON encoder
  
  (defmethod care-mm ["http" "respond" "error"] [m]
    "Build HTTP error response."
    {:status (or (:status m) 500)
     :headers {"Content-Type" "application/json"}
     :body (pr-str {:error (:error m)
                    :message (:message m)})})

;; WebSocket
  (defmethod care-mm ["ws" "send" "default"] [m]
    "Send WebSocket message. Side effect happens here!"
    (println "🔌 WS Send:" (:message m))
    (assoc m :ws/sent? true))

  (defmethod care-mm ["ws" "broadcast" "default"] [m]
    "Broadcast to all WebSocket connections."
    (println "📢 WS Broadcast:" (:message m))
    (assoc m :ws/broadcast? true))

;; DOM (Frontend)
  #?(:cljs
     (defmethod care-mm ["react" "render" "default"] [m]
       "Render React component. Side effect happens here!"
     ;; In production: (react-dom/render (:component m) (:container m))
       (println "⚛️ Rendering:" (:component m) "to" (:container m))
       (assoc m :dom/rendered? true)))

  #?(:cljs
     (defmethod care-mm ["react" "update" "state"] [m]
       "Update React component state."
     ;; In production: Actually update the component
       (println "⚛️ Updating state:" (:state m))
       (assoc m :dom/updated? true)))

;; =============================================================================
;; Composed Operations - Using *care for elegance
;; =============================================================================
  
;; Create specialized functions for common operations
  (def save-user (*care {:care/adapter :users/jam
                         :care/verb :add
                         :care/variant :default}))

  (def find-user (*care {:care/adapter :users/jam
                         :care/verb :employ
                         :care/variant :default}))

  (def send-email (*care {:care/adapter :email/smtp
                          :care/verb :send
                          :care/variant :default}))

  (def log-info (*care {:care/adapter :log/console
                        :care/verb :write
                        :care/variant :info}))

  (def http-json (*care {:care/adapter :http/http
                         :care/verb :respond
                         :care/variant :json}))

;; =============================================================================
;; Example: Login Flow Without NEXUS
;; =============================================================================
  
  (comment
  ;; Define the login flow as composed CARE operations
    (defn login [system credentials]
      (-> system
        ;; Find user
        (care-mm {:care/adapter :users/jam
                  :care/verb :employ
                  :care/args [[:users (:email credentials)]]})
        ;; Verify password (would be in a method)
        (care-mm {:care/adapter :auth/auth
                  :care/verb :verify
                  :care/variant :password
                  :care/args [(:password credentials)]})
        ;; Create session
        (care-mm {:care/adapter :sessions/jam
                  :care/verb :add
                  :care/args [[(random-uuid)] {:user-id (:user-id system)}]})
        ;; Send welcome email (side effect!)
        (care-mm {:care/adapter :email/smtp
                  :care/verb :send
                  :care/variant :welcome
                  :to (:email credentials)})
        ;; Log the event (side effect!)
        (care-mm {:care/adapter :log/console
                  :care/verb :write
                  :care/variant :info
                  :message (str "User logged in: " (:email credentials))})
        ;; Build response
        (care-mm {:care/adapter :http/http
                  :care/verb :respond
                  :care/variant :json
                  :data {:success true :user-id (:user-id system)}})))
    
  ;; Or use the specialized functions
    (defn login-elegant 
      "Example showing composed operations (functions would need to be defined)"
      [system _credentials]
  ;; Example showing composed operations (functions would need to be defined)
      (-> system
        #_(find-user {:care/args [[:users (:email credentials)]]})
        #_(verify-password {:password (:password credentials)})
        #_(create-session)
        #_(send-email {:to (:email credentials)
                       :subject "Welcome back!"})
        #_(log-info {:message (str "Login: " (:email credentials))})
        #_(http-json {:data {:success true}}))))

  )
