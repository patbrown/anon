# System Component Inventory
*Unix-style composable pieces for the Queen Bee architecture*

## Core Ingredients List

### Storage Adapters (Modatoms)
```clojure
;; 3 implementations, same interface
{:modatom/file
 {:needs ["filesystem access" "EDN serialization"]
  :provides [:local-persistence :instant-reads :no-deps]
  :implementation "atom-like with file backing"}
 
 :modatom/redis
 {:needs ["carmine" "redis connection"]
  :provides [:distributed-state :pub-sub :ttl]
  :implementation "atom-like with Redis backing"}
 
 :modatom/s3
 {:needs ["amazonica" "S3 credentials"]
  :provides [:infinite-storage :versioning :public-urls]
  :implementation "atom-like with S3 backing"}}

;; Unified interface
(defprotocol Modatom
  (get-state [this])
  (swap-state! [this f])
  (reset-state! [this v])
  (watch-state [this key fn]))
```

### RUUTER Components (12 implementations)
```clojure
{:ruuter/http      {:port 3000 :routes [...]}
 :ruuter/cli       {:commands [...]}
 :ruuter/queue     {:workers 4 :routes [...]}
 :ruuter/synapse   {:topics [...]}
 :ruuter/websocket {:port 3001 :routes [...]}
 :ruuter/repl      {:commands [...]}
 :ruuter/git-hook  {:hooks [...]}
 :ruuter/schedule  {:cron [...]}
 :ruuter/file-watch {:patterns [...]}
 :ruuter/email     {:imap {...}}
 :ruuter/slack     {:bot-token "..."}
 :ruuter/mcp       {:tools [...]}}
```

### Queue Components (Yoltq-based)
```clojure
{:queue/executor
 {:needs ["datomic conn" "worker count"]
  :provides [:async-execution :retry-logic :prioritization]}
 
 :queue/scheduler
 {:needs ["cron expressions" "queue/executor"]
  :provides [:scheduled-jobs :recurring-tasks]}
 
 :queue/dlq
 {:needs ["failure-threshold" "storage"]
  :provides [:failed-job-capture :retry-mechanism]}
 
 :queue/monitor
 {:needs ["metrics-store" "alert-threshold"]
  :provides [:queue-depth :processing-time :failure-rate]}}
```

### Datomic Foundation
```clojure
{:datomic/schema
 {:entities
  [{:db/ident :care/adapter}
   {:db/ident :care/verb}
   {:db/ident :care/variant}
   {:db/ident :entity/id :db/unique :db.unique/identity}
   {:db/ident :entity/created}
   {:db/ident :entity/updated}
   {:db/ident :queue/status}
   {:db/ident :queue/priority}
   {:db/ident :synapse/topics :db/cardinality :db.cardinality/many}]}
 
 :datomic/txfns
 {:care-tx "Universal transaction function"
  :queue-tx "Queue state transitions"
  :synapse-tx "Event publishing"}
 
 :datomic/rules
 '[[(care-shaped ?e)
    [?e :care/adapter]
    [?e :care/verb]
    [?e :care/variant]]
   
   [(queue-ready ?e)
    [?e :queue/status :pending]
    [?e :queue/priority ?p]]]}
```

### Synapse Components (Pub/Sub)
```clojure
{:synapse/local-channels
 {:needs ["core.async"]
  :provides [:in-process-pubsub :immediate-delivery]
  :buffer-size 100}
 
 :synapse/redis-bridge
 {:needs ["redis conn" "topics list"]
  :provides [:distributed-pubsub :cross-system-events]
  :when "(:distribute? message)"}
 
 :synapse/flow-pipeline
 {:needs ["flow definitions"]
  :provides [:complex-routing :transformations]}}
```

### Analysis Components
```clojure
{:analyzer/clj-kondo
 {:needs ["analyze-this! fn" "file paths"]
  :provides [:var-usage :unused-detection :lint-errors]}
 
 :analyzer/clindex
 {:needs ["project root"]
  :provides [:project-index :symbol-search :dependency-graph]}
 
 :analyzer/codeq
 {:needs ["git repo" "datomic conn"]
  :provides [:code-history :blame-data :evolution-tracking]}}
```

### Transformation Components
```clojure
{:transform/rewrite-clj
 {:needs ["zipper navigation"]
  :provides [:ast-manipulation :structural-editing]}
 
 :transform/selmer
 {:needs ["template strings" "data maps"]
  :provides [:string-generation :file-templating]}
 
 :transform/care-wrapper
 {:needs ["operation" "context"]
  :provides [:unified-interface :audit-trail]}}
```

### Memory/RAG Components
```clojure
{:memory/embedder
 {:needs ["openai-api-key" "text"]
  :provides [:vector-embeddings]
  :dimensions 1536}
 
 :memory/vector-store
 {:needs ["vectors" "metadata"]
  :provides [:similarity-search :hybrid-search]
  :backend "in-memory or milvus"}
 
 :memory/indexer
 {:needs ["documents" "embedder"]
  :provides [:auto-indexing :tag-extraction]}}
```

## Shopping List (What We Need)

### Dependencies to Add
```clojure
{:deps
 {;; Storage
  com.taoensso/carmine {:mvn/version "3.3.0"}  ; Redis
  amazonica/amazonica {:mvn/version "0.3.161"}  ; S3
  
  ;; Database
  com.datomic/peer {:mvn/version "1.0.6735"}
  com.yoltq/yoltq {:mvn/version "0.2.0"}
  
  ;; Analysis
  clj-kondo/clj-kondo {:mvn/version "2024.09.27"}
  com.github.jpmonettas/clindex {:mvn/version "1.3.0"}
  
  ;; Transformation
  rewrite-clj/rewrite-clj {:mvn/version "1.1.47"}
  selmer/selmer {:mvn/version "1.12.59"}
  
  ;; Infrastructure
  integrant/integrant {:mvn/version "0.8.1"}
  org.clojure/core.async {:mvn/version "1.6.681"}}}
```

### Configuration Needed
```clojure
{:system/config
 {:datomic-uri "datomic:dev://localhost:4334/queen-bee"
  :redis-uri "redis://localhost:6379"
  :s3-bucket "queen-bee-storage"
  :openai-key (System/getenv "OPENAI_API_KEY")
  :queue-workers 4
  :synapse-topics [:drilling/* :system/* :learning/*]
  :http-port 3000
  :websocket-port 3001}}
```

## Assembly Instructions

### 1. Data Foundation
```clojure
;; First: Datomic schema
(d/transact conn schema)

;; Second: Transaction functions
(d/transact conn txfns)

;; Third: Queue tables
(yoltq/init conn)
```

### 2. Storage Layer
```clojure
;; Modatom factory
(defn make-modatom [type config]
  (case type
    :file (->FileModatom (:path config))
    :redis (->RedisModatom (:conn config) (:key config))
    :s3 (->S3Modatom (:bucket config) (:key config))))

;; Neurons use file modatom
(def neurons (make-modatom :file {:path "neurons/"}))

;; Cache uses Redis modatom
(def cache (make-modatom :redis {:conn redis-conn :key "cache"}))

;; Archives use S3 modatom
(def archive (make-modatom :s3 {:bucket "archives" :key "2024"}))
```

### 3. Routing Layer
```clojure
;; RUUTER factory
(defn make-ruuter [type routes]
  (fn [{:keys [care/adapter care/verb care/variant] :as m}]
    (if-let [handler (get routes [adapter verb variant])]
      (handler m)
      (care {:care/adapter :error
             :care/verb :unknown
             :care/variant type
             :original m}))))

;; Make all 12 routers
(def http-ruuter (make-ruuter :http http-routes))
(def cli-ruuter (make-ruuter :cli cli-routes))
(def queue-ruuter (make-ruuter :queue queue-routes))
;; ... etc
```

### 4. Intelligence Layer
```clojure
;; Compose analyzers
(def analyze-project
  (comp clindex/index
        analyze-this!
        codeq/import))

;; Compose transformers  
(def transform-file
  (comp selmer/render
        rewrite-clj/transform
        care/wrap))
```

## Specific Next Steps

### Week 1: Foundation
- [ ] Install Datomic dev
- [ ] Create schema migrations
- [ ] Set up Yoltq queues
- [ ] Basic care multimethod

### Week 2: Storage
- [ ] Implement file modatom
- [ ] Implement Redis modatom
- [ ] Test S3 modatom
- [ ] Migrate neurons to modatoms

### Week 3: Routing
- [ ] HTTP RUUTER with Ring
- [ ] CLI RUUTER with tools.cli
- [ ] Queue RUUTER with Yoltq
- [ ] MCP RUUTER integration

### Week 4: Intelligence
- [ ] Wire clj-kondo analyzer
- [ ] Set up rewrite-clj transforms
- [ ] Create selmer templates
- [ ] Index codebase with clindex

## The Unix Philosophy Applied

Each component:
- Does ONE thing well
- Composes with others via care maps
- Can be tested in isolation
- Can be swapped for alternatives
- Communicates through standard shapes

No component knows about:
- Other components' internals
- The overall system architecture
- Database schemas
- Network protocols

Every component just:
- Receives care maps
- Transforms them
- Returns care maps

This is our shopping list. These are our building blocks.
