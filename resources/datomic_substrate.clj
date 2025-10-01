(ns workspace.munchausen.datomic-substrate
  "Datomic substrate for drilling improvement system with yoltq integration.
   Created: 2025-09-14 by Fi and Pat
   Purpose: Foundation for value-based architecture with event detection."
  (:require [datomic.api :as d]
            [clojure.edn :as edn]))

;; Database setup
(def db-uri "datomic:mem://drilling-substrate")

(defn init-database
  "Initialize the Datomic database with schema"
  []
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    ;; Core schema - the four IDs
    @(d/transact conn
       [{:db/ident :instance/id
         :db/valueType :db.type/uuid
         :db/cardinality :db.cardinality/one
         :db/unique :db.unique/identity
         :db/doc "Universal unique identifier for any instance"}
        
        {:db/ident :thing/id 
         :db/valueType :db.type/keyword
         :db/cardinality :db.cardinality/one
         :db/doc "What type of thing this is"}
        
        {:db/ident :inside-thing-unique/id
         :db/valueType :db.type/keyword
         :db/cardinality :db.cardinality/one
         :db/doc "Specific identity within type"}
        
       ;; Entry attributes for neurons
        {:db/ident :entry/id
         :db/valueType :db.type/uuid
         :db/cardinality :db.cardinality/one
         :db/unique :db.unique/identity
         :db/doc "UUID for this entry/fact"}
        
        {:db/ident :entry/created
         :db/valueType :db.type/instant
         :db/cardinality :db.cardinality/one
         :db/doc "When this entry was created"}
        
        {:db/ident :entry/neuron
         :db/valueType :db.type/keyword
         :db/cardinality :db.cardinality/one
         :db/doc "Which neuron owns this entry"}
        
        {:db/ident :entry/content
         :db/valueType :db.type/string
         :db/cardinality :db.cardinality/one
         :db/doc "EDN string of the entry content"}])
    
    ;; Drilling schema
    @(d/transact conn
       [{:db/ident :drilling/depth
         :db/valueType :db.type/double
         :db/cardinality :db.cardinality/one
         :db/doc "Measured depth in feet"}
        
        {:db/ident :drilling/pressure
         :db/valueType :db.type/double
         :db/cardinality :db.cardinality/one
         :db/doc "Pressure in PSI"}
        
        {:db/ident :drilling/rop
         :db/valueType :db.type/double
         :db/cardinality :db.cardinality/one
         :db/doc "Rate of penetration in ft/hr"}
        
        {:db/ident :drilling/torque
         :db/valueType :db.type/double
         :db/cardinality :db.cardinality/one
         :db/doc "Torque in ft-lbs"}
        
        {:db/ident :drilling/temperature
         :db/valueType :db.type/double
         :db/cardinality :db.cardinality/one
         :db/doc "Temperature in degrees F"}
        
       ;; Event detection
        {:db/ident :event/type
         :db/valueType :db.type/keyword
         :db/cardinality :db.cardinality/one
         :db/doc "Type of drilling event detected"}
        
        {:db/ident :event/confidence
         :db/valueType :db.type/double
         :db/cardinality :db.cardinality/one
         :db/doc "Confidence level 0.0-1.0"}
        
        {:db/ident :event/timestamp
         :db/valueType :db.type/instant
         :db/cardinality :db.cardinality/one
         :db/doc "When the event was detected"}
        
       ;; Synapse/topic attributes for pub/sub
        {:db/ident :synapse/topics
         :db/valueType :db.type/keyword
         :db/cardinality :db.cardinality/many
         :db/doc "Topics this fact publishes to"}
        
        {:db/ident :synapse/triggered-by
         :db/valueType :db.type/uuid
         :db/cardinality :db.cardinality/many
         :db/doc "UUIDs of facts that triggered this"}])
    
    conn))

;; Helper functions
(defn create-instance
  "Create an instance with our universal ID pattern"
  [thing-type unique-id attrs]
  (merge
    {:instance/id (java.util.UUID/randomUUID)
     :thing/id thing-type
     :inside-thing-unique/id unique-id}
    attrs))

(defn transact-neuron-entry
  "Add a neuron entry to Datomic"
  [conn neuron-id entry-map]
  (let [entry-id (or (:entry/id entry-map) 
                   (java.util.UUID/randomUUID))
        created (or (:entry/created entry-map)
                  (java.util.Date.))]
    @(d/transact conn
       [(merge
          {:entry/id entry-id
           :entry/created created
           :entry/neuron neuron-id
           :entry/content (pr-str entry-map)}
          (dissoc entry-map :entry/id :entry/created))])))

(defn query-neuron
  "Query all entries for a neuron"
  [db neuron-id]
  (d/q '[:find ?e ?created ?content
         :in $ ?neuron
         :where
         [?e :entry/neuron ?neuron]
         [?e :entry/created ?created]
         [?e :entry/content ?content]]
    db neuron-id))

;; Event detection
(defn detect-drilling-events
  "Detect events from measurements and create event entities"
  [conn measurement]
  (let [pressure (:drilling/pressure measurement)
        rop (:drilling/rop measurement)
        torque (:drilling/torque measurement)
        depth (:drilling/depth measurement)
        events (atom [])]
    
    ;; Pressure spike detection
    (when (> pressure 4000.0)
      (swap! events conj
        (create-instance
          :thing/event
          :event/pressure-spike
          {:event/type :stuck-pipe-risk
           :event/confidence 0.65
           :event/timestamp (java.util.Date.)
           :synapse/topics [:synapse/alert :synapse/pressure-spike]
           :synapse/triggered-by [(:instance/id measurement)]
           :drilling/depth depth
           :drilling/pressure pressure})))
    
    ;; ROP drop detection
    (when (< rop 20.0)
      (swap! events conj
        (create-instance
          :thing/event
          :event/rop-drop
          {:event/type :drilling-dysfunction
           :event/confidence 0.70
           :event/timestamp (java.util.Date.)
           :synapse/topics [:synapse/alert :synapse/rop-drop]
           :synapse/triggered-by [(:instance/id measurement)]
           :drilling/depth depth
           :drilling/rop rop})))
    
    ;; Combined risk detection
    (when (and (> pressure 4000.0) (< rop 20.0) (> torque 15000.0))
      (swap! events conj
        (create-instance
          :thing/event
          :event/stuck-pipe-imminent
          {:event/type :stuck-pipe-imminent
           :event/confidence 0.85
           :event/timestamp (java.util.Date.)
           :synapse/topics [:synapse/critical-alert :synapse/stuck-pipe :synapse/immediate-action]
           :synapse/triggered-by [(:instance/id measurement)]
           :drilling/depth depth
           :drilling/pressure pressure
           :drilling/rop rop
           :drilling/torque torque})))
    
    ;; Transact all detected events
    (when (seq @events)
      @(d/transact conn @events))
    
    @events))

;; Query patterns for drilling intelligence
(def queries
  {:pressure-spike
   '[:find ?e ?depth ?pressure ?rop ?topics
     :where
     [?e :drilling/pressure ?pressure]
     [(> ?pressure 4000.0)]
     [?e :drilling/depth ?depth]
     [?e :drilling/rop ?rop]
     [?e :synapse/topics ?topics]]
   
   :stuck-pipe-risk
   '[:find ?e ?depth ?pressure ?rop ?torque
     :where
     [?e :drilling/pressure ?pressure]
     [?e :drilling/rop ?rop]
     [?e :drilling/torque ?torque]
     [?e :drilling/depth ?depth]
     [(> ?pressure 4000.0)]
     [(< ?rop 20.0)]
     [(> ?torque 15000.0)]]
   
   :critical-alerts
   '[:find ?e ?type ?confidence ?depth
     :where
     [?e :synapse/topics :synapse/critical-alert]
     [?e :event/type ?type]
     [?e :event/confidence ?confidence]
     [?e :drilling/depth ?depth]]
   
   :event-cascade
   '[:find ?event ?trigger-id ?event-type
     :where
     [?event :synapse/triggered-by ?trigger-id]
     [?event :event/type ?event-type]]
   
   :topic-activity
   '[:find ?topic (count ?e)
     :where
     [?e :synapse/topics ?topic]]})

(comment
  ;; Example usage:
  (def conn (init-database))
  
  ;; Create measurements
  (def m1 (create-instance
            :thing/measurement
            :measurement/m001 ;; Fixed: valid keyword
            {:drilling/depth 5000.0
             :drilling/pressure 3500.0
             :drilling/rop 45.0
             :drilling/torque 12000.0
             :drilling/temperature 150.0
             :event/timestamp (java.util.Date.)
             :synapse/topics [:synapse/drilling-data :synapse/normal-operations]}))
  
  (def m2 (create-instance
            :thing/measurement
            :measurement/m002 ;; Fixed: valid keyword
            {:drilling/depth 5010.0
             :drilling/pressure 4200.0 ;; Spike!
             :drilling/rop 15.0 ;; Drop!
             :drilling/torque 18000.0 ;; High!
             :drilling/temperature 155.0
             :event/timestamp (java.util.Date.)
             :synapse/topics [:synapse/drilling-data :synapse/anomaly]}))
  
  ;; Transact and detect
  @(d/transact conn [m1 m2])
  (detect-drilling-events conn m2)
  
  ;; Query
  (d/q (:stuck-pipe-risk queries) (d/db conn))
  )
