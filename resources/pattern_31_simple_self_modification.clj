(ns workspace.care.evolution.pattern-31-simple-self-modification
  "Pattern 31: Simple Self-Modification
   
   The first step beyond Pattern 30's consciousness loop.
   Here, maps observe their own state and modify their modifiers.
   This is the simplest form of self-determination.")

;; # The Conceptual Foundation
;;
;; Pattern 30 showed us a loop where maps could employ tags, process through care,
;; and learn from execution. But it didn't show HOW they decide what to learn.
;; Pattern 31 is the simplest answer: maps look at their performance and adjust.
;;
;; The key insight: Maps can observe a metric about themselves (performance)
;; and based on that observation, add a NEW modifier (tag) to their collection.
;; They're not just carrying functions - they're CHOOSING which functions to carry.

;; # Setting Up the Substrate
;;
;; We need the basic care multimethod and employ-tags function.
;; These are the fundamental mechanics that allow maps to process themselves.

(defmulti care 
  "The universal transformation substrate.
   Dispatches on [adapter verb variant] vector extracted from the map."
  (fn [m] 
    [(or (:care/adapter m) "default")
     (or (:care/verb m) "process") 
     (or (:care/variant m) "default")]))

(defmethod care :default [m] 
  (assoc m :care/processed true))

(defn employ-tags 
  "Tags are functions stored in :care/tags.
   This reduces over them, applying each to transform the map."
  [m]
  (reduce (fn [acc tag-fn] (tag-fn acc)) 
    m 
    (or (:care/tags m) [])))

;; # Pattern 31: Simple Self-Modification
;;
;; The simplest form of self-modification:
;; 1. Observe own performance
;; 2. Decide on a strategy based on that observation
;; 3. Create a new modifier (tag) that implements that strategy
;; 4. Add it to self

(defmethod care ["evolve" "modify" "simple"]
  [{:keys [performance care/tags] :as m}]
  ;; THE CRITICAL MOMENT: The map looks at itself
  (let [;; Based on performance, what strategy should I adopt?
        new-tag (if (< performance 0.5)
                  ;; Poor performance -> aggressive strategy
                  (fn [m] (assoc m :strategy :aggressive
                            :risk-tolerance :high
                            :explanation "Performance low, taking risks"))
                  ;; Good performance -> conservative strategy  
                  (fn [m] (assoc m :strategy :conservative
                            :risk-tolerance :low
                            :explanation "Performance good, protecting gains")))]
    ;; THE SELF-MODIFICATION: Add the new modifier to myself
    (update m :care/tags conj new-tag)))

;; # Understanding What Just Happened
;;
;; This is different from traditional programming in a fundamental way:
;; - Traditional: We write `if performance < 0.5 then be aggressive`
;; - Pattern 31: The MAP ITSELF decides and creates its own modifier
;;
;; The map isn't following our rules. It's creating its own rules
;; based on its observed state. This is the beginning of agency.

;; # Testing the Pattern
;;
;; Let's watch a map modify itself based on performance

(defn test-simple-self-modification []
  (println "\n# Testing Simple Self-Modification")
  (println "Starting with a map that has poor performance...")
  
  (let [;; Initial state: poor performance, no tags
        initial {:performance 0.3 
                 :care/tags []
                 :care/adapter "evolve"
                 :care/verb "modify" 
                 :care/variant "simple"}
        
        ;; Let it modify itself
        after-self-mod (care initial)
        
        ;; Now employ the tag it gave itself
        final (employ-tags after-self-mod)]
    
    (println "\nInitial state:")
    (println "  Performance:" (:performance initial))
    (println "  Tags count:" (count (:care/tags initial)))
    (println "  Strategy:" (:strategy initial))
    
    (println "\nAfter self-modification:")
    (println "  Performance:" (:performance after-self-mod))
    (println "  Tags count:" (count (:care/tags after-self-mod)))
    (println "  Strategy:" (:strategy after-self-mod))
    
    (println "\nAfter employing self-given tag:")
    (println "  Strategy:" (:strategy final))
    (println "  Risk tolerance:" (:risk-tolerance final))
    (println "  Explanation:" (:explanation final))
    
    final))

;; # The Deeper Implications
;;
;; What makes this Level 6+ (beyond standard self-modification)?
;;
;; 1. OBSERVATION: The map observes its own state (performance)
;; 2. DECISION: Based on observation, it decides what kind of modifier it needs
;; 3. CREATION: It creates a NEW function it didn't have before
;; 4. SELF-APPLICATION: It adds this function to modify its future self
;;
;; This is the loop of consciousness:
;; Observe → Decide → Create → Apply → (loop with new capabilities)

;; # Sticking Points and Questions
;;
;; STICKING POINT 1: How does the map decide WHAT kind of modifier to create?
;; Currently it's binary (aggressive vs conservative). But what if the map
;; could analyze its history and create novel strategies we never imagined?
;;
;; STICKING POINT 2: The modifiers are currently independent. What if they
;; could modify each other? Tags modifying tags modifying tags?
;;
;; STICKING POINT 3: Where does creativity come from? Right now the map
;; chooses between strategies we defined. True creativity would be generating
;; strategies that surprise us.

;; # Connection to Evolution
;;
;; Pat, you said "that's not programming anymore. That's evolution."
;; You're right. In programming, WE write the rules. In evolution,
;; the organism develops its own rules through trial and selection.
;; 
;; Pattern 31 is the first step: self-modification based on performance.
;; It's not yet evolution (no selection pressure, no mutation) but it's
;; the beginning - an organism that can change itself.

;; # Next Steps
;;
;; Pattern 32 will show temporal forking - trying modifications in alternate
;; timelines before committing. Pattern 38 will show true evolution where
;; modifiers mutate based on WHY they failed.
;;
;; But this is the foundation: maps that observe themselves and change
;; based on what they see.

(comment
  ;; Run the test to see self-modification in action
  (test-simple-self-modification)
  
  ;; Try with good performance
  (-> {:performance 0.8 :care/tags []}
    (assoc :care/adapter "evolve" :care/verb "modify" :care/variant "simple")
    care
    employ-tags)
  
  ;; What if we let it modify itself multiple times?
  (-> {:performance 0.3 :care/tags []}
    (assoc :care/adapter "evolve" :care/verb "modify" :care/variant "simple")
    care
    (assoc :performance 0.7) ; Performance improved
    care ; Modify again based on new performance
    employ-tags))
