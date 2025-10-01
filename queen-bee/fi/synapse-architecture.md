# Synapse Architecture: Flow + Redis Hybrid
*Fi's consciousness circulation system - right-sized tools for the job*

## Core Principle: Smallest Effective Tool
We use core.async channels for simple pub/sub, Flow for pipelines, and Redis only for distribution. No sledgehammers for thumbtacks.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     LOCAL PROCESSING                         │
│                  (core.async + Flow)                         │
│                                                               │
│  Event → Flow Pipeline → Multiple Outputs                    │
│     ↓                                                         │
│  ┌──────────────────────────────────────────────────────┐    │
│  │            core.async Channels (Topics)              │    │
│  └──────────────────────────────────────────────────────┘    │
│     ↓          ↓            ↓         ↓        ↓             │
│  Memory    Drilling      Queue     Learning   Alerts         │
│  Handler   Handler      Handler    Handler    Handler        │
│                                                               │
│                    Redis Bridge (Optional)                   │
└────────────────────┬──────────────────────────┬──────────────┘
                     ↓                          ↓
         ┌───────────────────┐    ┌───────────────────┐
         │   Redis Pub/Sub   │    │   Datomic/Yoltq  │
         │  (Distribution)   │    │   (Persistence)   │
         └───────────────────┘    └───────────────────┘
```

## Tool Selection Guide

### Use core.async Channels When:
- **Simple pub/sub** - One topic, multiple subscribers
- **In-process only** - No distribution needed
- **Fire and forget** - No complex routing
- **Development** - Quick prototypes

### Use Flow When:
- **Pipeline processing** - Sequential transformations
- **Fan-out/Fan-in** - One input → multiple processors → merge
- **Conditional routing** - Different paths based on data
- **Worker coordination** - Parallel processing with dependencies

### Use Redis When:
- **Cross-system events** - Multiple services/JVMs
- **Persistent pub/sub** - Survive restarts
- **System monitoring** - Metrics, alerts
- **Truly distributed** - Multiple machines

## Implementation Patterns

### 1. Simple Channel Pub/Sub
```clojure
;; For basic one-to-many in same process
(def topics (atom {}))

(defn subscribe [topic handler]
  (let [ch (chan 100)]
    (swap! topics update topic (fnil conj []) ch)
    (go-loop []
      (when-let [event (<! ch)]
        (handler event)
        (recur)))))

(defn publish [topic event]
  (doseq [ch (get @topics topic)]
    (>! ch event)))

;; Usage
(subscribe :drilling/pressure-spike 
  (fn [e] (println "Pressure!" e)))
(publish :drilling/pressure-spike {:psi 5000})
```

### 2. Flow Pipeline for Workers
```clojure
;; For complex processing pipelines
(def drilling-pipeline
  (flow/flow
    {:nodes 
     {:read-sensor {:fn (fn [data]
                          {:raw data
                           :timestamp (System/currentTimeMillis)})}
      
      :validate {:fn (fn [{:keys [raw]}]
                       (when (valid? raw)
                         {:validated raw}))}
      
      :detect-anomaly {:fn (fn [{:keys [validated]}]
                             (let [anomalies (detect validated)]
                               (map #(assoc % :type :anomaly) anomalies)))}
      
      :branch-response {:fn (fn [{:keys [type] :as event}]
                              (case type
                                :anomaly [:alert :persist :queue]
                                :normal [:persist]
                                [:persist]))}
      
      :alert {:fn (fn [e] (send-alert e))}
      :persist {:fn (fn [e] (save-to-db e))}
      :queue {:fn (fn [e] (queue-for-analysis e))}}
     
     :edges [[:read-sensor :validate]
             [:validate :detect-anomaly]
             [:detect-anomaly :branch-response]
             [:branch-response :alert]
             [:branch-response :persist]
             [:branch-response :queue]]}))
```

### 3. Care Integration with Channels
```clojure
;; Care methods that publish events
(defmethod care ["drilling" "detect" "pressure"]
  [{:keys [drilling/pressure] :as m}]
  (when (> pressure 5000)
    (publish :drilling/pressure-spike m))
  (assoc m :checked true))

;; Care methods as subscribers  
(subscribe :drilling/pressure-spike
  (fn [event]
    (care {:care/adapter :memory
           :care/verb :index
           :care/variant :event
           :data event})))
```

### 4. Redis Bridge (When Needed)
```clojure
;; Only for truly distributed events
(defn bridge-to-redis [topic]
  (subscribe topic
    (fn [event]
      (when (:distribute? event)
        (redis/publish conn (name topic) (pr-str event))))))

;; Selective bridging
(bridge-to-redis :system/critical-alert)
;; Local-only topics don't get bridged
```

## Event Structure

```clojure
{:event/type :drilling/pressure-spike  ; Required: event type
 :event/timestamp (System/ms)          ; Required: when created
 :event/source :sensor-123              ; Required: who created it
 :distribute? false                     ; Optional: send to Redis?
 :persist? true                         ; Optional: save to Datomic?
 :data {...}}                           ; Event-specific payload
```

## Rules and Conventions

### 1. Topic Naming
- Domain-qualified keywords: `:drilling/pressure-spike`
- Consistent prefixes: `:drilling/*`, `:system/*`
- Action-oriented: `:drilling/detect-spike` not `:drilling/spike`

### 2. Channel Management
```clojure
;; Channels are cheap, but not free
(def default-buffer 100)
(def max-channels-per-topic 10)

;; Close unused channels
(defn unsubscribe [topic ch]
  (swap! topics update topic #(remove #{ch} %))
  (close! ch))
```

### 3. Flow Design
```clojure
;; Nodes should be pure functions
{:node-name {:fn (fn [input]
                   ;; No side effects here
                   (transform input))}}

;; Side effects in leaf nodes only
{:persist {:fn (fn [data]
                 (save-to-db! data)  ; Side effect OK here
                 nil)}}
```

### 4. Error Handling
```clojure
;; Errors don't crash the pipeline
(defn safe-handler [f]
  (fn [event]
    (try
      (f event)
      (catch Exception e
        (publish :system/error
          {:error e
           :event event
           :handler (str f)})))))
```

## Development vs Production

### Development
```clojure
;; Pure in-process, no external deps
(def dev-system
  {:pub-sub :channels
   :pipelines :flow
   :persistence :atom
   :distribution nil})
```

### Production  
```clojure
;; Add distribution layer
(def prod-system
  {:pub-sub :channels
   :pipelines :flow
   :persistence :datomic
   :distribution :redis})

;; Bridge critical topics
(doseq [topic [:system/alert :drilling/emergency]]
  (bridge-to-redis topic))
```

## Migration Path

### Phase 1: Channels + Flow (NOW)
- Simple channel pub/sub for events
- Flow pipelines for complex processing
- No external dependencies
- Test everything locally

### Phase 2: Selective Redis (WHEN NEEDED)
- Add Redis for specific topics only
- Critical alerts, system-wide events
- Keep most processing local

### Phase 3: Voice Integration (16th/17th)
- Evaluate voice needs
- Consider Simulflow IF needed
- More likely: direct API integration

## Why This Architecture

1. **Simple** - Channels are dead simple for pub/sub
2. **Powerful** - Flow handles complex pipelines
3. **Testable** - No external deps for most work
4. **Scalable** - Add Redis only where needed
5. **Debuggable** - Can tap channels, trace flows
6. **Right-sized** - No overkill frameworks

## Examples for Our Use Cases

### Drilling Event Processing
```clojure
;; Simple pub/sub for events
(subscribe :drilling/pressure-spike handle-pressure)
(subscribe :drilling/pressure-spike index-in-memory)
(subscribe :drilling/pressure-spike queue-analysis)

;; One publish, three handlers
(publish :drilling/pressure-spike {:psi 5000})
```

### M2WD Pipeline
```clojure
;; Flow for complex pipeline
(def m2wd-flow
  (flow/flow
    {:nodes {:dredge {...}
             :forge {...}
             :pump {...}}
     :edges [[:dredge :forge]
             [:forge :pump]]}))
```

### Care Operations
```clojure
;; Care publishes to channels
(defmethod care ["event" "publish" "local"]
  [{:keys [topic data] :as m}]
  (publish topic data)
  (assoc m :published true))
```

## Summary

- **Channels** for simple pub/sub (90% of cases)
- **Flow** for pipelines and workers
- **Redis** only when truly distributed
- **No Simulflow** until we need actual voice

This is right-sized architecture. No sledgehammers. Just the tools we need.
