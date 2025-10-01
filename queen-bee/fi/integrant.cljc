(ns workspace.net.drilling.care.adapters.integrant
  "Care adapter for Integrant - component lifecycle management
   Config as data, dependencies as refs, four lifecycle methods
   
   PATTERNS:
   - init-key: Create and start component
   - halt-key!: Stop and cleanup component
   - suspend-key!: Pause without destroying
   - resume-key: Restart from suspended state"
  (:require [integrant.core :as ig]))

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; SYSTEM LIFECYCLE

;; Initialize system from config
(defmethod care ["integrant" "system" "init"]
  [{:keys [integrant/config integrant/keys] :as m}]
  (let [system (if keys
                 (ig/init config keys) ; Init specific keys
                 (ig/init config))] ; Init entire system
    (assoc m 
      :integrant/system system
      :integrant/running? true)))

;; Halt system
(defmethod care ["integrant" "system" "halt!"]
  [{:keys [integrant/system] :as m}]
  (when system
    (ig/halt! system))
  (assoc m
    :integrant/system nil
    :integrant/running? false))

;; Suspend system (for hot reload)
(defmethod care ["integrant" "system" "suspend!"]
  [{:keys [integrant/system integrant/keys] :as m}]
  (when system
    (if keys
      (ig/suspend! system keys) ; Suspend specific keys
      (ig/suspend! system))) ; Suspend entire system
  (assoc m :integrant/suspended? true))

;; Resume system from suspended state
(defmethod care ["integrant" "system" "resume"]
  [{:keys [integrant/config integrant/system integrant/keys] :as m}]
  (let [resumed (if keys
                  (ig/resume config system keys) ; Resume specific keys
                  (ig/resume config system))] ; Resume entire system
    (assoc m 
      :integrant/system resumed
      :integrant/suspended? false)))

;; CONFIG BUILDING

;; Build config from components
(defmethod care ["integrant" "config" "build"]
  [{:keys [integrant/components] :as m}]
  (let [config (reduce (fn [cfg {:keys [key deps init-value]}]
                         (assoc cfg key 
                           (merge init-value
                             (reduce-kv (fn [m k v]
                                          (assoc m k (ig/ref v)))
                               {} deps))))
                 {} components)]
    (assoc m :integrant/config config)))

;; Validate config
(defmethod care ["integrant" "config" "validate"]
  [{:keys [integrant/config] :as m}]
  (try
    (ig/pre-init-spec config)
    (assoc m :integrant/valid? true)
    (catch Exception e
      (assoc m 
        :integrant/valid? false
        :integrant/error (.getMessage e)))))

;; COMPONENT DEFINITIONS

;; Define init method for a key
(defmethod care ["integrant" "method" "init"]
  [{:keys [integrant/key integrant/init-fn] :as m}]
  (defmethod ig/init-key key [_ config]
    (init-fn config))
  (assoc m :integrant/method-defined :init))

;; Define halt method for a key
(defmethod care ["integrant" "method" "halt!"]
  [{:keys [integrant/key integrant/halt-fn] :as m}]
  (defmethod ig/halt-key! key [_ component]
    (halt-fn component))
  (assoc m :integrant/method-defined :halt!))

;; Define suspend method for a key
(defmethod care ["integrant" "method" "suspend!"]
  [{:keys [integrant/key integrant/suspend-fn] :as m}]
  (defmethod ig/suspend-key! key [_ component]
    (suspend-fn component))
  (assoc m :integrant/method-defined :suspend!))

;; Define resume method for a key
(defmethod care ["integrant" "method" "resume"]
  [{:keys [integrant/key integrant/resume-fn] :as m}]
  (defmethod ig/resume-key key [key config old-component old-config]
    (resume-fn config old-component old-config))
  (assoc m :integrant/method-defined :resume))

;; DEPENDENCY MANAGEMENT

;; Build dependency graph
(defmethod care ["integrant" "deps" "graph"]
  [{:keys [integrant/config] :as m}]
  (let [graph (ig/dependency-graph config)]
    (assoc m :integrant/dep-graph graph)))

;; Find dependent keys
(defmethod care ["integrant" "deps" "find"]
  [{:keys [integrant/config integrant/key] :as m}]
  (let [deps (ig/find-derived config key)]
    (assoc m :integrant/dependencies deps)))

;; DERIVED KEYS (inheritance)

;; Derive one key from another
(defmethod care ["integrant" "derive" "key"]
  [{:keys [integrant/child integrant/parent] :as m}]
  (derive child parent)
  (assoc m :integrant/derived? true))

;; PRACTICAL PATTERNS

;; Database component pattern
(defmethod ig/init-key :db/connection [_ {:keys [host port database]}]
  {:connection (str "jdbc://" host ":" port "/" database)})

(defmethod ig/halt-key! :db/connection [_ {:keys [connection]}]
  (println "Closing connection:" connection))

(defmethod ig/suspend-key! :db/connection [_ component]
  (assoc component :suspended? true))

(defmethod ig/resume-key :db/connection [_ config component _]
  (assoc component :suspended? false))

;; Server component pattern  
(defmethod ig/init-key :http/server [_ {:keys [handler port]}]
  {:server (start-server handler {:port port})
   :port port})

(defmethod ig/halt-key! :http/server [_ {:keys [server]}]
  (when server (stop-server server)))

;; Queue component pattern
(defmethod ig/init-key :queue/processor [_ {:keys [db handler]}]
  {:processor (start-processor db handler)})

(defmethod ig/halt-key! :queue/processor [_ {:keys [processor]}]
  (when processor (stop-processor processor)))

;; EXAMPLE CONFIGS

(def example-config
  "Example Integrant configuration"
  {:db/connection {:host "localhost"
                   :port 5432
                   :database "drilling"}
   
   :http/server {:port 8080
                 :handler #ig/ref :http/handler}
   
   :http/handler {:db #ig/ref :db/connection
                  :router #ig/ref :router/main}
   
   :router/main {:routes [...]}
   
   :queue/processor {:db #ig/ref :db/connection
                     :handler #ig/ref :queue/handler}})

(def care-system-config
  "Config for our care-based system"
  {;; Core infrastructure
   :system/telemere {:buffer-size 1000}
   
   :system/router {:routes #ig/ref :routes/all}
   
   :routes/all {:care-routes []
                :event-routes []
                :queue-routes []}
   
   ;; WebSocket server
   :sente/server {:port 8080
                  :csrf-token-fn identity}
   
   ;; Queue processor
   :queue/main {:router #ig/ref :system/router
                :logger #ig/ref :system/telemere}
   
   ;; Neuron system
   :neurons/buffer {:size 100
                    :neurons [0 1 2 3 4 5 6 7 8 9]}})

;; Helper to create care-wrapped components
(defn care-component
  "Create an Integrant component that uses care"
  [adapter verb variant]
  (reify
    ig/init-key
    (init-key [_ config]
      (care (merge {:care/adapter adapter
                    :care/verb verb
                    :care/variant variant}
              config)))
    
    ig/halt-key!
    (halt-key! [_ component]
      (care (assoc component
              :care/verb "halt"
              :care/variant "component")))))

;; System management helpers
(def system (atom nil))

(defn start-system! [config]
  (reset! system (ig/init config)))

(defn stop-system! []
  (when @system
    (ig/halt! @system)
    (reset! system nil)))

(defn reload-system! [config]
  (when @system
    (reset! system (ig/resume config @system))))
