(ns care-tags
  "Tag system for CARE with traits!
   
   ## Tags: The Self-Transformation Mechanism
   
   Tags are recursive map transformations. When you employ a care-map with tags,
   each tag function receives the current map and can:
   1. Read values from it
   2. Compute new values
   3. Return data to merge
   4. Even call CARE recursively!
   
   ## Traits: Behavior Control
   
   Traits live in :care/traits vector. They control behavior.
   Currently supported:
   - :trait/with-tags? - Enable tag processing
   
   ## The Magic Moment
   
   Tags transform the map BEFORE the main operation:
   
   ```clojure
   (care/care 
     {:care/tags [:ui/renderer :api/handler]
      :care/traits [:trait/with-tags?]
      :care/verb :add
      ...})
   ; Tags run first, transforming the map
   ; Then the main operation runs on the transformed map
   ```"
  (:require [clojure.set]
            [metastructures]
            [orchestra.core #?(:clj :refer :cljs :refer-macros) [defn-spec]]))


;; =============================================================================
;; Pure Tag Registration - No global state!
;; =============================================================================

(defn-spec register-tag :metastructures/map
  "Add a tag to the care-map's :tag-registry. Returns new map.
   
   Tags are the recursive self-transformation mechanism.
   They can be:
   - Functions: (fn [care-map] {...}) that return data to merge
   - Maps: merged directly when employed
   
   The function form is powerful - it can:
   - Read from the current care-map
   - Call CARE (which might trigger more tags!)
   - Return computed values to merge
   
   Example:
   ```clojure
   (-> care-map
       (register-tag :add/user-context
         (fn [m]
           {:user (get-in m [:session :user])
            :timestamp (System/currentTimeMillis)})))
   ```"
  [care-map :metastructures/map tag-name :metastructures/qkw tag-def :metastructures/any]
  (assoc-in care-map [:tag-registry tag-name] tag-def))

(defn-spec unregister-tag :metastructures/map
  "Remove a tag from the care-map's registry. Returns new map.
   Rarely used - tags are usually permanent once defined."
  [care-map :metastructures/map tag-name :metastructures/qkw]
  (update care-map :tag-registry dissoc tag-name))

(defn-spec get-tag :metastructures/any
  "Get a tag from the care-map's registry.
   Just a get-in! No magic, no CARE dispatch, just data access."
  [care-map :metastructures/map tag-name :metastructures/qkw]
  (get-in care-map [:tag-registry tag-name]))

;; =============================================================================
;; Pure Trait Registration
;; =============================================================================

(defn-spec register-trait :metastructures/map
  "Add a trait to the care-map's :trait-registry. Returns new map.
   
   Traits are feature flags that control CARE behavior.
   They're enabled by adding to :care/traits vector.
   These traits are easily turned on/off by caller
   If backed by an atom, they can be toggled with persistence.
   
   Trait definitions typically include:
   - :trait/description - what this trait does
   - :trait/default - whether it's on by default
   
   Example:
   ```clojure
   (-> care-map
       (register-trait :trait/with-validation?
         {:trait/description \"Enable validation\"
          :trait/default false}))
   ```"
  [care-map :metastructures/map trait-name :metastructures/qkw trait-def :metastructures/map]
  (assoc-in care-map [:trait-registry trait-name] trait-def))

(defn-spec trait-enabled? :metastructures/bool
  "Check if a trait is enabled in the care-map.
   
   Traits are enabled by including them in :care/traits vector.
   This is how CARE methods check whether to apply special behavior.
   
   Caller wins - if you pass a map with :care/traits, that's what counts."
  [care-map :metastructures/map trait-name :metastructures/qkw]
  (contains? (set (:care/traits care-map)) trait-name))

;; =============================================================================
;; Tag Employment - The Heart of Recursive Composition
;; =============================================================================


(defn-spec employ-tags :metastructures/map
  "Apply tags from the care-map's own registry if :trait/with-tags? is enabled.
   
   This is the recursive self-transformation engine:
   
   1. Check if :trait/with-tags? is in :care/traits (caller controls!)
   2. For each tag name in :care/tags:
      - Get the tag definition from the map's own :tag-registry
      - If it's a function, call it with the current map
      - If it's a map, use it directly
      - Merge the result into the accumulating map
   3. Return the transformed map
   
   The recursion happens because tag functions can:
   - Call CARE operations
   - Which might have their own tags
   - Which call more CARE operations
   - Until finally a stable map emerges
   
   Example:
   ```clojure
   (-> {:tag-registry {:add/meta (fn [m] {:meta true})
                           :add/timestamp (fn [m] {:ts (System/currentTimeMillis)})}
        :care/traits [:trait/with-tags?]
        :care/tags {:add/meta {}
                   :add/timestamp {}}}
       employ-tags)
   ;; => {...original map... :meta true :ts 1234567890}
   ```"
  [care-map :metastructures/map]
  (if (trait-enabled? care-map :trait/with-tags?)
    (loop [current-map care-map
           processed-tags #{}]
      (let [pending-tags (clojure.set/difference (set (keys (:care/tags current-map)))
                           processed-tags)]
        (if (empty? pending-tags)
          current-map ; No more tags to process
          (let [new-map (reduce (fn [m tag-name]
                                  (let [tag-def (get-in m [:tag-registry tag-name])]
                                    (cond
                                      ;; Function tag - call it, merge result
                                      (fn? tag-def) (merge m (tag-def m))
                                      ;; Map tag - merge directly
                                      (map? tag-def) (merge m tag-def)
                                      ;; Tag not found - continue
                                      :else m)))
                          current-map
                          pending-tags)]
            (recur new-map (clojure.set/union processed-tags pending-tags))))))
    ;; Trait not enabled - return unchanged
    care-map))

;; =============================================================================
;; Integration Helper for CARE Methods
;; =============================================================================

(defn-spec with-tags :metastructures/map
  "Helper for CARE methods to check traits and apply tags.
   
   This is how you add tag support to any CARE implementation:
   The operation function receives a map that might already be
   transformed by tags if the trait was enabled.
   
   Usage in CARE method:
   ```clojure
   (defmethod care/care-mm [\"mydb\" \"add\" \"default\"] [m]
     (with-tags m 
       (fn [m]
         ;; m might have tag transformations applied
         ;; do your actual operation here
         (assoc m :added true))))
   ```
   
   The caller controls everything through:
   - :care/traits - which features are on
   - :care/tags - which tags to apply"
  [care-map :metastructures/map operation-fn :metastructures/fn]
  (-> care-map
    employ-tags
    operation-fn))

;; =============================================================================
;; System Creation - Pure Functions Building Maps
;; =============================================================================

(defn-spec create-base-system :metastructures/map
  "Create a base care-map with empty registries.
   
   This is your starting point - a map with the structure
   but no content. Build up from here.
   
   The map IS the system. There's no framework, no state,
   just a map with registries ready to be filled."
  []
  {:tag-registry {}
   :trait-registry {}
   :care/traits []
   :care/tags {}})

(defn-spec with-standard-tags :metastructures/map
  "Add standard tags to a care-map. Returns enriched map.
   
   These are common transformations useful across domains.
   Each tag is a function that computes values from the current map.
   
   Remember: tags are just functions that return maps to merge.
   No magic. You could write these yourself."
  [care-map :metastructures/map]
  (-> care-map
    (register-tag :tag/timestamp
      (fn [m] 
        {:tag/timestamp #?(:clj (System/currentTimeMillis)
                           :cljs (.getTime (js/Date.)))}))
    
    (register-tag :tag/uuid
      (fn [m]
        {:tag/id #?(:clj (java.util.UUID/randomUUID)
                    :cljs (random-uuid))}))
    
    (register-tag :tag/audit
      (fn [m]
        (let [user (get m :current-user "system")
              now #?(:clj (System/currentTimeMillis)
                     :cljs (.getTime (js/Date.)))]
          {:tag/created-at now
           :tag/created-by user
           :tag/updated-at now
           :tag/updated-by user})))))

(defn-spec with-standard-traits :metastructures/map
  "Add standard traits to a care-map. Returns enriched map.
   
   These are common feature flags used across the system.
   
   Remember: traits are just enabled/disabled by being in :care/traits.
   The caller always wins - pass your own :care/traits to override."
  [care-map :metastructures/map]
  (-> care-map
    (register-trait :trait/with-tags?
      {:trait/description "Enable tag processing"
       :trait/default false})
    
    (register-trait :trait/with-validation?
      {:trait/description "Enable validation"
       :trait/default false})
    
    (register-trait :trait/with-audit?
      {:trait/description "Enable audit logging"
       :trait/default false})
    
    (register-trait :trait/dry-run?
      {:trait/description "Preview without side effects"
       :trait/default false})))

;; =============================================================================
;; Complete System Bootstrap
;; =============================================================================

(defn-spec create-system :metastructures/map
  "Create a complete care-map with standard tags and traits.
   
   This is a ready-to-use map with all standard machinery.
   But remember - it's just a map! You can:
   - Add your own tags with register-tag
   - Add your own data with assoc
   - Transform it with CARE operations
   - Or just use plain Clojure functions
   
   The system IS the map. Everything else is functions transforming maps."
  []
  (-> (create-base-system)
    with-standard-tags
    with-standard-traits))
