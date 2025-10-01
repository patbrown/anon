(ns care
  "Enhanced CARE with universal tags and traits support.
   
   This module provides a drop-in replacement for care.core that adds
   universal behavior composition through tags and traits while maintaining
   complete backward compatibility."
  (:require [care-basic]
            [care-tags]
            [metastructures]
            [orchestra.core #?(:clj :refer :cljs :refer-macros) [defn-spec]]))

;; ============================================================================
;; ENHANCED CARE MULTI-METHOD
;; ============================================================================

(defmulti care-enhanced
  "Enhanced CARE multi-method with universal care-tags/traits support.
   
   Uses same dispatch as original but adds post-dispatch tag/trait composition.
   Maintains complete backward compatibility - existing calls work unchanged."
  (fn [m] [(name (or (:care/adapter m) :default))
           (name (or (:care/verb m) :default))
           (name (or (:care/variant m) :default))]))

;; Default method that provides universal tag/trait support
(defmethod care-enhanced :default [care-map]
  (let [;; Apply tags and traits composition first
        composed-map (care-tags/employ-tags care-map)
        ;; Then execute through original CARE system
        result (care-basic/care-mm composed-map)]
    result))

;; ============================================================================
;; CONVENIENCE FUNCTIONS
;; ============================================================================

(defn-spec *care :metastructures/fn
  "Enhanced care function with care-tags/traits support.
   
   Maintains same interface as original care function but adds universal
   behavior composition. Existing code works unchanged."
  [base :metastructures/map]
  (fn [overrides]
    (care-enhanced (merge base overrides))))

(def care (*care {}))

(defn-spec care-with-tags :metastructures/map
  "Convenience function for applying tags to a CARE operation.
   
   Usage:
   (care-with-tags {:care/db :ndb :care/verb :add :care/args [...]}
                   [:tag/audit-fields :tag/validate])
   
   Equivalent to:
   (care-enhanced {:care/db :ndb :care/verb :add :care/args [...]
                   :care/options {:care/behavior :options/tags
                                 :care/tags [:tag/audit-fields :tag/validate]}})"
  [care-map :metastructures/map tag-names :metastructures/vec]
  (care-enhanced (assoc care-map :care/options 
                   {:care/behavior :options/tags
                    :care/tags tag-names})))

(defn-spec care-with-traits :metastructures/?
  "Convenience function for applying traits to a CARE operation."
  [care-map :metastructures/map trait-names :metastructures/vec]
  (care-enhanced (assoc care-map :care/options
                   {:care/behavior :options/traits
                    :care/traits trait-names})))

(defn-spec care-with-tags-and-traits :metastructures/?
  "Convenience function for applying both tags and traits to a CARE operation."
  [care-map :metastructures/map tag-names :metastructures/vec trait-names :metastructures/vec]
  (care-enhanced (assoc care-map :care/options
                   {:care/behavior :options/tags-and-traits
                    :care/tags tag-names
                    :care/traits trait-names})))

;; ============================================================================
;; DEBUGGING AND INTROSPECTION
;; ============================================================================

(defn-spec preview-composition :metastructures/map
  "Preview what a CARE map would look like after tag/trait composition.
   
   Useful for debugging and understanding how tags compose together.
   Does not execute the operation, just shows the composed map."
  [care-map :metastructures/map]
  (care-tags/employ-tags care-map))

(defn-spec explain-tags :metastructures/vec
  "Explain what tags would be applied to a CARE map.
   
   Returns vector of {:tag-name :tag/example :definition {...}} maps
   showing exactly which tags would be applied and their definitions."
  [care-map :metastructures/map]
  (if (care-tags/trait-enabled? care-map :trait/with-tags?)
    (mapv (fn [tag-name]
            {:tag-name tag-name
             :definition (care-tags/get-tag care-map tag-name)})
      (keys (:care/tags care-map)))
    []))

(defn-spec explain-traits :metastructures/vec
  "Explain what traits would be applied to a CARE map."
  [care-map :metastructures/map]
  (mapv (fn [trait-name]
          {:trait-name trait-name
           :definition (get-in care-map [:trait-registry trait-name])})
    (:care/traits care-map [])))

;; ============================================================================
;; BACKWARD COMPATIBILITY ALIASES
;; ============================================================================

;; For backward compatibility, also export under original name
(def care-mm care-enhanced)
(defn-spec *care :metastructures/fn
  "Create a specialized CARE function with defaults baked in.
   
   This is the secret sauce for making domain-specific functions.
   
   ## Examples
   ```clojure
   ;; Create specialized functions
   (def save-user (*care {:care/adapter :users/jam
                          :care/verb :jam/add
                          :care/variant :default}))
   
   (def send-email (*care {:care/adapter :email/smtp
                           :care/verb :email/send
                           :care/variant :default}))
   
   (def render-component (*care {:care/adapter :dom/react
                                 :care/verb :component/render
                                 :care/variant :default}))
   
   ;; Use them simply
   (save-user {:care/args [[\"alice\"] {:name \"Alice\"}]})
   (send-email {:to \"alice@example.com\" :subject \"Welcome\"})
   (render-component {:component MyApp :container \"#root\"})
   ```"
  [defaults :metastructures/map]
  (fn [overrides]
    (care-mm (merge defaults overrides))))
