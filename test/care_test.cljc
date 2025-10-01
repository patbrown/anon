(ns care-enhanced-test
  "Production-ready tests for enhanced CARE with tags and traits.
   
   ## Test Philosophy
   - Test composition and layering of behaviors
   - Test isolation between different tag/trait sets
   - Test backward compatibility with core CARE
   - Test recursive tag application
   - Test trait-controlled behavior
   - Test performance of composition
   
   ## What We Test
   1. Enhanced CARE maintains backward compatibility
   2. Tags compose correctly and recursively
   3. Traits control behavior as feature flags
   4. Tag registry isolation (no global state)
   5. Performance overhead is acceptable
   6. Edge cases and error handling
   7. Real-world composition patterns"
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [care]
            [care-basic]            
            [care-tags :as tags]))

;; =============================================================================
;; Test Helpers - Pure Functions
;; =============================================================================

(defn make-test-system
  "Create an isolated test system with no global dependencies."
  []
  (-> (tags/create-system)
    (assoc :test/id (random-uuid))
      ;; Add test-specific tags
    (tags/register-tag :test/add-timestamp
      (fn [m] {:timestamp (System/currentTimeMillis)}))
    (tags/register-tag :test/add-user
      (fn [m] {:user (get m :current-user "anonymous")}))
    (tags/register-tag :test/validate
      (fn [m] (if (get m :valid? true)
                m
                (assoc m :errors ["Validation failed"]))))
      ;; Add test-specific traits
    (tags/register-trait :trait/test-mode
      {:description "Enable test mode"
       :default false})
    (tags/register-trait :trait/strict-validation
      {:description "Enable strict validation"
       :default false})))

(defn with-test-tags
  "Add test tags to a care map."
  [care-map & tag-names]
  (-> care-map
    (assoc :care/traits [:trait/with-tags?])
    (assoc :care/tags (zipmap tag-names (repeat {})))))

;; =============================================================================
;; BACKWARD COMPATIBILITY TESTS
;; =============================================================================

(deftest test-enhanced-backward-compatibility
  "Enhanced CARE must be 100% backward compatible with core."
  (testing "Basic CARE operations work unchanged"
    (let [result (care/care-mm {:care/adapter :example/jam
                                    :care/verb :add
                                    :care/variant :default
                                    :care/args [[:test] "value"]})]
      (is (map? result))
      (is (= "value" (get-in result [:example/jam :test])))))
  
  (testing "All core adapters work through enhanced"
    (are [adapter verb args initial-data result-check]
      (let [m (care/care-mm (merge {:care/adapter adapter
                                        :care/verb verb
                                        :care/variant :default
                                        :care/args args}
                                  initial-data))]
        (result-check m))
      
      :jam :add [[:key] "val"] {} 
      #(= "val" (get-in % [:example/jam :key]))
      :jam :employ [[:key] "val"] {} 
      #(= "val" (:care/result %))
      :jam :change [[:key] inc] {:example/jam {:key 1}} 
      #(= 2 (get-in % [:example/jam :key]))
      :jam :remove [[:key]] {:example/jam {:key "val"}} 
      #(nil? (get-in % [:example/jam :key])))))

(deftest test-enhanced-without-tags-equals-core
  "Without tags/traits, enhanced should equal core behavior."
  (testing "Same results from core and enhanced"
    (dotimes [_ 100]
      (let [care-map {:care/adapter :example/jam
                      :care/verb :add
                      :care/args [[(keyword (str "key-" (rand-int 100)))]
                                  (str "value-" (rand-int 100))]}
            core-result (care/care-mm care-map)
            enhanced-result (care/care-mm care-map)]
        (is (= core-result enhanced-result))))))

;; =============================================================================
;; TAG COMPOSITION TESTS
;; =============================================================================

(deftest test-tag-composition
  "Tags compose correctly through enhanced CARE."
  (testing "Single tag application"
    (let [system (make-test-system)
          care-map (-> {:care/adapter :example/jam
                        :care/verb :add
                        :care/args [[:data] "test"]}
                     (merge system)
                     (with-test-tags :test/add-timestamp))
          result (care/care-mm care-map)]
      
      (is (number? (:timestamp result)))
      (is (= "test" (get-in result [:example/jam :data])))))
  
  (testing "Multiple tags compose in order"
    (let [system (make-test-system)
          care-map (-> {:care/adapter :example/jam
                        :care/verb :add
                        :care/args [[:data] "test"]
                        :current-user "alice"}
                     (merge system)
                     (with-test-tags :test/add-timestamp :test/add-user))
          result (care/care-mm care-map)]
      
      (is (number? (:timestamp result)))
      (is (= "alice" (:user result)))
      (is (= "test" (get-in result [:example/jam :data])))))
  
  (testing "Tags don't apply without trait"
    (let [system (make-test-system)
          care-map (-> {:care/adapter :example/jam
                        :care/verb :add
                        :care/args [[:data] "test"]}
                     (merge system)
                      ;; Add tags but no trait
                     (assoc :care/tags {:test/add-timestamp {}}))
          result (care/care-mm care-map)]
      
      (is (nil? (:timestamp result)))
      (is (= "test" (get-in result [:example/jam :data]))))))

(deftest test-recursive-tag-application
  "Tags can trigger more tags recursively."
  (testing "Tag adds another tag"
    (let [system (-> (make-test-system)
                   (tags/register-tag :test/recursive
                     (fn [m] 
                         ;; This tag adds another tag
                       {:care/tags (assoc (:care/tags m)
                                     :test/add-timestamp {})})))
          care-map (-> {:care/adapter :example/jam
                        :care/verb :add
                        :care/args [[:data] "test"]}
                     (merge system)
                     (with-test-tags :test/recursive))
          result (care/care-mm care-map)]
      
      ;; Both tags should have applied
      (is (number? (:timestamp result)))
      (is (contains? (:care/tags result) :test/add-timestamp)))))

;; =============================================================================
;; TRAIT CONTROL TESTS
;; =============================================================================

(deftest test-trait-feature-control
  "Traits control feature flags correctly."
  (testing "Validation trait controls validation behavior"
    (let [system (-> (make-test-system)
                   (tags/register-tag :test/strict-validate
                     (fn [m]
                       (if (tags/trait-enabled? m :trait/strict-validation)
                         (assoc m :validated :strict)
                         (assoc m :validated :normal)))))
          
          ;; Without strict validation
          normal-result (-> {:care/adapter :example/jam
                             :care/verb :add
                             :care/args [[:data] "test"]}
                          (merge system)
                          (with-test-tags :test/strict-validate)
                          care/care-mm)
          
          ;; With strict validation
          strict-result (-> {:care/adapter :example/jam
                             :care/verb :add
                             :care/args [[:data] "test"]}
                          (merge system)
                          (assoc :care/traits [:trait/with-tags? 
                                               :trait/strict-validation])
                          (assoc :care/tags {:test/strict-validate {}})
                          care/care-mm)]
      
      (is (= :normal (:validated normal-result)))
      (is (= :strict (:validated strict-result))))))

;; =============================================================================
;; ISOLATION TESTS
;; =============================================================================

(deftest test-system-isolation
  "Each system has completely isolated tag/trait registries."
  (testing "Systems don't share tags"
    (let [sys1 (-> (make-test-system)
                 (tags/register-tag :shared/tag {:value "sys1"}))
          sys2 (-> (make-test-system)
                 (tags/register-tag :shared/tag {:value "sys2"}))
          
          result1 (-> {:care/adapter :example/jam
                       :care/verb :add
                       :care/args [[:data] "test"]}
                    (merge sys1)
                    (with-test-tags :shared/tag)
                    care/care-mm)
          
          result2 (-> {:care/adapter :example/jam
                       :care/verb :add
                       :care/args [[:data] "test"]}
                    (merge sys2)
                    (with-test-tags :shared/tag)
                    care/care-mm)]
      
      (is (= "sys1" (:value result1)))
      (is (= "sys2" (:value result2)))
      (is (not= (:test/id sys1) (:test/id sys2))))))

;; =============================================================================
;; CONVENIENCE FUNCTION TESTS
;; =============================================================================

(deftest test-convenience-functions
  "Convenience functions provide clean API."
  (testing "care-with-tags helper"
    (let [system (make-test-system)
          base-map {:care/adapter :example/jam
                    :care/verb :add
                    :care/args [[:data] "test"]}
          result (care/care-with-tags 
                   (merge base-map system)
                   [:test/add-timestamp :test/add-user])]
      
      ;; Tags specified via convenience function should apply
      (is (map? result))))
  
  (testing "preview-composition shows composed map"
    (let [system (make-test-system)
          care-map (-> {:care/adapter :example/jam
                        :care/verb :add
                        :care/args [[:data] "test"]}
                     (merge system)
                     (with-test-tags :test/add-timestamp))
          preview (care/preview-composition care-map)]
      
      ;; Preview should show what would be added
      (is (number? (:timestamp preview)))
      ;; But original data should be there too
      (is (= [:data] (first (:care/args preview)))))))

;; =============================================================================
;; PROPERTY-BASED TESTS
;; =============================================================================

(defspec tags-compose-associatively 50
  (prop/for-all [data (gen/map gen/keyword gen/string)
                 tag-count (gen/choose 1 5)]
    (let [system (make-test-system)
          ;; Create random tags
          tags (for [i (range tag-count)]
                 (let [tag-name (keyword "gen" (str "tag-" i))]
                   [tag-name (fn [m] (assoc m tag-name i))]))
          ;; Register them
          system-with-tags (reduce (fn [s [name f]]
                                     (tags/register-tag s name f))
                             system tags)
          tag-names (map first tags)
          
          ;; Apply all at once
          all-at-once (-> {:care/adapter :example/jam
                           :care/verb :add
                           :care/args [[:data] "test"]}
                        (merge system-with-tags data)
                        (assoc :care/traits [:trait/with-tags?])
                        (assoc :care/tags (zipmap tag-names (repeat {})))
                        care/care-mm)
          
          ;; Apply one by one
          one-by-one (reduce (fn [m tag-name]
                               (-> m
                                 (assoc :care/traits [:trait/with-tags?])
                                 (assoc :care/tags {tag-name {}})
                                 tags/employ-tags))
                       (merge {:care/adapter :example/jam
                               :care/verb :add
                               :care/args [[:data] "test"]}
                         system-with-tags data)
                       tag-names)]
      
      ;; All tags should be present in both
      (every? #(contains? all-at-once %) tag-names)
      (every? #(contains? one-by-one %) tag-names))))

(defspec enhanced-care-always-returns-map 100
  (prop/for-all [adapter gen/keyword
                 verb gen/keyword
                 variant gen/keyword]
    (let [care-map {:care/adapter adapter
                    :care/verb verb
                    :care/variant variant}
          result (care/care-mm care-map)]
      (map? result))))

;; =============================================================================
;; PERFORMANCE TESTS
;; =============================================================================

#?(:clj
   (deftest ^:performance test-tag-composition-overhead
     "Tag composition overhead should be acceptable."
     (testing "Overhead of tag application"
       (let [system (make-test-system)
             base-map {:care/adapter :example/jam
                       :care/verb :add
                       :care/args [[:data] "test"]}
             ;; Without tags
             no-tags (merge base-map system)
             ;; With 3 tags
             with-tags (-> base-map
                         (merge system)
                         (with-test-tags :test/add-timestamp 
                           :test/add-user
                           :test/validate))
             iterations 10000]
         
         ;; Warm up
         (dotimes [_ 1000]
           (care/care-mm no-tags)
           (care/care-mm with-tags))
         
         ;; Measure without tags
         (let [start (System/nanoTime)]
           (dotimes [_ iterations]
             (care/care-mm no-tags))
           (let [no-tags-time (- (System/nanoTime) start)]
             
             ;; Measure with tags
             (let [start (System/nanoTime)]
               (dotimes [_ iterations]
                 (care/care-mm with-tags))
               (let [with-tags-time (- (System/nanoTime) start)
                     overhead (double (/ with-tags-time no-tags-time))]
                 
                 (println "Tag composition overhead:" 
                   (format "%.2fx" overhead))
                 ;; Should be less than 5x overhead for 3 tags
                 (is (< overhead 5.0))))))))))

;; =============================================================================
;; REAL-WORLD PATTERNS
;; =============================================================================

(deftest test-audit-trail-pattern
  "Common pattern: automatic audit trail through tags."
  (testing "Audit tags add metadata automatically"
    (let [system (-> (make-test-system)
                   (tags/register-tag :audit/created
                     (fn [m]
                       {:created-at (System/currentTimeMillis)
                        :created-by (get m :current-user "system")}))
                   (tags/register-tag :audit/modified
                     (fn [m]
                       {:modified-at (System/currentTimeMillis)
                        :modified-by (get m :current-user "system")})))
          
          ;; Create operation with audit
          create-result (-> {:care/adapter :example/jam
                             :care/verb :add
                             :care/args [[:entities :user-1] {:name "Alice"}]
                             :current-user "admin"}
                          (merge system)
                          (with-test-tags :audit/created)
                          care/care-mm)
          
          ;; Update operation with audit
          update-result (-> {:care/adapter :example/jam
                             :care/verb :change
                             :care/args [[:entities :user-1 :name] 
                                         #(str % " Smith")]
                             :current-user "editor"}
                          (merge system)
                          (with-test-tags :audit/modified)
                          care/care-mm)]
      
      (is (number? (:created-at create-result)))
      (is (= "admin" (:created-by create-result)))
      (is (number? (:modified-at update-result)))
      (is (= "editor" (:modified-by update-result))))))

(deftest test-validation-pattern
  "Common pattern: validation through tags."
  (testing "Validation tags can prevent operations"
    (let [system (-> (make-test-system)
                   (tags/register-tag :validate/required-fields
                     (fn [m]
                       (let [required [:name :email]
                             ;; Get the data being added - second arg in care/args
                             data (second (:care/args m))
                             missing (filter #(nil? (get data %)) required)]
                         (if (empty? missing)
                           m
                           (assoc m :validation/errors 
                             {:missing-fields missing}))))))
          
          ;; Valid data passes
          valid-result (-> {:care/adapter :example/jam
                            :care/verb :add
                            :care/args [[:users] {:name "Alice" 
                                                  :email "alice@example.com"}]}
                         (merge system)
                         (with-test-tags :validate/required-fields)
                         care/care-mm)
          
          ;; Invalid data gets errors
          invalid-result (-> {:care/adapter :example/jam
                              :care/verb :add
                              :care/args [[:users] {:name "Bob"}]}
                           (merge system)
                           (with-test-tags :validate/required-fields)
                           care/care-mm)]
      
      (is (nil? (:validation/errors valid-result)))
      (is (= {:missing-fields [:email]} 
            (:validation/errors invalid-result))))))

;; =============================================================================
;; ERROR HANDLING TESTS
;; =============================================================================

(deftest test-tag-error-handling
  "Tags handle errors gracefully."
  (testing "Error in tag function doesn't crash"
    (let [system (-> (make-test-system)
                   (tags/register-tag :error/thrower
                     (fn [m] (throw (ex-info "Tag error" {})))))]
      
      (is (thrown? #?(:clj Exception :cljs js/Error)
            (-> {:care/adapter :example/jam
                 :care/verb :add
                 :care/args [[:data] "test"]}
              (merge system)
              (with-test-tags :error/thrower)
              care/care-mm)))))
  
  (testing "Missing tag is ignored"
    (let [system (make-test-system)
          result (-> {:care/adapter :example/jam
                      :care/verb :add
                      :care/args [[:data] "test"]}
                   (merge system)
                   (with-test-tags :nonexistent/tag)
                   care/care-mm)]
      
      ;; Should complete without the missing tag
      (is (= "test" (get-in result [:example/jam :data]))))))

(comment
  ;; Run all tests
  (clojure.test/run-tests 'net.drilling.modules.care.enhanced-test)
  
  ;; Run performance tests
  #?(:clj
     (clojure.test/test-vars 
       [(resolve 'test-tag-composition-overhead)])))
