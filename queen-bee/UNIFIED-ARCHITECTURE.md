# Unified System Architecture
*The One Map That Rules Them All*

## Core Principle: Everything is the Same Shape

Every operation in our system follows the same pattern:
```clojure
{:care/adapter :domain
 :care/verb :action  
 :care/variant :specific
 ;; Everything else is data
 :data {...}}
```

This map flows through queues, gets transacted to Datomic, triggers synapses, and configures reality.

## The Foundation: Datomic + Queues

### Schema Design
```clojure
;; Every entity is care-shaped
{:db/ident :care/adapter
 :db/valueType :db.type/keyword
 :db/cardinality :db.cardinality/one
 :db/doc "Domain of operation"}

{:db/ident :care/verb
 :db/valueType :db.type/keyword
 :db/cardinality :db.cardinality/one
 :db/doc "Action to perform"}

{:db/ident :care/variant
 :db/valueType :db.type/keyword
 :db/cardinality :db.cardinality/one
 :db/doc "Specific implementation"}

;; Universal tracking
{:db/ident :entity/id
 :db/valueType :db.type/uuid
 :db/cardinality :db.cardinality/one
 :db/unique :db.unique/identity
 :db/doc "Universal entity identifier"}

{:db/ident :entity/created
 :db/valueType :db.type/instant
 :db/cardinality :db.cardinality/one}

{:db/ident :entity/updated
 :db/valueType :db.type/instant
 :db/cardinality :db.cardinality/one}

;; Queue integration
{:db/ident :queue/status
 :db/valueType :db.type/keyword
 :db/cardinality :db.cardinality/one
 :db/doc "[:pending :processing :complete :failed]"}

{:db/ident :queue/priority
 :db/valueType :db.type/long
 :db/cardinality :db.cardinality/one}

;; Synapse tagging
{:db/ident :synapse/topics
 :db/valueType :db.type/keyword
 :db/cardinality :db.cardinality/many
 :db/doc "Pub/sub topics this entity publishes to"}
```

### Transaction Functions
```clojure
;; One txfn to rule them all
(defn care-tx
  [db {:keys [care/adapter care/verb care/variant entity/id] :as m}]
  (let [id (or id (squuid))
        now (java.util.Date.)
        existing (d/entity db [:entity/id id])]
    [(merge
      {:db/id (d/tempid :db.part/user)
       :entity/id id
       :entity/created (or (:entity/created existing) now)
       :entity/updated now
       :care/adapter adapter
       :care/verb verb
       :care/variant variant}
      (dissoc m :care/adapter :care/verb :care/variant))]))

;; Install it
(d/transact conn
  [{:db/id (d/tempid :db.part/user)
    :db/ident :care/tx
    :db/fn (d/function {:lang "clojure"
                       :params '[db m]
                       :code '(care-tx db m)})}])
```

### Queue Architecture (Yoltq-based)
```clojure
(def queue-config
  {:queues
   {:care/immediate {:priority 100 :workers 4}
    :care/batch {:priority 50 :workers 2}
    :care/background {:priority 10 :workers 1}}
   
   :dispatcher care  ; Everything dispatches through care
   
   :error-handler
   (fn [m e]
     (care {:care/adapter :error
            :care/verb :log
            :care/variant :queue
            :error e
            :original m}))})

;; Every queue message is care-shaped
(defn enqueue [m]
  (let [priority (queue-priority m)
        queue (queue-for m)]
    (yoltq/put queue (assoc m :queue/status :pending
                              :queue/priority priority))))
```

## Unified Components

### RUUTER Pattern (Route-Transform-Execute)
```clojure
(defn ruuter
  "Universal router for all our subsystems"
  [config]
  (fn [{:keys [care/adapter care/verb care/variant] :as m}]
    (let [route [adapter verb variant]
          handler (get-in config [:routes route])]
      (if handler
        (handler m)
        (care {:care/adapter :ruuter
               :care/verb :unknown
               :care/variant :route
               :attempted route
               :original m})))))

;; Used by:
;; - HTTP routing
;; - CLI command dispatch  
;; - Queue job routing
;; - Synapse event routing
;; - WebSocket message routing
;; - REPL command routing
```

### Integrant Config Shape
```clojure
;; Every component follows the same pattern
(def system-config
  {:care/multimethod {}  ; The universal dispatcher
   
   :datomic/conn
   {:uri "datomic:dev://localhost:4334/care"}
   
   :queue/conn
   {:datomic (ig/ref :datomic/conn)
    :config queue-config}
   
   :synapse/channels
   {:topics [:drilling/pressure :drilling/torque :system/alert]}
   
   :ruuter/http
   {:port 3000
    :routes {[:http :get :health] health-handler
             [:http :post :care] care-handler}}
   
   :ruuter/cli
   {:routes {[:cli :git :status] git-status
             [:cli :bb :eval] bb-eval}}
   
   :memory/rag
   {:dimensions 1536
    :index-type :hybrid}})

;; Every component init follows same pattern
(defmethod ig/init-key :ruuter/http [_ config]
  (ruuter config))

(defmethod ig/init-key :ruuter/cli [_ config]
  (ruuter config))
```

### Instance-Based Structures
```clojure
;; No classes, only instances from queries
(defn make-instance [template data]
  (merge template data {:entity/id (squuid)
                        :entity/created (java.util.Date.)}))

;; Templates are just data
(def templates
  {:drilling/event
   {:care/adapter :drilling
    :care/verb :detect
    :care/variant :anomaly
    :required [:pressure :torque :rop]}
   
   :system/alert
   {:care/adapter :system
    :care/verb :send
    :care/variant :alert
    :required [:severity :message]}})

;; Everything queries the same space
(defn get-instances [type]
  (d/q '[:find ?e
         :in $ ?adapter
         :where [?e :care/adapter ?adapter]]
       (d/db conn) type))
```

## Communication System

### Channels for Local
```clojure
(def local-synapses (atom {}))

(defn subscribe [topic handler]
  (let [ch (chan 100)]
    (swap! local-synapses update topic (fnil conj []) ch)
    (go-loop []
      (when-let [m (<! ch)]
        (care (assoc m :care/adapter :handler
                      :care/verb :process
                      :care/variant topic))
        (recur)))))

(defn publish [topic m]
  (doseq [ch (get @local-synapses topic)]
    (>! ch m)))
```

### Redis Bridge (When Needed)
```clojure
(defn bridge-to-redis [topic]
  (subscribe topic
    (fn [m]
      (when (:distribute? m)
        (redis/publish conn (name topic) (pr-str m))))))
```

## Data Flow Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Care Map Entry                        │
│    {:care/adapter :x :care/verb :y :care/variant :z}    │
└────────────────────┬─────────────────────────────────────┘
                     ↓
        ┌────────────────────────────┐
        │     Care Multimethod       │
        │   (dispatch on [x y z])    │
        └────────────┬───────────────┘
                     ↓
     ┌───────────────┴───────────────┐
     ↓               ↓               ↓
┌─────────┐    ┌─────────┐    ┌─────────┐
│ Queue   │    │ Datomic │    │ Synapse │
│ (Yoltq) │    │   Tx    │    │ Publish │
└─────────┘    └─────────┘    └─────────┘
     ↓               ↓               ↓
┌─────────────────────────────────────────┐
│          Unified Processing             │
│  - Same shapes                          │
│  - Same queries                         │
│  - Same transformations                 │
└─────────────────────────────────────────┘
```

## Sacrifices We're Making

1. **No Direct Execution** - Everything through care and queues
2. **No Custom Shapes** - Everything is care-shaped maps
3. **No Static Registries** - Generate from consciousness
4. **No Distributed State** - Datomic is truth, Redis is broadcast only

## Benefits We Get

1. **One Pattern Everywhere** - Learn once, use everywhere
2. **Complete Auditability** - Every operation in Datomic
3. **Natural Queue Integration** - Everything queueable by default
4. **Trivial Testing** - Mock one multimethod, test everything
5. **Configuration Not Code** - Change behavior through data

## Migration Strategy

### Phase 1: Foundation (NOW)
- Set up Datomic schema
- Implement care-tx function
- Basic queue integration
- Local channels for synapses

### Phase 2: Components (NEXT)
- RUUTER for all routing
- Migrate existing code to care shapes
- Integrant components following pattern

### Phase 3: Intelligence (SOON)
- Hook up rewrite-clj for transformations
- Clj-kondo for analysis
- Codeq for history

### Phase 4: Distribution (LATER)
- Redis bridge for critical events
- Multiple JVM coordination
- Distributed queue processing

## The One Map

```clojure
(def the-one-map
  {:care/adapter :universe
   :care/verb :create
   :care/variant :everything
   
   ;; This map shape flows through:
   :flows-through [:queues :datomic :synapses :channels
                   :redis :http :cli :repl :websockets]
   
   ;; Transformed by:
   :transformed-by [:care :ruuter :rewrite-clj :selmer]
   
   ;; Validated by:
   :validated-by [:clj-kondo :analyze-this!]
   
   ;; Remembered by:
   :remembered-by [:datomic :codeq :neurons]
   
   ;; The result:
   :result "Complete system unity through one pattern"})
```

## What's Left (20-40%)

- Actual Datomic connection setup
- Yoltq queue implementation details
- Redis connection when needed
- Specific RUUTER route handlers
- Intelligence stack integration
- Concrete drilling domain mappings

But the architecture is clear. One map, one pattern, infinite configurations.
