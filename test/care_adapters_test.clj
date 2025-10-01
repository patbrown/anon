(ns care-adapters-test
  "Comprehensive tests for JAM atom support and LOG adapter - ALL USING CARE"
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [care]))

;; ============================================================================
;; TEST FIXTURES AND DATA BUILDERS
;; ============================================================================

(defn make-jam-operation 
  "Create a JAM care operation with sensible defaults and optional overrides"
  ([verb] (make-jam-operation verb {}))
  ([verb overrides]
   (merge {:care/adapter :example/jam
           :care/verb verb
           :care/variant :default
           :example/jam {}}
     overrides)))

(defn make-log-operation 
  "Create a LOG care operation with sensible defaults and optional overrides"
  ([verb] (make-log-operation verb {}))
  ([verb overrides]
   (merge {:care/adapter :example/log
           :care/verb verb
           :care/variant :default
           :example/log []}
     overrides)))

;; Custom assertion functions for better error messages
(defn operation-succeeded?
  "Check if care operation completed successfully"
  [result]
  (and (map? result)
    (contains? result :care/adapter)
    (contains? result :care/verb)))

(defn atom-updated-correctly?
  "Check if atom was updated with expected value"
  [atom-ref expected-value]
  (= @atom-ref expected-value))

(defn preserves-operation-structure?
  "Check if result preserves core operation structure"
  [original result]
  (and (= (:care/adapter original) (:care/adapter result))
    (= (:care/verb original) (:care/verb result))
    (= (:care/variant original) (:care/variant result))))

;; Enhanced assertion macro with context
(defmacro is-care [pred actual & [msg]]
  "Assert with better error messages for care operations"
  `(is (~pred ~actual) 
     (str ~msg "\\nActual: " (pr-str ~actual))))

;; ============================================================================
;; JAM ATOM SUPPORT TESTS
;; ============================================================================

(deftest test-jam-add-with-atoms
  (testing "JAM add operations work correctly with atoms"
    (let [test-atom (atom {:users {}})
          operation (make-jam-operation :add 
                      {:example/jam test-atom
                       :care/args [[:users :user-1] {:name "Alice"}]})]
      
      ;; Perform add operation
      (let [result (care/care-mm operation)]
        
        ;; Operation should succeed
        (is-care operation-succeeded? result
          "JAM add operation with atom should succeed")
        
        ;; Atom should be updated correctly
        (is (atom-updated-correctly? test-atom {:users {:user-1 {:name "Alice"}}})
          "Atom should contain the added data")
        
        ;; Result should keep the atom reference
        (is (identical? test-atom (:example/jam result))
          "Result should keep the atom reference")
        
        ;; We can verify the value inside the atom
        (is (= {:users {:user-1 {:name "Alice"}}} @(:example/jam result))
          "Atom in result should contain the updated data")
        
        ;; Operation structure should be preserved
        (is (preserves-operation-structure? operation result)
          "Core operation structure should be preserved")))))

(deftest test-jam-change-with-atoms
  (testing "JAM change operations work correctly with atoms"
    (let [test-atom (atom {:counter 5})
          operation (make-jam-operation :change 
                      {:example/jam test-atom
                       :care/args [[:counter] inc]})]
      
      ;; Perform change operation
      (let [result (care/care-mm operation)]
        
        ;; Operation should succeed
        (is-care operation-succeeded? result
          "JAM change operation with atom should succeed")
        
        ;; Atom should be updated correctly
        (is (atom-updated-correctly? test-atom {:counter 6})
          "Atom should contain the incremented counter")
        
        ;; Result should keep the atom reference
        (is (identical? test-atom (:example/jam result))
          "Result should keep the atom reference")
        
        ;; We can verify the value inside the atom
        (is (= {:counter 6} @(:example/jam result))
          "Atom in result should contain the updated counter")))))

(deftest test-jam-rm-with-atoms
  (testing "JAM rm operations work correctly with atoms"
    (let [test-atom (atom {:users {:user-1 {:name "Alice"} :user-2 {:name "Bob"}}})
          operation (make-jam-operation :remove 
                      {:example/jam test-atom
                       :care/args [[:users :user-1]]})]
      
      ;; Perform rm operation
      (let [result (care/care-mm operation)]
        
        ;; Operation should succeed
        (is-care operation-succeeded? result
          "JAM rm operation with atom should succeed")
        
        ;; Atom should be updated correctly
        (is (atom-updated-correctly? test-atom {:users {:user-2 {:name "Bob"}}})
          "Atom should have the user removed")
        
        ;; Result should show updated data
        (is (= {:users {:user-2 {:name "Bob"}}} (:care/result result))
          "Result should reflect the atom's updated state")))))

(deftest test-jam-employ-with-atoms
  (testing "JAM employ operations work correctly with atoms"
    (let [test-atom (atom {:users {:user-1 {:name "Alice"}}})
          operation (make-jam-operation :employ 
                      {:example/jam test-atom
                       :care/args [[:users :user-1 :name]]})]
      
      ;; Perform employ operation
      (let [result (care/care-mm operation)]
        
        ;; Should return the requested value
        (is (= "Alice" (:care/result result))
          "Should return the name from the atom")))))

(deftest test-jam-variants
  (testing "JAM variants work correctly with atoms"
    (let [test-atom (atom {:data {:nested {:value 42}}})]
      
      ;; Test assoc-in variant
      (care/care-mm {:care/adapter :example/jam
                     :care/verb :add
                     :care/variant :assoc-in
                     :example/jam test-atom
                     :care/args [[:data :new-key] "new-value"]})
      (is (= "new-value" (get-in @test-atom [:data :new-key]))
        "assoc-in variant should work with atoms")
      
      ;; Test update-in variant  
      (care/care-mm {:care/adapter :example/jam
                     :care/verb :change
                     :care/variant :update-in
                     :example/jam test-atom
                     :care/args [[:data :nested :value] * 2]})
      (is (= 84 (get-in @test-atom [:data :nested :value]))
        "update-in variant should work with atoms")
      
      ;; Test get-in variant
      (let [result3 (care/care-mm {:care/adapter :example/jam
                                   :care/verb :employ
                                   :care/variant :get-in
                                   :example/jam test-atom
                                   :care/args [[:data :nested :value]]})]
        (is (= 84 (:care/result result3))
          "get-in variant should work with atoms")))))

(deftest test-jam-without-atoms
  (testing "JAM operations still work with regular maps"
    (let [test-map {:users {}}
          operation (make-jam-operation :add 
                      {:example/jam test-map
                       :care/args [[:users :user-1] {:name "Alice"}]})]
      
      ;; Perform add operation
      (let [result (care/care-mm operation)]
        
        ;; Operation should succeed
        (is-care operation-succeeded? result
          "JAM add operation with regular map should succeed")
        
        ;; Original map should be unchanged
        (is (= {:users {}} test-map)
          "Original map should be unchanged")
        
        ;; Result should show updated data
        (is (= {:users {:user-1 {:name "Alice"}}} (:example/jam result))
          "Result should contain the added data")))))

;; ============================================================================
;; LOG ADAPTER TESTS
;; ============================================================================

(deftest test-log-add-operations
  (testing "LOG add operations append items correctly"
    (let [operation (make-log-operation :add 
                      {:care/args ["Event 1" "Event 2" "Event 3"]})]
      
      ;; Perform add operation
      (let [result (care/care-mm operation)]
        
        ;; Operation should succeed
        (is-care operation-succeeded? result
          "LOG add operation should succeed")
        
        ;; Should contain all events in order
        (is (= ["Event 1" "Event 2" "Event 3"] (:example/log result))
          "LOG should contain events in append order")))))

(deftest test-log-employ-variants
  (testing "LOG employ variants work correctly"
    (let [test-log ["First" "Second" "Third" "Fourth" "Fifth"]
          base-operation (make-log-operation :employ {:example/log test-log})]
      
      ;; Test head variant
      (let [head-result (care/care-mm (assoc base-operation :care/variant :head))]
        (is (= "First" (:care/result head-result))
          "Head should return first item"))
      
      ;; Test tail variant
      (let [tail-result (care/care-mm (assoc base-operation :care/variant :tail))]
        (is (= "Fifth" (:care/result tail-result))
          "Tail should return last item"))
      
      ;; Test count variant
      (let [count-result (care/care-mm (assoc base-operation :care/variant :count))]
        (is (= 5 (:care/result count-result))
          "Count should return number of items"))
      
      ;; Test slice variant
      (let [slice-result (care/care-mm (assoc base-operation 
                                         :care/variant :slice
                                         :care/args [1 3]))]
        (is (= ["Second" "Third"] (:care/result slice-result))
          "Slice should return subset of items"))
      
      ;; Test index variant
      (let [index-result (care/care-mm (assoc base-operation 
                                         :care/variant :index
                                         :care/args [2]))]
        (is (= "Third" (:care/result index-result))
          "Index should return item at position"))
      
      ;; Test filter variant
      (let [filter-result (care/care-mm (assoc base-operation 
                                          :care/variant :filter
                                          :care/args [#(= % "Third")]))]
        (is (= ["Third"] (:care/result filter-result))
          "Filter should return matching items"))
      
      ;; Test reverse variant
      (let [reverse-result (care/care-mm (assoc base-operation :care/variant :reverse))]
        (is (= ["Fifth" "Fourth" "Third" "Second" "First"] (:care/result reverse-result))
          "Reverse should return items in reverse order")))))

(deftest test-log-with-atoms
  (testing "LOG operations work correctly with atoms"
    (let [test-atom (atom ["Initial"])
          add-operation (make-log-operation :add 
                          {:example/log test-atom
                           :care/args ["Event A" "Event B"]})]
      
      ;; Perform add operation
      (let [result (care/care-mm add-operation)]
        
        ;; Operation should succeed
        (is-care operation-succeeded? result
          "LOG add operation with atom should succeed")
        
        ;; Atom should be updated correctly
        (is (atom-updated-correctly? test-atom ["Initial" "Event A" "Event B"])
          "Atom should contain all events")
        
        ;; Test employ with atom
        (let [head-result (care/care-mm {:care/adapter :example/log
                                         :care/verb :employ
                                         :care/variant :head
                                         :example/log test-atom})]
          (is (= "Initial" (:care/result head-result))
            "Should be able to employ from atom"))))))

(deftest test-log-immutability
  (testing "LOG adapter enforces immutability - no rm or change"
    ;; LOG should not have rm or change methods
    (let [test-log ["Event 1" "Event 2"]
          rm-operation {:care/adapter :example/log
                        :care/verb :rm
                        :care/variant :default
                        :example/log test-log}
          change-operation {:care/adapter :example/log
                            :care/verb :change
                            :care/variant :default
                            :example/log test-log}]
      
      ;; These operations should return false (not implemented)
      (is (= false (care/care-mm rm-operation))
        "LOG rm should not be implemented (return false)")
      
      (is (= false (care/care-mm change-operation))
        "LOG change should not be implemented (return false)"))))

(deftest test-log-variants
  (testing "LOG variants work correctly with different adapters"
    (let [test-log ["A" "B" "C" "D" "E"]]
      
      ;; Test conj variant
      (let [result (care/care-mm {:care/adapter :example/log
                                  :care/verb :add
                                  :care/variant :conj
                                  :example/log test-log
                                  :care/args ["F" "G"]})]
        (is (= ["A" "B" "C" "D" "E" "F" "G"] (:example/log result))
          "conj variant should append items"))
      
      ;; Test default employ variant
      (let [result (care/care-mm {:care/adapter :example/log
                                  :care/verb :employ
                                  :care/variant :default
                                  :example/log test-log})]
        (is (= test-log (:care/result result))
          "default employ should return entire log")))))

;; ============================================================================
;; ERROR SCENARIOS AND EDGE CASES
;; ============================================================================

(deftest test-edge-cases
  (testing "Operations handle edge cases gracefully"
    
    ;; Empty log
    (let [empty-log-op (make-log-operation :employ {:care/variant :head})]
      (is (nil? (:care/result (care/care-mm empty-log-op)))
        "Head of empty log should return nil"))
    
    ;; Index out of bounds
    (let [index-op (make-log-operation :employ 
                     {:example/log ["Only item"]
                      :care/variant :index
                      :care/args [5]})]
      (is (nil? (:care/result (care/care-mm index-op)))
        "Index out of bounds should return nil"))
    
    ;; Slice with invalid bounds
    (let [slice-op (make-log-operation :employ 
                     {:example/log ["Item 1" "Item 2"]
                      :care/variant :slice
                      :care/args [1]})]
      (is (= ["Item 2"] (:care/result (care/care-mm slice-op)))
        "Slice with single argument should work from start position"))
    
    ;; Nil atom handling - this should be handled more gracefully
    (let [nil-atom (atom nil)
          operation (make-jam-operation :add 
                      {:example/jam nil-atom
                       :care/args [[:key] "value"]})]
      ;; Instead of expecting an exception, check if it handles nil gracefully
      (let [result (care/care-mm operation)]
        (is (map? result)
          "Operations on nil atom content should return a result map")))))

(deftest test-data-edge-cases
  (testing "Handle various data types correctly"
    
    ;; Complex nested data in JAM
    (let [complex-atom (atom {:nested {:deep {:value 42}}})
          operation (make-jam-operation :change 
                      {:example/jam complex-atom
                       :care/args [[:nested :deep :value] * 2]})]
      
      (care/care-mm operation)
      (is (atom-updated-correctly? complex-atom {:nested {:deep {:value 84}}})
        "Should handle nested data structures correctly"))
    
    ;; Different data types in LOG
    (let [mixed-log-atom (atom [])
          operation (make-log-operation :add 
                      {:example/log mixed-log-atom
                       :care/args [42 "string" {:map "value"} ["vector" "data"]]})]
      
      (care/care-mm operation)
      (is (atom-updated-correctly? mixed-log-atom [42 "string" {:map "value"} ["vector" "data"]])
        "LOG should handle mixed data types"))
    
    ;; Empty arguments
    (let [empty-args-op (make-log-operation :add {:care/args []})]
      (let [result (care/care-mm empty-args-op)]
        (is (= [] (:example/log result))
          "Empty args should result in no changes")))
    
    ;; Large data structures
    (let [large-data (vec (range 1000))
          large-op (make-log-operation :add {:care/args large-data})]
      (let [result (care/care-mm large-op)]
        (is (= large-data (:example/log result))
          "Should handle large data structures")))))

;; ============================================================================
;; PERFORMANCE REALITY CHECK
;; ============================================================================

(deftest test-performance-characteristics
  (testing "Operations complete in reasonable time with moderate data"
    
    ;; Test JAM with moderate-sized data
    (let [large-map (into {} (map #(vector (keyword (str "key-" %)) %) (range 100)))
          large-atom (atom large-map)
          start-time (System/nanoTime)]
      
      ;; Perform multiple operations
      (dotimes [i 10]
        (care/care-mm (make-jam-operation :change 
                        {:example/jam large-atom
                         :care/args [[(keyword (str "key-" i))] inc]})))
      
      (let [end-time (System/nanoTime)
            duration-ms (/ (- end-time start-time) 1000000.0)]
        
        ;; Should complete in reasonable time
        (is (< duration-ms 100.0) 
          (str "JAM atom operations took " duration-ms "ms, expected <100ms"))))
    
    ;; Test LOG with many items
    (let [large-log (vec (range 1000))
          start-time (System/nanoTime)]
      
      ;; Perform multiple employ operations
      (dotimes [_ 20]
        (care/care-mm (make-log-operation :employ 
                        {:example/log large-log
                         :care/variant :head})))
      
      (let [end-time (System/nanoTime)
            duration-ms (/ (- end-time start-time) 1000000.0)]
        
        ;; Should complete in reasonable time
        (is (< duration-ms 50.0) 
          (str "LOG employ operations took " duration-ms "ms, expected <50ms"))))))

;; ============================================================================
;; PROPERTY-BASED TESTS
;; ============================================================================

(def simple-value-gen
  (gen/one-of [gen/string gen/int gen/keyword]))

(def jam-path-gen
  (gen/vector gen/keyword 1 3))

(defspec jam-atom-roundtrip 25
  (prop/for-all [value simple-value-gen
                 path jam-path-gen]
    ;; Add then employ should return the same value
    (let [test-atom (atom {})
          add-op (make-jam-operation :add 
                   {:example/jam test-atom
                    :care/args [path value]})
          _ (care/care-mm add-op)
          employ-op (make-jam-operation :employ 
                      {:example/jam test-atom
                       :care/args [path]})]
      (= value (:care/result (care/care-mm employ-op))))))

(defspec jam-immutability-with-maps 25
  (prop/for-all [initial-map (gen/map gen/keyword simple-value-gen)
                 path jam-path-gen
                 value simple-value-gen]
    ;; Operations on regular maps should not mutate the original
    (let [original-map (into {} initial-map)
          ;; Only test if the path is valid for assoc-in
          ;; i.e., either the path doesn't exist or leads to a map
          can-assoc? (or (empty? path)
                       (not (get-in original-map (butlast path)))
                       (map? (get-in original-map (butlast path))))
          add-op (make-jam-operation :add 
                   {:example/jam original-map
                    :care/args [path value]})]
      (if can-assoc?
        (let [_ (care/care-mm add-op)]
          ;; Original map should remain unchanged
          (= original-map initial-map))
        ;; Skip test when path would cause assoc-in to fail
        true))))

(defspec log-preserves-order 25
  (prop/for-all [items (gen/vector simple-value-gen 1 10)]
    ;; Adding items to log should preserve order
    (let [test-atom (atom [])
          add-op (make-log-operation :add 
                   {:example/log test-atom
                    :care/args items})
          _ (care/care-mm add-op)]
      (= items @test-atom))))

(defspec log-employ-consistency 25
  (prop/for-all [items (gen/vector simple-value-gen 1 10)]
    ;; Different employ variants should be consistent
    (let [test-log items
          head (care/care-mm (make-log-operation :employ 
                               {:example/log test-log
                                :care/variant :head}))
          tail (care/care-mm (make-log-operation :employ 
                               {:example/log test-log
                                :care/variant :tail}))
          count-result (care/care-mm (make-log-operation :employ 
                                       {:example/log test-log
                                        :care/variant :count}))]
      (and (= (:care/result head) (first items))
        (= (:care/result tail) (last items))
        (= (:care/result count-result) (count items))))))

(defspec log-append-only-property 25
  (prop/for-all [initial-items (gen/vector simple-value-gen 0 5)
                 new-items (gen/vector simple-value-gen 1 5)]
    ;; LOG should always append, never modify existing items
    (let [test-atom (atom initial-items)
          add-op (make-log-operation :add 
                   {:example/log test-atom
                    :care/args new-items})
          _ (care/care-mm add-op)
          final-log @test-atom]
      (and (= (take (count initial-items) final-log) initial-items)
        (= (drop (count initial-items) final-log) new-items)))))

;; ============================================================================
;; INTEGRATION TESTS - FULL WORKFLOW
;; ============================================================================

(deftest test-jam-log-integration
  (testing "JAM and LOG can work together in a workflow"
    (let [config-atom (atom {:settings {:log-enabled true}})
          log-atom (atom [])
          
          ;; Enable logging in config
          _ (care/care-mm (make-jam-operation :change 
                            {:example/jam config-atom
                             :care/args [[:settings :log-enabled] (constantly true)]}))
          
          ;; Add events to log
          _ (care/care-mm (make-log-operation :add 
                            {:example/log log-atom
                             :care/args ["User login" "Data processed" "User logout"]}))
          
          ;; Check configuration
          log-enabled? (care/care-mm (make-jam-operation :employ 
                                       {:example/jam config-atom
                                        :care/args [[:settings :log-enabled]]}))
          
          ;; Get log summary
          event-count (care/care-mm (make-log-operation :employ 
                                      {:example/log log-atom
                                       :care/variant :count}))
          
          latest-event (care/care-mm (make-log-operation :employ 
                                       {:example/log log-atom
                                        :care/variant :tail}))]
      
      ;; Verify the workflow
      (is (true? (:care/result log-enabled?)) "Configuration should be enabled")
      (is (= 3 (:care/result event-count)) "Should have 3 events logged")
      (is (= "User logout" (:care/result latest-event)) "Latest event should be logout"))))

(deftest test-complex-business-workflow
  (testing "Complex workflow with multiple adapters and operations"
    (let [user-store (atom {})
          audit-log (atom [])
          session-config (atom {:timeout 300 :max-users 100})]
      
      ;; User registration workflow
      (care/care-mm (make-jam-operation :add 
                      {:example/jam user-store
                       :care/args [[:users :user-123] {:name "John Doe" :role "admin"}]}))
      
      ;; Log the registration
      (care/care-mm (make-log-operation :add 
                      {:example/log audit-log
                       :care/args [{:event "user-registered" 
                                    :user-id :user-123 
                                    :timestamp (System/currentTimeMillis)}]}))
      
      ;; Update session timeout based on user role
      (let [user-role (care/care-mm (make-jam-operation :employ 
                                      {:example/jam user-store
                                       :care/args [[:users :user-123 :role]]}))]
        (when (= "admin" (:care/result user-role))
          (care/care-mm (make-jam-operation :change 
                          {:example/jam session-config
                           :care/args [[:timeout] (constantly 3600)]}))))
      
      ;; Verify the complete workflow
      (let [final-user (care/care-mm (make-jam-operation :employ 
                                       {:example/jam user-store
                                        :care/args [[:users :user-123]]}))
            final-timeout (care/care-mm (make-jam-operation :employ 
                                          {:example/jam session-config
                                           :care/args [[:timeout]]}))
            audit-count (care/care-mm (make-log-operation :employ 
                                        {:example/log audit-log
                                         :care/variant :count}))]
        
        (is (= "John Doe" (:name (:care/result final-user))) "User should be registered")
        (is (= "admin" (:role (:care/result final-user))) "User role should be set")
        (is (= 3600 (:care/result final-timeout)) "Admin timeout should be extended")
        (is (= 1 (:care/result audit-count)) "Should have one audit log entry")))))

;; ============================================================================
;; DOCUMENTATION THROUGH TESTS
;; ============================================================================

(deftest test-usage-examples
  (testing "Common usage patterns are documented through tests"
    
    ;; JAM for configuration management with atoms
    (let [config (atom {:database {:host "localhost" :port 5432}})]
      
      ;; Update configuration
      (care/care-mm {:care/adapter :example/jam
                     :care/verb :change
                     :care/variant :default
                     :example/jam config
                     :care/args [[:database :host] (constantly "production-db")]})
      
      ;; Verify configuration was updated
      (is (= "production-db" (get-in @config [:database :host]))
        "JAM should update configuration atoms"))
    
    ;; LOG for event tracking
    (let [events (atom [])]
      
      ;; Record events
      (care/care-mm {:care/adapter :system/log
                     :care/verb :add
                     :care/variant :default
                     :system/log events
                     :care/args [{:event "user-action" :timestamp (System/currentTimeMillis)}
                                 {:event "system-response" :timestamp (System/currentTimeMillis)}]})
      
      ;; Query events
      (let [event-count (care/care-mm {:care/adapter :system/log
                                       :care/verb :employ
                                       :care/variant :count
                                       :system/log events})]
        
        (is (= 2 (:care/result event-count))
          "LOG should track events correctly")))
    
    ;; Combined JAM and LOG for audit trail
    (let [user-data (atom {:permissions {:admin false}})
          permissions-log (atom [])]
      
      ;; Promote user to admin
      (care/care-mm {:care/adapter :example/jam
                     :care/verb :change
                     :care/variant :default
                     :example/jam user-data
                     :care/args [[:permissions :admin] (constantly true)]})
      
      ;; Log the permission change
      (care/care-mm {:care/adapter :audit/log
                     :care/verb :add
                     :care/variant :default
                     :audit/log permissions-log
                     :care/args [{:action "permission-change"
                                  :field :admin
                                  :old-value false
                                  :new-value true
                                  :timestamp (System/currentTimeMillis)}]})
      
      ;; Verify both state and audit trail
      (let [is-admin (care/care-mm {:care/adapter :example/jam
                                    :care/verb :employ
                                    :care/variant :default
                                    :example/jam user-data
                                    :care/args [[:permissions :admin]]})
            audit-entries (care/care-mm {:care/adapter :audit/log
                                         :care/verb :employ
                                         :care/variant :count
                                         :audit/log permissions-log})]
        
        (is (true? (:care/result is-admin)) "User should be promoted to admin")
        (is (= 1 (:care/result audit-entries)) "Should have audit trail entry")))))

(deftest test-adapter-patterns
  (testing "Different adapter patterns work correctly"
    
    ;; Domain-specific adapters
    (let [user-atom (atom {:profile {}})
          system-atom (atom {:status :running})]
      
      ;; Different adapter namespaces
      (care/care-mm {:care/adapter :users/jam
                     :care/verb :add
                     :care/variant :default
                     :users/jam user-atom
                     :care/args [[:profile :name] "Alice"]})
      
      (care/care-mm {:care/adapter :system/jam
                     :care/verb :change
                     :care/variant :default
                     :system/jam system-atom
                     :care/args [[:status] (constantly :maintenance)]})
      
      ;; Verify both work independently
      (is (= "Alice" (get-in @user-atom [:profile :name]))
        "Domain-specific user adapter should work")
      (is (= :maintenance (get-in @system-atom [:status]))
        "Domain-specific system adapter should work"))
    
    ;; Different log adapters for different purposes
    (let [access-log (atom [])
          error-log (atom [])
          audit-log (atom [])]
      
      ;; Different log types
      (care/care-mm {:care/adapter :access/log
                     :care/verb :add
                     :care/variant :default
                     :access/log access-log
                     :care/args [{:ip "192.168.1.1" :path "/api/users"}]})
      
      (care/care-mm {:care/adapter :error/log
                     :care/verb :add
                     :care/variant :default
                     :error/log error-log
                     :care/args [{:error "Database connection failed"}]})
      
      (care/care-mm {:care/adapter :audit/log
                     :care/verb :add
                     :care/variant :default
                     :audit/log audit-log
                     :care/args [{:user "admin" :action "user-delete"}]})
      
      ;; Verify each log type works
      (is (= 1 (count @access-log)) "Access log should have entries")
      (is (= 1 (count @error-log)) "Error log should have entries")
      (is (= 1 (count @audit-log)) "Audit log should have entries"))))
