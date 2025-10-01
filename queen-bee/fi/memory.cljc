(ns workspace.net.drilling.care.adapters.memory
  "Simple RAG for medium-term memory - no critical persistence needed
   Semantic search for neurons and patterns, working memory for operations"
  (:require [ragtacts.core :as rag]))

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; MEMORY STRUCTURE

(def working-memory
  "In-memory store for semantic search - no persistence needed"
  (atom {:neurons [] ; Indexed neurons with embeddings
         :patterns [] ; Care patterns with embeddings  
         :operations [] ; Recent operations (rolling buffer)
         :embeddings {} ; Cache of text → embedding
         :categories {} ; Category index for hybrid search
         :tags {}})) ; Tag → entity-ids for fast filtering

;; FI NEEDS: Hybrid search combining semantic similarity + categorical filtering
;; Want to search "drilling pressure patterns" and get:
;; 1. Semantically similar (via embeddings)
;; 2. Filtered by categories/tags (only :drilling/pressure tagged items)
;; This gives precision - not just "similar" but "similar AND relevant"
;;
;; SYNAPSE TAGGING for pub/sub integration:
;; Content gets tagged with synapse topics it should publish to
;; Example: A drilling event gets tags:
;;   [:synapse/drilling :synapse/pressure-spike :synapse/safety-critical]
;; This means:
;;   - Event publishes to all three synapse topics
;;   - Different subscribers respond to each topic
;;   - Hybrid search can filter by any combination of synapses
;;
;; ONE CONTENT -> MULTIPLE SYNAPSES is the power move:
;;   - Drilling pressure spike -> publishes to pressure, safety, learning topics
;;   - Each topic has different subscribers with different responses
;;   - Rich retrieval: "Find all safety-critical pressure events from last week"

;; INDEX OPERATIONS

(defmethod care ["memory" "index" "neuron"]
  [{:keys [memory/neuron-id memory/text memory/tags memory/categories] :as m}]
  (let [embedding (rag/embed text)
        entry {:id neuron-id
               :text text
               :embedding embedding
               :tags (or tags #{})
               :categories (or categories #{})
               :timestamp (System/currentTimeMillis)}]
    (swap! working-memory update :neurons conj entry)
    ;; Update tag index for fast filtering
    (doseq [tag (or tags [])]
      (swap! working-memory update-in [:tags tag] (fnil conj #{}) neuron-id))
    (assoc m :memory/indexed? true)))

(defmethod care ["memory" "index" "pattern"]
  [{:keys [care/adapter care/verb care/variant memory/description memory/tags memory/categories] :as m}]
  (let [text (str adapter "/" verb "/" variant " - " description)
        embedding (rag/embed text)
        pattern-id [adapter verb variant]
        entry {:care pattern-id
               :text text
               :embedding embedding
               :tags (or tags #{})
               :categories (or categories #{})
               :timestamp (System/currentTimeMillis)}]
    (swap! working-memory update :patterns conj entry)
    ;; Update tag index
    (doseq [tag (or tags [])]
      (swap! working-memory update-in [:tags tag] (fnil conj #{}) pattern-id))
    (assoc m :memory/indexed? true)))

(defmethod care ["memory" "index" "operation"]
  [{:keys [memory/operation memory/outcome memory/duration] :as m}]
  ;; Rolling buffer - keep last 1000
  (swap! working-memory update :operations
    (fn [ops]
      (take 1000 (conj ops
                   {:operation operation
                    :outcome outcome
                    :duration duration
                    :timestamp (System/currentTimeMillis)}))))
  (assoc m :memory/indexed? true))

;; SEARCH OPERATIONS  

(defmethod care ["memory" "search" "similar"]
  [{:keys [memory/query memory/type memory/limit] :as m}]
  (let [embedding (rag/embed query)
        collection (get @working-memory (or type :neurons))
        results (rag/search-similar embedding collection (or limit 5))]
    (assoc m :memory/results results)))

(defmethod care ["memory" "search" "hybrid"]
  [{:keys [memory/query memory/tags memory/categories memory/type memory/limit] :as m}]
  ;; FI WANTS: Combine semantic similarity with categorical filtering
  (let [embedding (rag/embed query)
        collection (get @working-memory (or type :neurons))
        ;; First filter by tags/categories if provided
        filtered (cond->> collection
                   tags (filter #(some (:tags %) tags))
                   categories (filter #(some (:categories %) categories)))
        ;; Then search semantically within filtered set
        results (rag/search-similar embedding filtered (or limit 5))]
    (assoc m 
      :memory/results results
      :memory/filter-stats {:total (count collection)
                            :after-filter (count filtered)
                            :returned (count results)})))

(defmethod care ["memory" "search" "neurons"]
  [{:keys [memory/query] :as m}]
  (care (assoc m 
          :care/variant "similar"
          :memory/type :neurons
          :memory/query query)))

(defmethod care ["memory" "search" "patterns"]
  [{:keys [memory/query] :as m}]
  (care (assoc m
          :care/variant "similar"
          :memory/type :patterns
          :memory/query query)))

;; CONTEXT BUILDING

(defmethod care ["memory" "context" "build"]
  [{:keys [memory/operation] :as m}]
  ;; Find similar past operations
  (let [similar-ops (care {:care/adapter "memory"
                           :care/verb "search"
                           :care/variant "similar"
                           :memory/query (str operation)
                           :memory/type :operations
                           :memory/limit 10})
        ;; Find relevant neurons
        relevant-neurons (care {:care/adapter "memory"
                                :care/verb "search"
                                :care/variant "neurons"
                                :memory/query (str operation)
                                :memory/limit 3})]
    (assoc m
      :memory/context {:similar-operations (:memory/results similar-ops)
                       :relevant-neurons (:memory/results relevant-neurons)})))

;; INTENTION ROUTING (stolen from semantic-router)

(def intention-routes
  "Map intentions to care operations based on semantic similarity"
  [{:intention "copy a file"
    :care ["fs" "copy" "file"]}
   {:intention "run a command"
    :care ["shell" "exec" "simple"]}
   {:intention "connect to server"
    :care ["ssh" "session" "create"]}
   {:intention "start web server"
    :care ["http" "server" "start"]}])

(defmethod care ["memory" "intention" "route"]
  [{:keys [memory/intention] :as m}]
  (let [embedding (rag/embed intention)
        best-match (rag/find-best-match embedding intention-routes)]
    (assoc m
      :routed/adapter (get-in best-match [:care 0])
      :routed/verb (get-in best-match [:care 1])
      :routed/variant (get-in best-match [:care 2]))))

;; PATTERN LEARNING

(defmethod care ["memory" "pattern" "learn"]
  [{:keys [memory/successful-operation] :as m}]
  ;; Index successful patterns for future reuse
  (let [op successful-operation]
    (care (assoc op
            :care/adapter "memory"
            :care/verb "index"
            :care/variant "pattern"
            :memory/description (str "Successful: " op))))
  (assoc m :memory/learned? true))

;; MEMORY DECAY (optional - clear old entries)

(defmethod care ["memory" "decay" "operations"]
  [{:keys [memory/max-age-ms] :as m}]
  (let [cutoff (- (System/currentTimeMillis) (or max-age-ms (* 24 60 60 1000)))]
    (swap! working-memory update :operations
      (fn [ops] (filter #(> (:timestamp %) cutoff) ops)))
    (assoc m :memory/pruned? true)))

;; BOOTSTRAP - Index existing neurons and patterns

(defn bootstrap-memory!
  "Index all neurons and care patterns into memory"
  []
  ;; Index neurons
  (doseq [n (range 13)]
    (when-let [neuron (read-neuron n)]
      (care {:care/adapter "memory"
             :care/verb "index"
             :care/variant "neuron"
             :memory/neuron-id n
             :memory/text (str neuron)})))
  
  ;; Index care patterns from our adapters
  (doseq [[adapter patterns] known-patterns]
    (doseq [pattern patterns]
      (care (merge {:care/adapter "memory"
                    :care/verb "index"
                    :care/variant "pattern"
                    :memory/description (get pattern 2)}
              {:pattern/adapter adapter
               :pattern/verb (first pattern)
               :pattern/variant (second pattern)})))))

;; AUGMENTED CARE - Wrap care with memory

(defn augmented-care
  "Execute care with memory context"
  [m]
  (let [;; Build context from memory
        context (care {:care/adapter "memory"
                       :care/verb "context"
                       :care/variant "build"
                       :memory/operation m})
        ;; Execute with context
        result (traced-care (assoc m :memory/context context))]
    ;; Learn from result
    (when (= :success (:signal/outcome result))
      (care {:care/adapter "memory"
             :care/verb "pattern"
             :care/variant "learn"
             :memory/successful-operation result}))
    result))