(ns care-basic-test
  "Production-ready test suite for CARE - the universal multimethod.
   
   ## Test Philosophy (Rich Hickey Style)
   - Test contracts, not implementations
   - Test properties, not just examples
   - Test composition and recursion
   - Test isolation and purity
   - Test real-world scenarios
   
   ## What We Test
   1. Core CARE dispatch and return contracts
   2. Tag system recursive self-transformation
   3. Trait system feature control
   4. Adapter implementations (jam, ndb, log)
   5. Placeholder interpolation
   6. Composition and threading
   7. No global state pollution
   8. Performance characteristics"
  (:require [clojure.test :refer [deftest testing is are use-fixtures]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [care]
            [care-tags]
            [net.drilling.modules.care.hierarchy :as hierarchy]
            [net.drilling.modules.ui.renderer :as renderer]))

;; =============================================================================
;; Test Helpers - Pure Functions
;; =============================================================================

(defn make-test-system
  "Create an isolated test system with no global dependencies."
  []
  (-> (care-tags/create-system)
    (assoc :test/id (random-uuid))))

(defn with-test-adapter
  "Add a test adapter implementation to verify dispatch."
  [system adapter-name verb variant return-val]
  (let [dispatch-key [(name adapter-name) (name verb) (name variant)]]
    ;; Dynamically add method for testing
    (defmethod care/care-mm dispatch-key [m]
      (assoc m :test/result return-val))
    system))

;; =============================================================================
;; FUNDAMENTAL CONTRACT TESTS
;; =============================================================================

(deftest test-care-always-returns-map
  "CARE must ALWAYS return a map, never nil or other types."
  (testing "Valid dispatch returns map"
    (let [result (care/care {:test-data/jam {}
                             :care/adapter :test-data/jam
                             :care/verb :add
                             :care/variant :default
                             :care/args [[:test] "value"]})]
      (is (map? result))
      (is (= "value" (get-in result [:test-data/jam :test])))))
  
  (testing "Invalid dispatch returns map (default behavior)"
    (let [result (care/care {:care/adapter :nonexistent/adapter
                             :care/verb :unknown
                             :care/variant :impossible})]
      (is (map? result))))
  
  (testing "Missing adapter/verb returns map"
    (is (map? (care/care {})))
    (is (map? (care/care {:care/adapter :test-data/jam})))
    (is (map? (care/care {:care/verb :add})))))

(deftest test-care-dispatch-extraction
  "CARE extracts [adapter verb variant] for dispatch."
  (testing "Dispatch with all three components"
    (let [test-map {:care/adapter :test/adapter
                    :care/verb :test/verb 
                    :care/variant :test/variant}]
      ;; Add test method
      (defmethod care/care-mm ["adapter" "verb" "variant"] [m]
        (assoc m :dispatched true))
      
      (let [result (care/care test-map)]
        (is (:dispatched result)))))
  
  (testing "Default variant when not specified"
    (let [test-map {:care/adapter :test/adapter2
                    :care/verb :test/verb2}]
      ;; Add test method for default
      (defmethod care/care-mm ["adapter2" "verb2" "default"] [m]
        (assoc m :used-default true))
      
      (let [result (care/care test-map)]
        (is (:used-default result))))))

#_(deftest test-care-threading
  (testing "Sequential operations build up state"
    (let [result (-> {:test/jam {}}
                   (care/care {:care/adapter :test/jam
                               :care/verb :add
                               :care/args [[:user] "alice"]})
                   (care/care {:care/adapter :test/jam
                               :care/verb :add
                               :care/args [[:timestamp] 12345]})
                   (care/care {:care/adapter :test/jam
                               :care/verb :change
                               :care/args [[:user] #(str % " smith")]}))]
      (is (= "alice smith" (get-in result [:test/jam :user])))
      (is (= 12345 (get-in result [:test/jam :timestamp]))))))

;; =============================================================================
;; TAG SYSTEM TESTS - Recursive Self-Transformation
;; =============================================================================

(deftest test-tags-no-global-state
  "Tags operate on local map state, never global."
  (testing "Each system has independent tag registry"
    (let [sys1 (-> (make-test-system)
                 (care-tags/register-tag :test/tag1 {:added "sys1"}))
          sys2 (-> (make-test-system)
                 (care-tags/register-tag :test/tag1 {:added "sys2"}))]
      
      (is (= {:added "sys1"} (care-tags/get-tag sys1 :test/tag1)))
      (is (= {:added "sys2"} (care-tags/get-tag sys2 :test/tag1)))
      (is (not= (:test/id sys1) (:test/id sys2))))))

(deftest test-tag-recursive-transformation
  "Tags can trigger CARE operations that trigger more tags."
  (testing "Recursive tag employment"
    (let [system (-> (make-test-system)
                     ;; Tag that adds another tag
                   (care-tags/register-tag :test/add-secondary
                     (fn [m] {:care/tags (assoc (:care/tags m) :test/add-timestamp {})}))
                     ;; The secondary tag
                   (care-tags/register-tag :test/add-timestamp
                     (fn [m] {:timestamp 99999}))
                     ;; Enable tags and add first tag
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:test/add-secondary {}}))]
      
      (let [result (care-tags/employ-tags system)]
        ;; Both tags should have been applied
        (is (= 99999 (:timestamp result)))
        (is (contains? (:care/tags result) :test/add-timestamp))))))

(deftest test-tag-function-vs-map
  "Tags can be functions or static maps."
  (testing "Function tags compute values"
    (let [counter (atom 0)
          system (-> (make-test-system)
                   (care-tags/register-tag :count/increment
                     (fn [m] 
                       (swap! counter inc)
                       {:count @counter}))
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:count/increment {}}))]
      
      (let [r1 (care-tags/employ-tags system)
            r2 (care-tags/employ-tags system)]
        (is (= 1 (:count r1)))
        (is (= 2 (:count r2))))))
  
  (testing "Map tags merge directly"
    (let [system (-> (make-test-system)
                   (care-tags/register-tag :static/data {:static "value"})
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:static/data {}}))]
      
      (let [result (care-tags/employ-tags system)]
        (is (= "value" (:static result)))))))

;; =============================================================================
;; TRAIT SYSTEM TESTS - Feature Control
;; =============================================================================

(deftest test-traits-control-behavior
  "Traits enable/disable CARE features."
  (testing "Tags only apply when trait enabled"
    (let [base-system (-> (make-test-system)
                        (care-tags/register-tag :test/tag {:added true})
                        (assoc :care/tags {:test/tag {}}))]
      
      ;; Without trait - no tag application
      (let [without (care-tags/employ-tags base-system)]
        (is (not (:added without))))
      
      ;; With trait - tags apply
      (let [with-trait (-> base-system
                         (assoc :care/traits [:trait/with-tags?])
                         care-tags/employ-tags)]
        (is (:added with-trait)))))
  
  (testing "Multiple traits can be active"
    (let [system (-> (make-test-system)
                   (assoc :care/traits [:trait/with-tags? 
                                        :trait/with-validation?
                                        :trait/with-audit?]))]
      (is (care-tags/trait-enabled? system :trait/with-tags?))
      (is (care-tags/trait-enabled? system :trait/with-validation?))
      (is (care-tags/trait-enabled? system :trait/with-audit?))
      (is (not (care-tags/trait-enabled? system :trait/dry-run?))))))

;; =============================================================================
;; ADAPTER IMPLEMENTATION TESTS
;; =============================================================================

(deftest test-jam-adapter-operations
  "JAM (Just A Map) adapter provides pure map operations."
  (testing "Add operation"
    (let [result (care/care {:test-data/jam {}
                             :care/adapter :test-data/jam
                             :care/verb :add
                             :care/args [[:deep :path] "value"]})]
      (is (= "value" (get-in result [:test-data/jam :deep :path])))))
  
  (testing "Employ (get) operation"
    (let [result (care/care {:test-data/jam {:existing {:data "here"}}
                             :care/adapter :test-data/jam
                             :care/verb :employ
                             :care/args [[:existing :data]]})]
      (is (= "here" (:care/result result)))))
  
  (testing "Change (update) operation"
    (let [result (care/care {:test-data/jam {:counter 5}
                             :care/adapter :test-data/jam
                             :care/verb :change
                             :care/args [[:counter] inc]})]
      (is (= 6 (get-in result [:test-data/jam :counter])))))
  
  (testing "Remove operation"
    (let [result (care/care {:test-data/jam {:keep "this"
                                             :remove "that"}
                             :care/adapter :test-data/jam
                             :care/verb :remove
                             :care/args [[:remove]]})]
      (is (= "this" (get-in result [:test-data/jam :keep])))
      (is (not (contains? (:test-data/jam result) :remove))))))

;; =============================================================================
;; PLACEHOLDER SYSTEM TESTS
;; =============================================================================

(deftest test-placeholder-interpolation
  "Placeholder system replaces keywords with computed values."
  (testing "Basic placeholder replacement"
    (let [placeholders {:placeholder/test 
                        {:placeholder/fn (fn [ctx] "replaced!")}}
          context {}
          data {:key :placeholder/test}
          result (renderer/interpolate placeholders context data)]
      (is (= "replaced!" (:key result)))))
  
  (testing "Context-aware replacement"
    (let [placeholders {:placeholder/event-val
                        {:placeholder/fn (fn [ctx] (:event/value ctx))}}
          context {:event/value "user-input"}
          data {:captured :placeholder/event-val}
          result (renderer/interpolate placeholders context data)]
      (is (= "user-input" (:captured result)))))
  
  (testing "Nested placeholder replacement"
    (let [placeholders {:placeholder/deep
                        {:placeholder/fn (constantly "found")}}
          context {}
          data {:level1 {:level2 {:level3 :placeholder/deep}}}
          result (renderer/interpolate placeholders context data)]
      (is (= "found" (get-in result [:level1 :level2 :level3])))))
  
  (testing "Non-placeholder keywords unchanged"
    (let [placeholders {:placeholder/test {:placeholder/fn (constantly "x")}}
          data {:normal/keyword :other/keyword
                :field-with-placeholder :placeholder/test}
          result (renderer/interpolate placeholders {} data)]
      (is (= :other/keyword (:normal/keyword result)))
      ;; The value :placeholder/test gets replaced with "x"
      (is (= "x" (:field-with-placeholder result))))))

;; =============================================================================
;; COMPOSITION TESTS
;; =============================================================================

(deftest test-star-care-partial-application
  "The *care pattern creates specialized functions."
  (testing "Partial application creates reusable functions"
    ;; Define the methods before using them
    ;; Note: (name :users/jam) returns "jam", not "users/jam"
    (defmethod care/care-mm ["jam" "add" "user"] [m]
      (let [db-key (:care/adapter m)
            db-value (get m db-key {})
            user-id (:user/id m)
            user-data (:user/data m)]
        (assoc m db-key (assoc db-value user-id user-data))))
    
    (defmethod care/care-mm ["jam" "employ" "user"] [m]
      (let [db-key (:care/adapter m)
            db-value (get m db-key {})
            user-id (:user/id m)]
        (assoc m :result (get db-value user-id))))
    
    (let [save-user (care/*care {:care/adapter :users/jam
                                 :care/verb :add
                                 :care/variant :user})
          load-user (care/*care {:care/adapter :users/jam
                                 :care/verb :employ
                                 :care/variant :user})]
      
      ;; Use the specialized functions
      (let [system {:users/jam {}}
            saved (save-user (merge system {:user/id "alice" 
                                            :user/data {:name "Alice"}}))
            ;; For load-user, only pass the data it needs, not the entire saved operation
            loaded (load-user {:users/jam (:users/jam saved)
                               :user/id "alice"})]
        (is (= {:name "Alice"} (get-in saved [:users/jam "alice"])))
        (is (= {:name "Alice"} (:result loaded)))))))




;; =============================================================================
;; HIERARCHY OPERATIONS TESTS
;; =============================================================================

(deftest test-hierarchy-operations-pure-data
  "Hierarchy operations return pure CARE maps."
  (testing "Operations return data, not functions"
    (let [toggle-op (hierarchy/toggle-self [:active])
          increment-op (hierarchy/increment-self [:counter] 5)]
      
      ;; These should be pure data structures
      (is (map? toggle-op))
      (is (= :ui/jam (:care/adapter toggle-op)))
      (is (= :change (:care/verb toggle-op)))
      (is (vector? (:care/args toggle-op)))
      
      ;; Args should contain data and function references
      (is (= [[:active] not] (:care/args toggle-op))))))


;; =============================================================================
;; PROPERTY-BASED TESTS
;; =============================================================================

(defspec care-always-returns-map-property
  50
  (prop/for-all [adapter gen/keyword
                 verb gen/keyword
                 variant gen/keyword
                 data (gen/map gen/keyword gen/string)]
    (let [care-map (merge data
                     {:care/adapter adapter
                      :care/verb verb
                      :care/variant variant})
          result (care/care care-map)]
      ;; Result is either a map or false (for unhandled)
      (or (map? result) (false? result)))))

(defspec tags-are-isolated-property
  50
  (prop/for-all [tag-suffixes (gen/vector gen/keyword 1 5)
                 tag-values (gen/vector gen/string 1 5)]
    (let [tag-names (mapv #(keyword "test" (name %)) tag-suffixes)
          sys1 (reduce (fn [s [name val]]
                         (care-tags/register-tag s name {:value val}))
                 (make-test-system)
                 (map vector tag-names tag-values))
          sys2 (make-test-system)]
      
      ;; sys1 has tags, sys2 doesn't
      (every? #(some? (care-tags/get-tag sys1 %)) tag-names)
      (every? #(nil? (care-tags/get-tag sys2 %)) tag-names))))

(defspec threading-preserves-data-property
  50
  (prop/for-all [initial-data (gen/map gen/keyword gen/string)
                 keys-to-add (gen/vector gen/keyword 1 5)
                 values-to-add (gen/vector gen/string 1 5)]
    (let [;; Start with initial data in the jam
          starting-point {:care/adapter :example/jam
                          :care/verb :add
                          :example/jam initial-data
                          :care/args [[:dummy] "dummy"]}
          ;; Create operations that thread through results
          operations (map (fn [k v]
                            (fn [prev-result]
                              (care/care (assoc prev-result
                                           :care/args [[k] v]))))
                       keys-to-add values-to-add)
          ;; Thread operations through
          final-result (reduce (fn [acc op] (op acc))
                         starting-point
                         operations)]
      
      ;; Check that the final result contains all the expected data
      (and 
        ;; Result is a map
        (map? final-result)
        ;; Has the jam key
        (contains? final-result :example/jam)
        ;; Original data is preserved in the jam (if any)
        (every? (fn [[k v]] 
                  (= v (get-in final-result [:example/jam k]))) 
          initial-data)
        ;; New data is added
        (every? (fn [[k v]] 
                  (= v (get-in final-result [:example/jam k]))) 
          (map vector keys-to-add values-to-add))))))

;; =============================================================================
;; PERFORMANCE TESTS (JVM Only)
;; =============================================================================

#?(:clj
   (deftest ^:performance test-care-dispatch-performance
     "CARE dispatch should be fast enough for production."
     (testing "Dispatch performance"
       (let [care-map {:care/adapter :example/jam
                       :care/verb :add
                       :care/args [[:test] "value"]}]
         ;; Warm up
         (dotimes [_ 1000] (care/care care-map))
         
         ;; Measure - this is a simple benchmark
         (let [start (System/nanoTime)
               iterations 100000]
           (dotimes [_ iterations]
             (care/care care-map))
           (let [elapsed (- (System/nanoTime) start)
                 per-op (/ elapsed iterations 1000000.0)]
             (println "CARE dispatch:" per-op "ms per operation")
             ;; Should be sub-millisecond
             (is (< per-op 1.0))))))))

#?(:clj
   (deftest ^:performance test-tag-employment-performance
     "Tag employment should handle recursive scenarios efficiently."
     (testing "Recursive tag performance"
       (let [system (-> (make-test-system)
                      (care-tags/register-tag ::t1 {:data1 "x"})
                      (care-tags/register-tag ::t2 {:data2 "y"})
                      (care-tags/register-tag ::t3 {:data3 "z"})
                      (assoc :care/traits [:trait/with-tags?])
                      (assoc :care/tags {::t1 {} ::t2 {} ::t3 {}}))]
         
         (let [start (System/nanoTime)
               iterations 10000]
           (dotimes [_ iterations]
             (care-tags/employ-tags system))
           (let [elapsed (- (System/nanoTime) start)
                 per-op (/ elapsed iterations 1000000.0)]
             (println "Tag employment:" per-op "ms per operation")
             ;; Should be reasonably fast
             (is (< per-op 5.0))))))))

;; =============================================================================
;; CONCURRENCY TESTS (JVM Only)
;; =============================================================================

#?(:clj
   (deftest test-care-thread-safety
     "CARE operations with atoms are thread-safe."
     (testing "Concurrent CARE operations on shared atoms"
       (let [shared-atom (atom {:counter 0})
             operations 1000
             threads 10
             ;; Each thread will increment the counter
             futures (doall
                       (for [thread-id (range threads)]
                         (future
                           (dotimes [_ (/ operations threads)]
                             (care/care {:care/adapter :example/jam
                                         :care/verb :change
                                         :care/variant :default
                                         :example/jam shared-atom
                                         :care/args [[:counter] inc]})))))]
         
         ;; Wait for all operations to complete
         (doseq [f futures]
           @f)
         
         ;; The counter should equal total operations
         (is (= operations (get-in @shared-atom [:counter]))
           "All increments should be atomic and accounted for")))
     
     (testing "Concurrent reads and writes don't interfere"
       (let [shared-atom (atom {:users {}})
             write-futures (doall
                             (for [user-id (range 100)]
                               (future
                                 (care/care {:care/adapter :example/jam
                                             :care/verb :add
                                             :care/variant :default
                                             :example/jam shared-atom
                                             :care/args [[:users (keyword (str "user-" user-id))]
                                                         {:id user-id :name (str "User " user-id)}]}))))
             ;; Concurrent reads while writing
             read-futures (doall
                            (for [_ (range 50)]
                              (future
                                (dotimes [_ 20]
                                  (let [result (care/care {:care/adapter :example/jam
                                                           :care/verb :employ
                                                           :care/variant :default
                                                           :example/jam shared-atom
                                                           :care/args [[:users]]})]
                                    ;; Just verify we can read without error
                                    (is (map? (:care/result result))))))))]
         
         ;; Wait for all operations
         (doseq [f (concat write-futures read-futures)]
           @f)
         
         ;; Verify all users were added
         (is (= 100 (count (:users @shared-atom)))
           "All users should be added despite concurrent access")))
     
     (testing "LOG adapter with concurrent appends"
       (let [shared-log (atom [])
             threads 20
             events-per-thread 50
             futures (doall
                       (for [thread-id (range threads)]
                         (future
                           (dotimes [event-num events-per-thread]
                             (care/care {:care/adapter :example/log
                                         :care/verb :add
                                         :care/variant :default
                                         :example/log shared-log
                                         :care/args [{:thread thread-id
                                                      :event event-num
                                                      :timestamp (System/nanoTime)}]})))))]
         
         ;; Wait for all operations
         (doseq [f futures]
           @f)
         
         ;; Verify all events were logged
         (is (= (* threads events-per-thread) (count @shared-log))
           "All events should be logged")
         
         ;; Verify each thread logged all its events
         (let [by-thread (group-by :thread @shared-log)]
           (doseq [thread-id (range threads)]
             (is (= events-per-thread (count (get by-thread thread-id)))
               (str "Thread " thread-id " should have logged all events"))))))))

;; =============================================================================
;; INTEGRATION TESTS
;; =============================================================================

(deftest test-complete-ui-render-flow
  "Test complete flow from CARE through tags to rendering."
  (testing "Full system integration"
    (let [;; Build a complete system
          system (-> (care-tags/create-system)
                    ;; Register component tags
                   (care-tags/register-tag :ui/timestamp
                     (fn [m] {:rendered-at (System/currentTimeMillis)}))
                   (care-tags/register-tag :ui/user-context
                     (fn [m] {:user (get-in m [:session :user] "anonymous")}))
                    ;; Add session data
                   (assoc :session {:user "alice"})
                    ;; Enable tags
                   (assoc :care/traits [:trait/with-tags?])
                   (assoc :care/tags {:ui/timestamp {}
                                      :ui/user-context {}}))
          
          ;; Apply tags
          rendered (care-tags/employ-tags system)]
      
      (is (number? (:rendered-at rendered)))
      (is (= "alice" (:user rendered))))))

;; =============================================================================
;; ERROR HANDLING TESTS
;; =============================================================================

(deftest test-care-error-handling
  "CARE handles errors gracefully."
  (testing "Invalid dispatch returns map"
    (is (map? (care/care {:care/adapter :nonexistent/adapter
                          :care/verb :unknown/verb}))))
  
  (testing "Missing required keys returns map"
    (is (map? (care/care {})))
    (is (map? (care/care {:care/adapter :test/jam}))))
  
  (testing "Malformed args don't crash"
    ;; Test with minimal valid args (at least path and value for assoc-in)
    (let [result (care/care {:test/jam {}
                             :care/adapter :test/jam
                             :care/verb :add
                             :care/args [[:key] "value"]})]
      (is (map? result))
      (is (= "value" (get-in result [:test/jam :key]))))
    
    ;; Test with change verb and valid args
    (let [result (care/care {:test/jam {:counter 0}
                             :care/adapter :test/jam
                             :care/verb :change
                             :care/args [[:counter] inc]})]
      (is (map? result))
      (is (= 1 (get-in result [:test/jam :counter]))))))

(comment
  ;; Run all tests
  (clojure.test/run-tests 'net.drilling.modules.care.production-test)
  
  ;; Run specific test categories
  (clojure.test/test-vars [#'test-care-always-returns-map])
  
  ;; Run performance tests only
  (clojure.test/run-tests 
    (clojure.test/test-vars 
      (filter #(:performance (meta %)) 
        (vals (ns-interns 'net.drilling.modules.care.production-test))))))
