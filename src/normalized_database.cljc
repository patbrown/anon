(ns normalized-database
  (:require   
   [clojure.spec.alpha :as s]
   [clojure.walk]
   [functions]
   [medley.core]
   [metastructures]
   [orchestra.core #?(:clj :refer :cljs :refer-macros) [defn-spec]]))

(defn-spec employ-at-path :metastructures/any
  "Get value at path from store."
  [store :metastructures/any path :metastructures/vec]
  (get-in store (functions/follow-path store path)))

(defn-spec employ-follow-path :metastructures/any
  "Get value at path and follow ident references, handling many cardinality."
  [store :metastructures/any path :metastructures/vec]
  (let [followed-paths (functions/follow-path store path)]
    (if (and (sequential? followed-paths) (vector? (first followed-paths)))
      ;; Multiple paths - get value from each (lazy)
      (map #(get-in store %) followed-paths)
      ;; Single path - get value
      (get-in store followed-paths))))

(defn-spec employ-ndb :metastructures/any
  "Employ value using default strategy."
  [m :metastructures/map]
  (let [ndb-entry (medley.core/filter-keys #(= "ndb" (name %)) m)
        ndb (-> ndb-entry vals first)
        ;; Handle both :care/path and :care/args formats
        path (or (:care/path m)
               (when-let [args (:care/args m)]
                   ;; Convert :care/args [path] to path vector
                 (when (and (vector? args) (seq args))
                   (first args)))
               [])]
    (if ndb
      (employ-at-path ndb path)
      nil)))
(def <- employ-ndb)

(defn-spec employ-follow :metastructures/any
  "Employ value and follow references."
  [m :metastructures/map]
  (let [ndb-entry (medley.core/filter-keys #(= "ndb" (name %)) m)
        ndb (-> ndb-entry vals first)
        path (or (:care/path m) [])]
    (employ-follow-path ndb path)))

(def <<- employ-follow)

(defn-spec <-default-value :metastructures/any
  "Returns the default position's value for idents and ids."
  [m :metastructures/map id :metastructures/ident-or-qkw]
  (<- m (functions/as-default-value-path id)))

(defn-spec <<-default-value :metastructures/any
  "Returns the default position's value for idents and ids."
  [m :metastructures/map id :metastructures/ident-or-qkw]
  (<<- m (functions/as-default-value-path id)))

(defn-spec *return-as :metastructures/any
  [variant :metastructures/qkw pred :metastructures/fn return-fn :metastructures/fn-or-kw m :metastructures/map thing :metastructures/any]
  (if (pred thing)
    thing
    (return-fn (employ-at-path m thing))))

(defn-spec return-as-thing :metastructures/map
  "If given a path, return the map entry, ortherwise return the map. A variant can be provided first to use a named method of employ."
  ([m :metastructures/map thing :metastructures/map-or-vec] (return-as-thing :default m thing))
  ([variant :metastructures/kw m :metastructures/map thing :metastructures/map-or-vec]
   (*return-as variant map? identity m thing)))

(defn-spec return-as-function :metastructures/fn
  "Returns  either the function given or the function stored at the path. Notice :function/function is "
  ([m :metastructures/map thing :metastructures/fn-or-vec] (return-as-function :default m thing))
  ([variant :metastructures/kw m :metastructures/map thing :metastructures/fn-or-vec]
   (*return-as variant fn? :function/function m thing)))

(defn-spec call-ident-fn :metastructures/any
  "Calls a function located at the ident."
  [m :metastructures/map ident :metastructures/ident-or-qkw]
  (let [f (<-default-value m ident)]
    (f)))

(defn-spec apply-ident-fn :metastructures/any
  "Applies the function located at the ident to the supplied args"
  [m :metastructures/map ident :metastructures/ident-or-qkw args :metastructures/vec]
  (let [f (<-default-value m ident)]
    (apply f args)))

;; Normalization functions - DRY approach
(defn-spec normalize-entity :metastructures/any
  "Normalize an entity by ID field with fallback strategies:
   1. Use supplied :id-field if provided
   2. Look for :instance/id field  
   3. Find any field ending in '/id'
   4. Return unnormalized if no ID found"
  ([entity :metastructures/any] (normalize-entity entity nil))
  ([entity :metastructures/any id-field :metastructures/any]
   ;; Guard: only process maps
   (if-not (map? entity)
     entity ; Return non-maps unchanged
     (cond
       ;; Strategy 1: Use supplied ID field
       (and id-field (get entity id-field))
       (let [id-val (get entity id-field)
             table-kw (keyword (namespace id-field) "id")]
         {table-kw {id-val entity}})

       ;; Strategy 2: Look for :instance/id
       (:instance/id entity)
       {:instance/id {(:instance/id entity) entity}}

       ;; Strategy 3: Find any field ending in '/id'
       :else
       (let [id-fields (filter #(and (keyword? %)
                                     (= "id" (name %)))
                               (keys entity))]
         (if (= 1 (count id-fields))
           (let [id-field (first id-fields)
                 id-val (get entity id-field)
                 table-kw id-field]
             {table-kw {id-val entity}})
           ;; No single ID field found - return unnormalized
           entity))))))

(defn-spec normalize-instances :metastructures/any
  "Normalize a collection of instances into entity tables"
  ([instances :metastructures/any] (normalize-instances instances nil))
  ([instances :metastructures/any id-kw :metastructures/qkw]
   (cond
    ;; Handle vector of entities  
     (vector? instances)
     (reduce (fn [acc entity]
               (let [normalized (normalize-entity entity id-kw)]
                 (if (map? normalized)
                   (medley.core/deep-merge acc normalized)
                   acc)))
             {} instances)

    ;; Handle map of instances (string-key -> entity) vs single entity
     (map? instances)
    ;; Check if this looks like a single entity (has ID fields) vs a collection
     (let [id-fields (filter #(and (keyword? %)
                                   (= "id" (name %)))
                             (keys instances))]
       (if (seq id-fields)
        ;; Has ID fields - treat as single entity
         (normalize-entity instances id-kw)
        ;; No ID fields - treat as map of instances
         (reduce (fn [acc [_k entity]]
                   (let [normalized (normalize-entity entity)]
                     (if (map? normalized)
                       (medley.core/deep-merge acc normalized)
                       acc)))
                 {} instances)))

    ;; Single entity (not a map or vector)
     :else
     (normalize-entity instances))))

(defn-spec add-normalized-specific :metastructures/any
  [store :metastructures/any instances :metastructures/any id-kw :metastructures/qkw]
  (let [normalized (normalize-instances instances id-kw)]
    (medley.core/deep-merge store normalized)))

(defn-spec add-normalized-default :metastructures/any
  [store :metastructures/any instances :metastructures/any]
  (let [normalized (normalize-instances instances)]
    (medley.core/deep-merge store normalized)))

(defn-spec add-all-vts-to-store :metastructures/map
  [store :metastructures/any]
  (let [vts (set (filter #(= "vt" (namespace %)) (keys (s/registry))))
        vt-map (apply merge (map (fn [s] {s {:metastructures/id s :metastructures/vt (s/form s)}}) vts))]
    (assoc store :metastructures/id vt-map)))

(defn-spec add-normalized :metastructures/map
  "Add normalized instances using our custom normalization only."
  ([variant :metastructures/qkw store :metastructures/any instances :metastructures/map-or-vec]
   (add-normalized variant store instances nil))
  ([variant :metastructures/qkw store :metastructures/any instances :metastructures/map-or-vec id-kw :metastructures/qkw]
   (case variant
     :add/all-vts (add-all-vts-to-store store)
     :add/normalized-by (add-normalized-specific variant store instances id-kw)
    ;; All other variants use our custom normalization
     (let [normalized (normalize-instances instances)]
       (medley.core/deep-merge store normalized)))))

(defn-spec add-ndb :metastructures/map
  "Add instances using default normalization."
  [m :metastructures/map]
  (let [store (get m (:care/adapter m))
        ;; Handle both :care/instances and :care/args formats
        instances (or (:care/instances m)
                    (when-let [args (:care/args m)]
                        ;; Convert :care/args [path entity] to entity
                      (when (and (vector? args) (= 2 (count args)))
                        (second args))))
        normalized (normalize-instances instances)]
    (assoc m (:care/adapter m) (medley.core/deep-merge store normalized))))

(defn-spec add-all-vts :metastructures/map
  "Add all VT specs to store."
  [m :metastructures/map]
  (let [ndb-entry (medley.core/filter-keys #(= "ndb" (name %)) m) 
        ndb (-> ndb-entry vals first)]
    (add-normalized :add/all-vts ndb {})))

(defn-spec add-follow :metastructures/map
  "Add instances using follow normalization."
  [m :metastructures/map]
  (let [ndb-entry (medley.core/filter-keys #(= "ndb" (name %)) m)
        ndbkw (-> ndb-entry keys first)
        ndb (-> ndb-entry vals first)]
    (assoc m ndbkw (add-normalized :add/follow ndb (:care/instances m)))))

;; Change


(defn-spec change-with-path :metastructures/map
  "Change value at path using function and optional args."
  [store :metastructures/any path :metastructures/vec function :metastructures/fn-or-vec & args :metastructures/vec]
  (let [resolved-path (functions/follow-path store path)
        resolved-function (if function
                            (return-as-function store function)
                            identity)]
    (if (and args (seq args))
      (apply update-in store resolved-path resolved-function args)
      (update-in store resolved-path resolved-function))))

(defn-spec change-follow-path :metastructures/map
  "Change values at multiple paths using function and optional args, handling many cardinality."
  [store :metastructures/any path :metastructures/vec function :metastructures/fn-or-vec & args :metastructures/vec]
  (let [followed-paths (functions/follow-path store path)
        resolved-function (if function
                            (return-as-function store function)
                            identity)
        paths-list (if (and (sequential? followed-paths) (vector? (first followed-paths)))
                     followed-paths
                     [followed-paths])]
    (reduce (fn [acc-store path-to-change]
              (if (and args (seq args))
                (apply update-in acc-store path-to-change resolved-function args)
                (update-in acc-store path-to-change resolved-function)))
      store
      paths-list)))

(defn-spec change-default :metastructures/map
  "Change value using default strategy."
  [{:care/keys [args] :as m} :metastructures/map]
  (let [store (get m (:care/adapter m))
        path (:care/path m)
        function (:care/function m)]
    (if (and args (seq args))
      (apply change-with-path store path function args)
      (change-with-path store path function))))

(defn-spec change-follow :metastructures/map
  "Change values following references across many cardinality."
  [{:care/keys [args] :as m} :metastructures/map]
  (let [store (get m (:care/adapter m))
        path (:care/path m)
        function (:care/function m)]
    (if (and args (seq args))
      (apply change-follow-path store path function args)
      (change-follow-path store path function))))



(defn-spec change-with-path :metastructures/map
  "Change value at path using function and optional args."
  [store :metastructures/any path :metastructures/vec function :metastructures/fn-or-vec & args :metastructures/vec]
  (let [resolved-path (functions/follow-path store path)
        resolved-function (if function
                            (return-as-function store function)
                            identity)]
    (if (and args (seq args))
      (apply update-in store resolved-path resolved-function args)
      (update-in store resolved-path resolved-function))))

(defn-spec change-follow-path :metastructures/map
  "Change values at multiple paths using function and optional args, handling many cardinality."
  [store :metastructures/any path :metastructures/vec function :metastructures/fn-or-vec & args :metastructures/vec]
  (let [followed-paths (functions/follow-path store path)
        resolved-function (if function
                            (return-as-function store function)
                            identity)
        paths-list (if (and (sequential? followed-paths) (vector? (first followed-paths)))
                     followed-paths
                     [followed-paths])]
    (reduce (fn [acc-store path-to-change]
              (if (and args (seq args))
                (apply update-in acc-store path-to-change resolved-function args)
                (update-in acc-store path-to-change resolved-function)))
      store
      paths-list)))

(defn-spec change-default :metastructures/map
  "Change value using default strategy."
  [{:care/keys [args] :as m} :metastructures/map]
  (let [store (get m (:care/adapter m))
        path (:care/path m)
        function (:care/function m)]
    (if (and args (seq args))
      (apply change-with-path store path function args)
      (change-with-path store path function))))

(defn-spec change-follow :metastructures/map
  "Change values following references across many cardinality."
  [{:care/keys [args] :as m} :metastructures/map]
  (let [store (get m (:care/adapter m))
        path (:care/path m)
        function (:care/function m)]
    (if (and args (seq args))
      (apply change-follow-path store path function args)
      (change-follow-path store path function))))

(defn-spec remove-normalized :metastructures/map
  "Remove path from normalized map using proper dissoc-in that doesn't over-delete."
  [store :metastructures/any path :metastructures/vec]
  (if (empty? path)
    store
    (if (= 1 (count path))
      (dissoc store (first path))
      (let [parent-path (butlast path)
            key-to-remove (last path)]
        (update-in store parent-path dissoc key-to-remove)))))

(defn-spec follow-remove-normalized :metastructures/map
  "Remove path and all referenced entities from normalized map.
   
   This function:
   1. Removes the field at the given path
   2. Finds any idents in the field value 
   3. Removes those entities completely
   
   Based on Fulcro's remove-entity pattern but for field-level cascade deletion."
  [store :metastructures/any path :metastructures/vec]
  (let [value-at-path (get-in store path)
        
        ;; Step 1: Remove the field itself using dissoc-in pattern
        store-without-field (update-in store (butlast path) dissoc (last path))
        
        ;; Step 2: Find idents to remove
        idents-to-remove (cond
                          ;; Single ident: [:user/id "bob"]
                           (and (vector? value-at-path) 
                             (= 2 (count value-at-path))
                             (keyword? (first value-at-path)))
                           [value-at-path]
                           
                          ;; Many idents: [[:user/id "bob"] [:user/id "charlie"]]
                           (and (vector? value-at-path)
                             (every? #(and (vector? %) 
                                        (= 2 (count %))
                                        (keyword? (first %))) value-at-path))
                           value-at-path
                           
                          ;; Not idents - just remove the field
                           :else
                           [])
        
        ;; Step 3: Remove each referenced entity
        final-store (reduce (fn [store ident]
                              (let [[table id] ident]
                                (update-in store [table] dissoc id)))
                      store-without-field
                      idents-to-remove)]
    
    final-store))

(defn-spec rm-ndb :metastructures/map
  "Remove path using default strategy."
  [m :metastructures/map]
  (let [ndb (-> (medley.core/filter-keys #(= "ndb" (name %)) m) vals first)
        path (or (:care/path m) [])]
    (if ndb
      (remove-normalized ndb path)
      {})))

(defn-spec rm-follow :metastructures/map
  "Remove path and follow references using NMU follow-path."
  [m :metastructures/map path :metastructures/vec]
  (let [ndb-entry (medley.core/filter-keys #(= "ndb" (name %)) m)
        ndbkw (-> ndb-entry keys first)
        ndb (-> ndb-entry vals first)]
    (let [resolved-paths (functions/follow-path ndb path)]
      (assoc m ndbkw
        (if (sequential? resolved-paths)
          (reduce remove-normalized ndb resolved-paths)
          (remove-normalized ndb resolved-paths))))))


