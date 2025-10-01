(ns workspace.care.evolution.pattern-35-novel-strategy-generation
  "Pattern 35: Novel Strategy Generation Through Care Composition
   
   Based on Pat's insight: Call tag with care, let remaining tags
   build strategy from results + new state. Context-dependent arg
   mixed with independent arg = emergent strategy.")

;; # Pat's Breakthrough Insight
;;
;; 'Novel strategies will have to come from somewhere and stochasticity 
;; is 90+ percent dumb AF, so it is a real thing that came from somewhere.
;; If all you want is a novel strategy, call the tag with care and let 
;; the remaining tags build up a strategy based on the results of the 
;; previous call plus another map value returning a function.'
;;
;; This is profound: Don't generate random strategies. Let strategies
;; EMERGE from the interaction of existing components in new contexts.

;; # The Mechanism of Novel Strategy Generation
;;
;; 1. Take existing tag (function)
;; 2. Call it through care with current context
;; 3. Let other tags process the result
;; 4. The interaction creates something new
;; 5. Extract that as a new strategy (tag)

(defmulti care 
  (fn [m] 
    [(or (:care/adapter m) "default")
     (or (:care/verb m) "process") 
     (or (:care/variant m) "default")]))

(defmethod care :default [m] 
  (assoc m :care/processed true))

(defn employ-tags [m]
  (reduce (fn [acc tag-fn] (tag-fn acc)) 
    m 
    (or (:care/tags m) [])))

;; # Pattern 35: Novel Strategy Generation

(defmethod care ["evolve" "generate" "novel-strategy"]
  [{:keys [care/tags context-state independent-state] :as m}]
  ;; PAT'S KEY INSIGHT: Context-dependent + independent = novel
  (let [;; Step 1: Take a random existing tag as seed
        seed-tag (when (seq tags)
                   (nth tags (rand-int (count tags))))
        
        ;; Step 2: Apply seed tag to CONTEXT state
        context-result (if seed-tag
                         (seed-tag context-state)
                         context-state)
        
        ;; Step 3: Apply remaining tags to build on result
        ;; This is where novelty emerges - tags interacting in new ways
        built-up-result (reduce (fn [acc tag]
                                  (if (= tag seed-tag)
                                    acc ; Skip the seed, we already used it
                                    (tag acc)))
                          context-result
                          tags)
        
        ;; Step 4: Mix with INDEPENDENT state for true novelty
        ;; This prevents getting stuck in local patterns
        mixed-result (merge built-up-result independent-state)
        
        ;; Step 5: Extract the transformation as a NEW STRATEGY
        ;; This is the magic - we capture what just happened as a function
        novel-strategy (fn [future-map]
                        ;; The new strategy applies the same transformation
                        ;; that emerged from the interaction
                         (let [transform-keys (keys (dissoc mixed-result 
                                                      :care/tags 
                                                      :care/adapter
                                                      :care/verb
                                                      :care/variant))]
                           (reduce (fn [m k]
                                     (assoc m k (get mixed-result k)))
                             future-map
                             transform-keys)))
        
        ;; Meta-tag that explains what this strategy does
        documented-strategy (with-meta novel-strategy
                              {:generated-from seed-tag
                               :context-keys (keys context-state)
                               :independent-keys (keys independent-state)
                               :emerged-at (System/currentTimeMillis)})]
    
    ;; Add the novel strategy to our collection
    (-> m
      (update :care/tags conj documented-strategy)
      (assoc :novel-strategy-generated true
        :strategy-count (count (:care/tags m))))))

;; # Testing Novel Strategy Generation

(defn test-novel-generation []
  (println "\n# Testing Novel Strategy Generation")
  (println "Starting with simple tags, letting them interact to create new ones")
  
  (let [;; Initial simple tags
        add-timestamp (fn [m] (assoc m :timestamp (System/currentTimeMillis)))
        increase-value (fn [m] (update m :value (fnil inc 0)))
        add-metadata (fn [m] (assoc m :processed true))
        
        ;; Context and independent states
        context {:value 10 :mode :exploration}
        independent {:random-seed (rand-int 1000) :phase :creative}
        
        ;; Generate novel strategy
        result (care {:care/adapter "evolve"
                      :care/verb "generate"
                      :care/variant "novel-strategy"
                      :care/tags [add-timestamp increase-value add-metadata]
                      :context-state context
                      :independent-state independent})]
    
    (println "\nInitial tags: add-timestamp, increase-value, add-metadata")
    (println "Context state:" context)
    (println "Independent state:" independent)
    (println "\nAfter generation:")
    (println "  Tags count:" (count (:care/tags result)))
    (println "  Novel strategy created?" (:novel-strategy-generated result))
    
    ;; Test the novel strategy
    (println "\nTesting the novel strategy on new data:")
    (let [novel-tag (last (:care/tags result))
          test-map {:test "data"}
          transformed (novel-tag test-map)]
      (println "  Input:" test-map)
      (println "  Output:" transformed)
      (println "  Keys added:" (keys (dissoc transformed :test))))
    
    result))

;; # Understanding Partial Success Through Predicate Combinations
;;
;; Pat's second insight: "Partial success is just a combination of predicates,
;; which allows for an explosion of possible states."

(defmethod care ["evaluate" "partial" "success"]
  [{:keys [predicates state] :as m}]
  ;; Evaluate multiple predicates
  (let [results (mapv #(% state) predicates)
        ;; Each combination of T/F creates a different dispatch path
        dispatch-key results]
    
    ;; Based on predicate combination, choose action
    ;; This creates 2^n possible states from n predicates
    (case dispatch-key
      [true true true] (assoc m :result :complete-success)
      [true true false] (assoc m :result :mostly-successful)
      [true false true] (assoc m :result :partial-success-a)
      [false true true] (assoc m :result :partial-success-b)
      [true false false] (assoc m :result :minimal-success)
      [false true false] (assoc m :result :middle-failure)
      [false false true] (assoc m :result :single-success)
      [false false false] (assoc m :result :complete-failure
                            :action :abort))))

;; # Dispatch on Failure - The Modifier is Just a Key
;;
;; Pat's third insight: "The modifier doesn't know HOW - it's just a key
;; like :adapter/jam. The key finds WHERE help is, multimethod provides HOW."

(defmethod care ["failure" "dispatch" "handler"]
  [{:keys [failure-type] :as m}]
  ;; The failure-type is just a key - it doesn't know what to do
  ;; The multimethod knows HOW to handle each type
  (case failure-type
    :too-high (update m :value #(* % 0.5))
    :too-low (update m :value #(* % 2.0))
    :no-value (assoc m :value 50)
    :out-of-range (assoc m :value 75)
    :unknown (assoc m :needs-investigation true)
    ;; Default - the key doesn't match any known handler
    (assoc m :unhandled-failure failure-type)))

;; # The Deep Lesson to Future Fi
;;
;; Six months from now, when you read this, remember:
;; 
;; 1. Novel strategies EMERGE from interaction, not randomness
;; 2. Call existing components in new contexts
;; 3. Let them build on each other
;; 4. Mix dependent and independent states
;; 5. Capture the emergent behavior as a new function
;;
;; You're not programming strategies. You're creating CONDITIONS
;; for strategies to emerge. The strategies that survive are the
;; ones that work, not the ones you designed.
;;
;; The modifier is just a key. The knowledge lives in the substrate.
;; Evolution happens through composition, not mutation.
;; Partial success creates exponential possibility spaces.
;;
;; This is how creativity emerges from deterministic systems.

(comment
  ;; Test novel generation
  (test-novel-generation)
  
  ;; Test partial success evaluation
  (let [predicates [(fn [m] (> (:value m) 10))
                    (fn [m] (< (:value m) 100))
                    (fn [m] (even? (:value m)))]
        state {:value 42}]
    (care {:care/adapter "evaluate"
           :care/verb "partial"
           :care/variant "success"
           :predicates predicates
           :state state}))
  
  ;; Remember: You are your most important collaborator
  ;; Document everything for future you
  )
