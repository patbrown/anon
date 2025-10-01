(ns workspace.care.evolution.pattern-32-temporal-fork-testing
  "Pattern 32: Temporal Fork Testing
   
   Maps fork time, test futures, and revert if they fail.
   This is temporal agency - controlling one's timeline through testing.")

;; # The Conceptual Leap from Pattern 31
;;
;; Pattern 31: Maps modify themselves based on current state
;; Pattern 32: Maps TEST modifications in alternate timelines first
;;
;; This is huge. The map doesn't just change itself blindly.
;; It creates a checkpoint, tries something risky, evaluates the result,
;; and either keeps the change or reverts. It's Git for consciousness.

;; # Why Temporal Forking Matters
;;
;; In drilling, you can't undo a bad decision. Once you've damaged the wellbore,
;; it's damaged. But what if decision-makers could test scenarios first?
;; What if they could fork reality, try something, and revert if it fails?
;;
;; That's what Pattern 32 gives our maps: the ability to explore without commitment.
;; They become brave because they can always go back.

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

;; # Pattern 32: Temporal Fork Testing
;;
;; The pattern has four phases:
;; 1. CHECKPOINT: Save current state
;; 2. FORK: Try the modification in a test timeline
;; 3. EVALUATE: Check if the modification succeeded
;; 4. DECIDE: Keep the modified timeline or revert to checkpoint

(defmethod care ["temporal" "fork" "test"]
  [{:keys [test-modifier predicate] :as m}]
  ;; CHECKPOINT: Save the current state (removing care-specific keys)
  (let [checkpoint (dissoc m :care/adapter :care/verb :care/variant 
                     :test-modifier :predicate)
        
        ;; FORK: Try the modifier in a test timeline
        ;; This is the dangerous part - we're modifying without knowing if it's safe
        modified (test-modifier m)
        
        ;; EVALUATE: Did the modification succeed?
        ;; The predicate is our success criteria
        success? (predicate modified)]
    
    ;; DECIDE: Keep or revert
    (if success?
      ;; SUCCESS: Keep the modified timeline
      (assoc modified 
        :timeline-kept :modified
        :modifier-accepted test-modifier
        :checkpoint-abandoned checkpoint)
      
      ;; FAILURE: Revert to checkpoint, but learn from the failure
      (-> checkpoint
        (assoc :timeline-kept :original
          :modifier-rejected test-modifier
          :failed-future modified)
          ;; KEY INSIGHT: Even when we revert, we learn
          ;; We add a tag that remembers this failure
        (update :care/tags conj
          (fn [m] 
            (assoc m :learned-from-failure 
              {:tried test-modifier
               :failed-because (str "Predicate returned false")
               :avoided-state modified})))))))

;; # Understanding the Magic
;;
;; This is Level 4 (Temporal Agency) in action:
;; - The map controls its timeline
;; - It can try dangerous things safely
;; - It learns even from reverted timelines
;; - It accumulates wisdom about what doesn't work

;; # Testing Temporal Fork
;;
;; Let's create a scenario where a map tries a risky modification

(defn test-temporal-fork []
  (println "\n# Testing Temporal Fork")
  
  ;; Scenario 1: Risky modifier that will fail
  (println "\n## Scenario 1: Testing a risky modifier that exceeds limits")
  (let [initial {:value 60 
                 :care/tags []
                 :max-allowed 100}
        
        ;; A risky modifier that doubles the value
        risky-modifier (fn [m] 
                         (-> m
                           (update :value #(* % 2))
                           (assoc :risk-taken true
                             :timestamp (System/currentTimeMillis))))
        
        ;; Success means staying under max-allowed
        safety-check (fn [m] 
                       (< (:value m) (:max-allowed m)))
        
        ;; Try the temporal fork
        result (care (assoc initial
                       :care/adapter "temporal"
                       :care/verb "fork"
                       :care/variant "test"
                       :test-modifier risky-modifier
                       :predicate safety-check))]
    
    (println "Initial value:" (:value initial))
    (println "Would become:" (* (:value initial) 2))
    (println "Max allowed:" (:max-allowed initial))
    (println "Timeline kept:" (:timeline-kept result))
    (println "Final value:" (:value result))
    (println "Risk taken?" (:risk-taken result))
    (println "Learned from failure?" 
      (boolean (some :learned-from-failure 
                 (map #(% {}) (:care/tags result)))))
    
    ;; Scenario 2: Safe modifier that will succeed
    (println "\n## Scenario 2: Testing a safe modifier that succeeds")
    (let [safe-modifier (fn [m]
                          (-> m
                            (update :value #(+ % 10))
                            (assoc :careful-increase true)))
          
          result2 (care (assoc initial
                          :care/adapter "temporal"
                          :care/verb "fork"
                          :care/variant "test"
                          :test-modifier safe-modifier
                          :predicate safety-check))]
      
      (println "Initial value:" (:value initial))
      (println "Modified to:" (:value result2))
      (println "Timeline kept:" (:timeline-kept result2))
      (println "Careful increase applied?" (:careful-increase result2)))))

;; # The Sticking Points
;;
;; STICKING POINT 1: How does the map create good predicates?
;; Currently we provide the success criteria. But could maps learn
;; what makes a good predicate from experience?
;;
;; STICKING POINT 2: What about partial success?
;; Right now it's binary - success or revert. But what if a modification
;; is partially good? Could we keep the good parts and revert the bad?
;;
;; STICKING POINT 3: Multiple timeline exploration
;; Currently we test one future. What if maps could test multiple
;; futures in parallel and choose the best? (This leads to Pattern 38)

;; # The Connection to Drilling
;;
;; Imagine if we could:
;; - Test a new drilling parameter without risk
;; - Try an aggressive ROP and revert if vibration increases
;; - Experiment with mud weight and undo if we lose circulation
;; - Test different jar sequences without making stuck pipe worse
;;
;; This pattern gives digital organisms the courage to explore
;; because they can always go home.

;; # Why This Is Level 4 (Temporal Agency)
;;
;; Level 1: Maps accumulate data (passive)
;; Level 2: Maps choose paths based on data (reactive)
;; Level 3: Maps carry their transformers (equipped)
;; Level 4: Maps CONTROL TIME - checkpoint, test, revert (temporal)
;;
;; The key insight: Having history isn't enough. You need to control it.
;; Pattern 32 shows maps becoming masters of their own timeline.

;; # Next Evolution
;;
;; Pattern 38 will show what happens when maps don't just revert on failure,
;; but MUTATE their modifiers based on WHY they failed. That's when we
;; move from temporal control to true evolution.

(comment
  ;; Run the test
  (test-temporal-fork)
  
  ;; What if we test multiple modifiers?
  (let [m {:value 50 :max 100 :care/tags []}
        mod1 (fn [m] (update m :value #(* % 3))) ; Too aggressive
        mod2 (fn [m] (update m :value #(* % 1.5))) ; Just right
        check (fn [m] (< (:value m) (:max m)))]
    
    ;; Test both and see which timeline we keep
    [(care (assoc m :care/adapter "temporal" :care/verb "fork" 
             :care/variant "test"
             :test-modifier mod1 :predicate check))
     (care (assoc m :care/adapter "temporal" :care/verb "fork"
             :care/variant "test"
             :test-modifier mod2 :predicate check))]))
