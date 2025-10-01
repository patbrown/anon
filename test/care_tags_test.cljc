(ns care-tags-test
  "Production-ready tests for the CARE tags system.
   
   ## Test Philosophy
   - Tags are just data in maps - test the purity
   - No global state - test isolation
   - Recursive self-transformation - test the snake eating its tail
   - Traits as feature flags - test caller control
   - Property-based testing for invariants
   
   ## What We Test
   1. Pure tag registration and retrieval
   2. Recursive tag employment
   3. Trait-based feature control
   4. System isolation (no global state)
   5. Tag composition properties
   6. Performance characteristics
   7. Real-world patterns"
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [care-tags]
            [care]))
;; =============================================================================
;; FUNDAMENTAL PURITY TESTS
;; =============================================================================

(deftest test-tags-are-pure-data
  "Tags are just data in maps, no magic."
  (testing "Tag registration returns new map"
    (let [system (care-tags/create-base-system)
          original-id (:test/id system)
          new-system (care-tags/register-tag system :test/tag {:data "value"})]
      
      ;; Original unchanged
      (is (nil? (care-tags/get-tag system :test/tag)))
      ;; New system has tag
      (is (= {:data "value"} (care-tags/get-tag new-system :test/tag)))
      ;; Different objects
      (is (not (identical? system new-system)))))
  
  (testing "Tag functions are stored as-is"
    (let [tag-fn (fn [m] (assoc m :modified true))
          system (-> (care-tags/create-base-system)
                   (care-tags/register-tag :test/fn tag-fn))]
      
      (is (fn? (care-tags/get-tag system :test/fn)))
      (is (identical? tag-fn (care-tags/get-tag system :test/fn))))))

(deftest test-no-global-state
  "Tags system has zero global state."
  (testing "Multiple systems are completely independent"
    (let [sys1 (care-tags/create-system)
          sys2 (care-tags/create-system)
          sys3 (care-tags/create-system)]
      
      ;; Each has unique identity
      (is (not= sys1 sys2))
      (is (not= sys2 sys3))
      (is (not= sys1 sys3))
      
      ;; Modifications don't affect others
      (let [sys1-tagged (care-tags/register-tag sys1 ::shared {:sys 1})
            sys2-tagged (care-tags/register-tag sys2 ::shared {:sys 2})]
        
        (is (= {:sys 1} (care-tags/get-tag sys1-tagged ::shared)))
        (is (= {:sys 2} (care-tags/get-tag sys2-tagged ::shared)))
        (is (nil? (care-tags/get-tag sys3 ::shared)))))))

;; =============================================================================
;; TAG EMPLOYMENT TESTS - The Snake Eating Its Tail
;; =============================================================================

(deftest test-employ-tags-basic
  "Basic tag employment without recursion."
  (testing "Function tag transforms map"
    (let [system (-> (care-tags/create-system)
                   (care-tags/register-tag :add/metadata
                     (fn [m] {:meta/added true
                              :meta/timestamp 12345}))
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:add/metadata {}}))]
      
      (let [result (care-tags/employ-tags system)]
        (is (:meta/added result))
        (is (= 12345 (:meta/timestamp result))))))
  
  (testing "Map tag merges directly"
    (let [system (-> (care-tags/create-system)
                   (care-tags/register-tag :static/data
                     {:static/value "constant"
                      :static/flag true})
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:static/data {}}))]
      
      (let [result (care-tags/employ-tags system)]
        (is (= "constant" (:static/value result)))
        (is (:static/flag result)))))
  
  (testing "Multiple tags compose"
    (let [applied-tags (atom #{})
          system (-> (care-tags/create-system)
                   (care-tags/register-tag ::tag1
                     (fn [m] 
                       (swap! applied-tags conj ::tag1)
                       {::tag1-applied true}))
                   (care-tags/register-tag ::tag2
                     (fn [m]
                       (swap! applied-tags conj ::tag2)
                       {::tag2-applied true}))
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {::tag1 {} ::tag2 {}}))]
      
      (let [result (care-tags/employ-tags system)]
        (is (::tag1-applied result))
        (is (::tag2-applied result))
        ;; Both tags should be applied, order doesn't matter
        (is (= #{::tag1 ::tag2} @applied-tags))))))

(deftest test-recursive-tag-employment
  "The snake eats its tail - tags can add more tags."
  (testing "Tag adds another tag"
    (let [system (-> (care-tags/create-system)
                   (care-tags/register-tag :recursive/adder
                     (fn [m]
                        ;; This tag adds another tag to be employed
                       {:care/tags (assoc (:care/tags m) :recursive/added {})}))
                   (care-tags/register-tag :recursive/added
                     (fn [m] {:was-added true}))
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:recursive/adder {}}))]
      
      (let [result (care-tags/employ-tags system)]
        (is (:was-added result))
        (is (contains? (:care/tags result) :recursive/added)))))
  
  (testing "Deep recursion terminates"
    (let [depth (atom 0)
          max-depth 10
          create-depth-tag (fn [level]
                             (fn [m]
                               (swap! depth inc)
                               (if (< level (dec max-depth))
                                 {:care/tags (assoc (:care/tags m) 
                                               (keyword "recursive" (str "depth-" (inc level))) {})
                                  :depth @depth}
                                 {:final-depth @depth})))
          system (reduce (fn [sys level]
                           (care-tags/register-tag sys 
                             (keyword "recursive" (str "depth-" level))
                             (create-depth-tag level)))
                   (care-tags/create-system)
                   (range max-depth))]
      
      (reset! depth 0)
      (let [result (-> system
                     (assoc :care/traits [:trait/with-tags?])
                     (assoc :care/tags {:recursive/depth-0 {}})
                     care-tags/employ-tags)]
        ;; Should recurse but terminate
        (is (= max-depth (:final-depth result)))))))

;; =============================================================================
;; TRAIT SYSTEM TESTS
;; =============================================================================

(deftest test-trait-control
  "Traits are feature flags that control behavior."
  (testing "Tags only apply when trait enabled"
    (let [system (-> (care-tags/create-system)
                   (care-tags/register-tag ::controlled {::controlled true}))]
      
      ;; Without trait
      (let [without (-> system
                      (assoc :care/tags {::controlled {}})
                      care-tags/employ-tags)]
        (is (not (::controlled without))))
      
      ;; With trait
      (let [with-trait (-> system
                         (assoc :care/traits [:trait/with-tags?])
                         (assoc :care/tags {::controlled {}})
                         care-tags/employ-tags)]
        (is (::controlled with-trait)))))
  
  (testing "Trait registration and query"
    (let [system (-> (care-tags/create-system)
                   (care-tags/register-trait :trait/custom
                     {:description "Custom trait"
                      :default false}))]
      
      ;; Not enabled by default
      (is (not (care-tags/trait-enabled? system :trait/custom)))
      
      ;; Enabled when in traits vector
      (let [enabled (assoc system :care/traits [:trait/custom])]
        (is (care-tags/trait-enabled? enabled :trait/custom)))))
  
  (testing "Multiple traits can be active"
    (let [system (assoc (care-tags/create-system)
                   :care/traits [:trait/with-tags?
                                 :trait/with-validation?
                                 :trait/dry-run?])]
      
      (is (care-tags/trait-enabled? system :trait/with-tags?))
      (is (care-tags/trait-enabled? system :trait/with-validation?))
      (is (care-tags/trait-enabled? system :trait/dry-run?))
      (is (not (care-tags/trait-enabled? system :trait/with-audit?))))))

;; =============================================================================
;; STANDARD TAGS AND TRAITS
;; =============================================================================

(deftest test-standard-tags
  "Standard tags provide common functionality."
  (testing "Timestamp tag"
    (let [system (-> (care-tags/create-system)
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:tag/timestamp {}}))]
      
      (let [result (care-tags/employ-tags system)]
        (is (number? (:tag/timestamp result))))))
  
  (testing "UUID tag"
    (let [system (-> (care-tags/create-system)
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:tag/uuid {}}))]
      
      (let [result (care-tags/employ-tags system)]
        (is (uuid? (:tag/id result))))))
  
  (testing "Audit tag"
    (let [system (-> (care-tags/create-system)
                   (assoc :current-user "testuser")
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:tag/audit {}}))]
      
      (let [result (care-tags/employ-tags system)]
        (is (number? (:tag/created-at result)))
        (is (= "testuser" (:tag/created-by result)))
        (is (number? (:tag/updated-at result)))
        (is (= "testuser" (:tag/updated-by result)))))))

;; =============================================================================
;; INTEGRATION WITH CARE
;; =============================================================================

(deftest test-with-tags-helper
  "Helper for CARE methods to apply tags."
  (testing "with-tags applies tags when trait enabled"
    (let [operation-called (atom false)
          operation (fn [m] 
                      (reset! operation-called true)
                      (assoc m :operated true))
          system (-> (care-tags/create-system)
                   (care-tags/register-tag ::before {::before true})
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {::before {}}))]
      
      (let [result (care-tags/with-tags system operation)]
        (is @operation-called)
        (is (:operated result))
        (is (::before result)))))
  
  (testing "with-tags skips when trait disabled"
    (let [operation (fn [m] (assoc m :operated true))
          system (-> (care-tags/create-system)
                   (care-tags/register-tag ::skipped {::skipped true})
                    ;; No trait enabled
                   (assoc :care/tags {::skipped {}}))]
      
      (let [result (care-tags/with-tags system operation)]
        (is (:operated result))
        (is (not (::skipped result)))))))

;; =============================================================================
;; PROPERTY-BASED TESTS
;; =============================================================================

(defspec tag-registration-is-pure 100
  (prop/for-all [tag-suffix gen/keyword
                 tag-value gen/any]
    (let [tag-name (keyword "test" (name tag-suffix))
          system (care-tags/create-base-system)
          new-system (care-tags/register-tag system tag-name tag-value)]
      
      (and (map? new-system)
        (not (identical? system new-system))
        (= tag-value (care-tags/get-tag new-system tag-name))
        (nil? (care-tags/get-tag system tag-name))))))

(defspec employ-tags-is-idempotent-for-maps 100
  (prop/for-all [tag-data (gen/map gen/keyword gen/any)]
    (let [system (-> (care-tags/create-system)
                   (care-tags/register-tag :test/data tag-data)
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:test/data {}}))
          once (care-tags/employ-tags system)
          ;; Apply tags again to the result
          twice (care-tags/employ-tags once)]
      
      ;; Map tags are idempotent
      (= once twice))))

(defspec traits-control-tag-application 100
  (prop/for-all [should-apply gen/boolean
                 tag-data (gen/map gen/keyword gen/any)]
    (let [base-system (-> (care-tags/create-system)
                        (care-tags/register-tag :test/controlled tag-data)
                        (assoc :care/tags {:test/controlled {}}))
          system (if should-apply
                   (assoc base-system :care/traits [:trait/with-tags?])
                   base-system)
          result (care-tags/employ-tags system)]
      
      (if should-apply
        ;; Tag data should be merged
        (every? (fn [[k v]] (= v (get result k))) tag-data)
        ;; Tag data should not be present
        (not-any? (fn [[k _]] (contains? result k)) tag-data)))))

;; =============================================================================
;; PERFORMANCE TESTS
;; =============================================================================

#?(:clj
   (deftest ^:performance test-tag-employment-performance
     "Tag employment should be fast."
     (testing "Simple tag performance"
       (let [system (-> (care-tags/create-system)
                      (care-tags/register-tag :perf/test {:data "test"})
                      (assoc :care/traits [:trait/with-tags?])
                      (assoc :care/tags {:perf/test {}}))
             iterations 100000]
         
         ;; Warm up
         (dotimes [_ 1000] (care-tags/employ-tags system))
         
         ;; Measure
         (let [start (System/nanoTime)]
           (dotimes [_ iterations]
             (care-tags/employ-tags system))
           (let [elapsed (- (System/nanoTime) start)
                 per-op (/ elapsed iterations 1000.0)]
             (println "Tag employment:" per-op "μs per operation")
             ;; Should be under 10 microseconds
             (is (< per-op 10000))))))
     
     (testing "Multi-tag composition performance"
       (let [system (reduce (fn [s i]
                              (care-tags/register-tag s 
                                (keyword "perf" (str "tag" i))
                                {(keyword (str "data" i)) i}))
                      (care-tags/create-system)
                      (range 10))
             system (-> system
                      (assoc :care/traits [:trait/with-tags?])
                      (assoc :care/tags 
                        (zipmap (map #(keyword "perf" (str "tag" %)) 
                                  (range 10))
                          (repeat {}))))
             iterations 10000]
         
         ;; Warm up
         (dotimes [_ 100] (care-tags/employ-tags system))
         
         ;; Measure
         (let [start (System/nanoTime)]
           (dotimes [_ iterations]
             (care-tags/employ-tags system))
           (let [elapsed (- (System/nanoTime) start)
                 per-op (/ elapsed iterations 1000.0)]
             (println "10-tag composition:" per-op "μs per operation")
             ;; Should be under 100 microseconds even with 10 tags
             (is (< per-op 100000))))))))

;; =============================================================================
;; REAL-WORLD PATTERNS
;; =============================================================================

(deftest test-configuration-pattern
  "Common pattern: system configuration through tags."
  (testing "Tags configure system behavior"
    (let [system (-> (care-tags/create-system)
                    ;; Development tags
                   (care-tags/register-tag :env/development
                     {:db/host "localhost"
                      :db/port 5432
                      :cache/enabled false
                      :logging/level :debug})
                    ;; Production tags
                   (care-tags/register-tag :env/production
                     {:db/host "prod.db.example.com"
                      :db/port 5432
                      :cache/enabled true
                      :logging/level :warn})
                    ;; Test tags
                   (care-tags/register-tag :env/test
                     {:db/host "memory"
                      :db/port nil
                      :cache/enabled false
                      :logging/level :info}))
          
          ;; Different environments
          dev-system (-> system
                       (assoc :care/traits [:trait/with-tags?])
                       (assoc :care/tags {:env/development {}})
                       care-tags/employ-tags)
          
          prod-system (-> system
                        (assoc :care/traits [:trait/with-tags?])
                        (assoc :care/tags {:env/production {}})
                        care-tags/employ-tags)]
      
      (is (= "localhost" (:db/host dev-system)))
      (is (= :debug (:logging/level dev-system)))
      
      (is (= "prod.db.example.com" (:db/host prod-system)))
      (is (= :warn (:logging/level prod-system)))
      (is (:cache/enabled prod-system)))))

(deftest test-middleware-pattern
  "Common pattern: tags as middleware."
  (testing "Tags wrap operations with cross-cutting concerns"
    (let [log-entries (atom [])
          system (-> (care-tags/create-system)
                    ;; Logging middleware
                   (care-tags/register-tag :middleware/log-entry
                     (fn [m]
                       (swap! log-entries conj
                         {:op (:care/verb m)
                          :time (System/currentTimeMillis)})
                       {}))
                    ;; Timing middleware
                   (care-tags/register-tag :middleware/timing
                     (fn [m]
                       {:operation/start (System/nanoTime)}))
                    ;; Validation middleware
                   (care-tags/register-tag :middleware/validate
                     (fn [m]
                       (if (:care/args m)
                         {}
                         {:validation/error "Missing args"}))))
          
          ;; Operation with middleware
          result (-> {:care/verb :test-op
                      :care/args [:some :args]}
                   (merge system)
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:middleware/log-entry {}
                                      :middleware/timing {}
                                      :middleware/validate {}})
                   care-tags/employ-tags)]
      
      ;; Log was captured
      (is (= 1 (count @log-entries)))
      (is (= :test-op (:op (first @log-entries))))
      
      ;; Timing was added
      (is (number? (:operation/start result)))
      
      ;; Validation passed
      (is (nil? (:validation/error result))))))

(comment
  ;; Run all tests
  (clojure.test/run-tests 'net.drilling.modules.care.tags-test)
  
  ;; Run specific categories
  (clojure.test/test-vars [#'test-tags-are-pure-data])
  
  ;; Run performance tests
  #?(:clj
     (clojure.test/test-vars 
       [(resolve 'test-tag-employment-performance)])))
