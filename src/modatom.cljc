(ns modatom
  (:require [clojure.edn]
            [clojure.spec.alpha :as s]
            [clojure.set]
            [clojure.pprint :as pp] ;; Add pprint
            [metastructures]
            [orchestra.core #?(:clj :refer :cljs :refer-macros) [defn-spec]])
  #?(:bb (:import [clojure.lang IAtom IDeref IRef IMeta IObj Atom
                   IPersistentMap PersistentTreeMap
                   IPersistentSet PersistentTreeSet
                   PersistentQueue IPersistentVector]
                  [java.io Writer])
     :clj (:import java.util.concurrent.locks.ReentrantLock)))

;; Fix pprint dispatch ambiguity for Modatom
;; Modatom implements both IDeref and IPersistentMap
#?(:clj (prefer-method pp/simple-dispatch 
                       clojure.lang.IDeref 
                       clojure.lang.IPersistentMap))

(defprotocol IPersist
  (commit [this])
  (snapshot [this]))

(defmulti commit-with #(-> % :variant))
(defmulti snapshot-with #(-> % :variant))
(defmethod commit-with :default [this] (identity this))
(defmethod snapshot-with :default [this] (identity this))


;; replace any with lock spec
#?(:clj (defmacro with-locking
          [lock & body]
          (if (s/valid? :metastructures/any lock)
            `(try
               (.lock ~lock)
               ~@body
               (finally (.unlock ~lock)))
            (do `(locking ~lock
                   ~@body)))))

(defrecord Modatom [connection variant id commit-with snapshot-with read-with write-with lock source-atom watches-atom]
  #?@(:cljs [ISwap
             (-swap! [this f]
               (let [original-value (read-with @source-atom)
                     new-value (write-with (f original-value))
                     result (reset! source-atom new-value)]
                 (commit this)
                 (read-with result)))
             (-swap! [this f a]
               (let [original-value (read-with @source-atom)
                     new-value (write-with (f original-value a))
                     result (reset! source-atom new-value)]
                 (commit this)
                 (read-with result)))
             (-swap! [this f a b]
               (let [original-value (read-with @source-atom)
                     new-value (write-with (f original-value a b))
                     result (reset! source-atom new-value)]
                 (commit this)
                 (read-with result)))
             IReset
             (-reset! [this new-value]
               (let [result (reset! source-atom (write-with new-value))]
                 (commit this)
                 (read-with result)))
             IDeref
             (-deref [_] (read-with (deref source-atom)))
             IWatchable
             (-notify-watches [this oldval newval]
               (doseq [[k f] (let [watch-instances (-> @watches-atom vals vals)]
                               (map (fn [w] [(:modatom-watcher/id w)
                                             (:modatom-watcher/supatom-watcher w)])
                                 watch-instances))]
                 (f k this oldval newval)))
             (-add-watch [this k f]
               (swap! watches-atom assoc k f)
               (add-watch source-atom k
                 (fn [_ _ old-value new-value]
                   (when-not (= old-value new-value)
                     (f k this old-value new-value))))
               this)
             (-remove-watch [this k]
               (swap! watches-atom dissoc k)
               (remove-watch source-atom k) this)
             IPersist
             (commit [this] (commit-with this))
             (snapshot [this] (snapshot-with this))]

      :bb [clojure.lang.IAtom
           (swap [this f]
             (with-locking lock
               (let [original-value (read-with @source-atom)
                     new-value (write-with (f original-value))
                     result (read-with (reset! source-atom new-value))
                     _ (commit this)]
                 result)))
           (swap [this f a]
             (with-locking lock
               (let [original-value (read-with @source-atom)
                     new-value (write-with (f original-value a))
                     result (read-with (reset! source-atom new-value))
                     _ (commit this)]
                 result)))
           (swap [this f a b]
             (with-locking lock
               (let [original-value (read-with @source-atom)
                     new-value (write-with (f original-value a b))
                     result (read-with (reset! source-atom new-value))
                     _ (commit this)]
                 result)))
           (swap [this f a b c]
             (with-locking lock
               (let [original-value (read-with @source-atom)
                     new-value (write-with (f original-value a b c))
                     result (read-with (reset! source-atom new-value))
                     _ (commit this)]
                 result)))
           (reset [this new-value]
             (with-locking lock
               (let [newer-value (write-with new-value)
                     result (read-with (reset! source-atom newer-value))
                     _ (commit this)]
                 result)))
           clojure.lang.IDeref
           (deref [_] (read-with (deref source-atom)))
           IPersist
           (commit [this] (commit-with this))
           (snapshot [this] (snapshot-with this))]

      :clj [clojure.lang.IAtom
            (swap
              [this f]
              (with-locking lock
                (let [original-value (read-with @source-atom)
                      new-value (write-with (f original-value))
                      result (reset! source-atom new-value)]
                  (commit this)
                  (read-with result))))
            (swap [this f a]
              (with-locking lock
                (let [original-value (read-with @source-atom)
                      new-value (write-with (f original-value a))
                      result (reset! source-atom new-value)]
                  (commit this)
                  (read-with result))))
            (swap [this f a b]
              (with-locking lock
                (let [original-value (read-with @source-atom)
                      new-value (write-with (f original-value a b))
                      result (reset! source-atom new-value)]
                  (commit this)
                  (read-with result))))
            (swap [this f a b c]
              (with-locking lock
                (let [original-value (read-with @source-atom)
                      new-value (write-with (f original-value a b c))
                      result (reset! source-atom new-value)]
                  (commit this)
                  (read-with result))))
            (reset [this new-value]
              (with-locking lock
                (let [newer-value (write-with new-value)
                      result (reset! source-atom newer-value)]
                  (commit this)
                  (read-with result))))
            clojure.lang.IDeref
            (deref [_] (read-with (deref source-atom)))
            clojure.lang.IRef
            (getWatches [_]
              @watches-atom)
            (addWatch [this k f]
              (swap! watches-atom assoc k f)
              (add-watch source-atom k
                (fn [_ _ old-value new-value]
                  (when-not (= old-value new-value)
                    (f k this old-value new-value))))
              this)
            (removeWatch [this k]
              (swap! watches-atom dissoc k)
              (remove-watch source-atom k) this)
            IPersist
            (commit [this] (commit-with this))
            (snapshot [this] (snapshot-with this))]))

(deftype Modlens [source-atom meta validate-with watches-atom get-with set-with id]

  #?@(:bb [Object
           (equals [_ other]
             (and (instance? Modlens other)
               (= source-atom (.-source-atom other))))
           clojure.lang.IAtom
           (swap [this f]
             (reset! this (f (deref this))))
           (swap [this f x]
             (reset! this (f (deref this) x)))
           (swap [this f x y]
             (reset! this (f (deref this) x y)))
           (swap [this f x y z]
             (reset! this (apply f (deref this) x y z)))
           (compareAndSet [_ old-value new-value]
             (let [_ (compare-and-set! source-atom old-value new-value)
                   commit? (= old-value new-value)]
               (when commit?
                 (swap! source-atom new-value))))
           (reset [this new-value]
             (if set-with
               (let [validate (.-validate-with this)]
                 (when-not (nil? validate)
                   (assert (validate new-value) "Invalid Value"))
                 (swap! source-atom #(set-with % new-value))
                 new-value)
               (throw (Exception. "READ ONLY ATOM: Reset unavailable."))))
           clojure.lang.IDeref
           (deref [this]
             (get-with @source-atom))]

      :clj [Object
            (equals [_ other]
              (and (instance? Modlens other)
                (= source-atom (.-source-atom other))))
            clojure.lang.IAtom
            (swap [this f]
              (reset! this (f (deref this))))
            (swap [this f x]
              (reset! this (f (deref this) x)))
            (swap [this f x y]
              (reset! this (f (deref this) x y)))
            (swap [this f x y z]
              (reset! this (apply f (deref this) x y z)))
            (compareAndSet [_ old-value new-value]
              (let [_ (compare-and-set! source-atom old-value new-value)
                    commit? (= old-value new-value)]
                (when commit?
                  (swap! source-atom new-value))))
            (reset [this new-value]
              (if set-with
                (let [validate (.-validate-with this)]
                  (when-not (nil? validate)
                    (assert (validate new-value) "Invalid Value"))
                  (swap! source-atom #(set-with % new-value))
                  new-value)
                (throw (Exception. "READ ONLY ATOM: Reset unavailable."))))
            clojure.lang.IDeref
            (deref [_]
              (get-with (deref source-atom)))
            clojure.lang.IRef
            (getWatches [_]
              @watches-atom)
            (addWatch [this k f]
              (swap! watches-atom assoc k f)
              (add-watch source-atom k
                (fn [_ _ old-value new-value]
                  (when-not (= old-value new-value)
                    (f key this old-value new-value))))
              this)
            (removeWatch [this k]
              (swap! watches-atom dissoc k)
              (remove-watch source-atom k)
              this)]

      :cljs [Object
             (equiv [this other] (-equiv this other))
             IDeref
             (-deref [_]
               (get-with (deref source-atom)))
             IMeta
             (-meta [_] meta)
             IWithMeta
             (-with-meta [_ meta]
               (Modlens. source-atom meta validate-with watches-atom get-with set-with id))
             IWatchable
             (-notify-watches [this oldval newval]
               (doseq [[k f] (let [watch-instances (-> @watches-atom vals vals)]
                               (map (fn [{:modlens/keys [id modlens-watcher]}]
                                      [id modlens-watcher])
                                 watch-instances))]
                 (f k this oldval newval)))
             (-add-watch [this k f]
               (swap! watches-atom assoc k f)
               (add-watch source-atom k
                 (fn [_ _ old-value new-value]
                   (when-not (= old-value new-value)
                     (f k this old-value new-value))))
               this)
             (-remove-watch [this k]
               (swap! watches-atom dissoc k)
               (remove-watch source-atom k)
               this)
             IReset
             (-reset! [this new-value]
               (if set-with
                 (let [validate (.-validate-with this)]
                   (when-not (nil? validate)
                     (assert (validate new-value) "Invalid Value"))
                   (swap! source-atom #(set-with % new-value))
                   new-value)
                 (throw (js/Exception. "READ ONLY ATOM: Reset unavailable."))))
             ISwap
             (-swap! [this f] (reset! this (f (deref this))))
             (-swap! [this f x] (reset! this (f (deref this) x)))
             (-swap! [this f x y] (reset! this (f (deref this) x y)))]))

;; Just kept it dumb AF.   
#?(:clj (defmethod print-method Modatom [x w]
          ((get-method print-method clojure.lang.IRecord) x w)))

#?(:clj (defmethod print-method Modlens [x w]
          (.write w (str "#<Modlens@" (Integer/toHexString (System/identityHashCode x)) ": "))
          (.write w (pr-str @x))
          (.write w ">")))

(defn-spec modatom? :metastructures/?
  "Is a thing a modatom?"
  [thing :metastructures/any]
  (instance? Modatom thing))

^:rct/test
(comment
  (* 8 1) ;=> 8
  (* 7 1) ;=> 7
  )
(s/def :metastructures/modatom #(modatom? %))

(def modatom-default-base {:variant :mem/default
                           :backing :mem
                           :commit-with commit-with
                           :snapshot-with snapshot-with
                           :read-with identity
                           :write-with identity})

(defn fresh-lock [] #?(:bb (Object.)
                       :clj (ReentrantLock.)
                       :cljs (js/Object.)))

(defn-spec *modatom :metastructures/fn
  "Takes a config map and returns a function that takes another config map and makes a modatom."
  ([overlay-config :metastructures/map]
   (fn [config]
     (let [{:keys [backing write-with source-atom] :as new-config}
           (merge modatom-default-base
             {:source-atom (atom nil)
              :lock (fresh-lock)
              :watches-atom (atom {})}
             overlay-config
             config)
           _ (when-let [snap (snapshot-with new-config)]
               (reset! source-atom (write-with snap)))
           modatom (map->Modatom new-config)]
       (if (= backing :mem)
         (let [_ (reset! modatom (or (:initial-value config) {}))]
           modatom)
         modatom)))))

(defn-spec modatom-> :metastructures/modatom
  "Simplest modatom creation function."
  [config :metastructures/map] 
  (if (or (:read-with config) (:write-with config) (:initial-value config))
    ;; If it contains modatom config keys, use it as config
    ((*modatom {}) config)
    ;; Otherwise treat it as initial value
    ((*modatom {}) {:initial-value config})))

(defn-spec modlens? :metastructures/?
  "Is a thing a modlens?"
  [thing :metastructures/any]
  (instance? Modlens thing))

(s/def :metastructures/modlens #(modlens? %))

(defn-spec map->Modlens :metastructures/modlens
  "Takes a map and creates a modlens."
  [{:keys [source-atom get-with set-with meta validate-with watches-atom id]} :metastructures/map]
  (Modlens. source-atom meta validate-with watches-atom get-with set-with id))

(def modlens-defaults
  {:get-with (fn [x] x)
   :set-with (fn [a link-value] (merge a link-value))
   :validate-with any?
   :meta {:modlens? true}
   :source-atom (atom {})
   :watches-atom (atom {})})

(defn-spec modlens :metastructures/modlens
  "Simplest modlens creation function"
  ([config :metastructures/map]
   (map->Modlens (merge modlens-defaults config)))
  ([source-atom :metastructures/atom get-with :metastructures/fn]
   (modlens {:source-atom source-atom :get-with get-with}))
  ([source-atom :metastructures/atom get-with :metastructures/fn set-with :metastructures/fn]
   (modlens {:source-atom source-atom :get-with get-with :set-with set-with})))

(def link-> "Alias to create simple modlens functions." modlens)

(defn-spec cursor-> :metastructures/modlens
  "Creates a cursor from the supplied atom using a supplied path."
  ([{:keys [source-atom path]} :metastructures/map] (cursor-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec]
   (if-not (seq path)
     source-atom
     (link-> source-atom
       #(get-in % path)
       #(assoc-in %1 path %2)))))

(defn-spec xform-> :metastructures/modlens
  "Xfroms a source atom to the destination atom using the supplied fn."
  ([{:keys [source-atom xform destination-atom]} :metastructures/map] (xform-> source-atom xform destination-atom))
  ([source-atom :metastructures/atom xform :metastructures/fn dest :metastructures/atom]
   (link-> source-atom
     (fn [x] (reset! (or dest source-atom) (xform x)))
     (fn [x] (xform x)))))

(defn-spec count-> :metastructures/modlens
  "Returns the count in the atom along the supplied path."
  ([{:keys [source-atom path]} :metastructures/map]
   (count-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec]
   (link-> source-atom #(let [v (get-in % (or path []))]
                          (count v)))))

(defn-spec head-> :metastructures/modlens
  "Returns the last `quantity` entries from the collection (most recent when conj-ing to a vector).
   When used after sorting, this gives you the 'top N' items in reverse order (highest first)."
  ([{:keys [source-atom path quantity]} :metastructures/map]
   (head-> source-atom path quantity))
  ([source-atom :metastructures/atom path :metastructures/vec quantity :metastructures/long]
   (link-> source-atom #(vec (reverse (take-last quantity (get-in % (or path []))))))))

(defn-spec tail-> :metastructures/modlens
  "Returns the first `quantity` entries from the collection (oldest when conj-ing to a vector)."
  ([{:keys [source-atom path quantity]} :metastructures/map]
   (tail-> source-atom path quantity))
  ([source-atom :metastructures/atom path :metastructures/vec quantity :metastructures/long]
   (link-> source-atom #(vec (take quantity (get-in % (or path [])))))))

(defn-spec backup-> :metastructures/modlens
  "Create a backup atom."
  ([{:keys [source-atom destination-atom]} :metastructures/map]
   (backup-> source-atom destination-atom))
  ([source-atom :metastructures/atom dest :metastructures/atom]
   (link-> source-atom #(let [v (get-in % [])]
                          (reset! dest v)))))

;; ============================================================================
;; COLLECTION OPERATION LENSES
;; ============================================================================

(defn-spec filter-> :metastructures/modlens
  "Show only items matching predicate."
  ([{:keys [source-atom path pred-fn]} :metastructures/map]
   (filter-> source-atom path pred-fn))
  ([source-atom :metastructures/atom path :metastructures/vec pred-fn :metastructures/fn]
   (link-> source-atom #(filter pred-fn (get-in % path)))))

(defn-spec search-> :metastructures/modlens 
  "Filter items by text search in specified field."
  ([{:keys [source-atom path search-field query]} :metastructures/map]
   (search-> source-atom path search-field query))
  ([source-atom :metastructures/atom path :metastructures/vec search-field :metastructures/kw query :metastructures/str]
   (link-> source-atom 
     #(->> (get-in % path)
        (filter (fn [item] 
                  (when-let [text (get item search-field)]
                    #?(:clj (clojure.string/includes? 
                              (clojure.string/lower-case (str text))
                              (clojure.string/lower-case query))
                       :cljs (.includes 
                               (.toLowerCase (str text))
                               (.toLowerCase query))))))))))

(defn-spec sort-by-> :metastructures/modlens
  "Show collection sorted by key function."
  ([{:keys [source-atom path key-fn]} :metastructures/map]
   (sort-by-> source-atom path key-fn))
  ([source-atom :metastructures/atom path :metastructures/vec key-fn :metastructures/fn-or-kw]
   (link-> source-atom #(sort-by key-fn (get-in % path)))))

(defn-spec distinct-> :metastructures/modlens
  "Show unique items only."
  ([{:keys [source-atom path]} :metastructures/map]
   (distinct-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec] 
   (link-> source-atom #(distinct (get-in % path)))))

(defn-spec reverse-> :metastructures/modlens
  "Show collection in reverse order."
  ([{:keys [source-atom path]} :metastructures/map]
   (reverse-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec]
   (link-> source-atom #(reverse (get-in % path)))))

;; ============================================================================
;; AGGREGATION LENSES
;; ============================================================================

(defn-spec sum-> :metastructures/modlens
  "Sum numeric values at path."
  ([{:keys [source-atom path]} :metastructures/map]
   (sum-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec]
   (link-> source-atom #(reduce + (get-in % path)))))

(defn-spec max-> :metastructures/modlens 
  "Get maximum value."
  ([{:keys [source-atom path]} :metastructures/map]
   (max-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec]
   (link-> source-atom #(when-let [vals (seq (get-in % path))]
                          (apply max vals)))))

(defn-spec min-> :metastructures/modlens 
  "Get minimum value."
  ([{:keys [source-atom path]} :metastructures/map]
   (min-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec]
   (link-> source-atom #(when-let [vals (seq (get-in % path))]
                          (apply min vals)))))

(defn-spec group-by-> :metastructures/modlens
  "Group items by key function."
  ([{:keys [source-atom path key-fn]} :metastructures/map]
   (group-by-> source-atom path key-fn))
  ([source-atom :metastructures/atom path :metastructures/vec key-fn :metastructures/fn-or-kw]
   (link-> source-atom #(group-by key-fn (get-in % path)))))

;; ============================================================================
;; UTILITY LENSES
;; ============================================================================



(defn-spec slice-> :metastructures/modlens
  "Get items from start to end index."
  ([{:keys [source-atom path start end]} :metastructures/map]
   (slice-> source-atom path start end))
  ([source-atom :metastructures/atom path :metastructures/vec start :metastructures/long end :metastructures/long]
   (link-> source-atom 
     #(->> (get-in % path)
        (drop start)
        (take (- end start))))))

(defn-spec default-> :metastructures/modlens
  "Provide default value when nil/empty."
  ([{:keys [source-atom path default-val]} :metastructures/map]
   (default-> source-atom path default-val))
  ([source-atom :metastructures/atom path :metastructures/vec default-val :metastructures/any]
   (link-> source-atom 
     #(let [val (get-in % path)]
        (if (or (nil? val) (and (coll? val) (empty? val)))
          default-val 
          val)))))

(defn-spec exists?-> :metastructures/modlens
  "Check if value exists (non-nil)."
  ([{:keys [source-atom path]} :metastructures/map]
   (exists?-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec]
   (link-> source-atom #(some? (get-in % path)))))

;; ============================================================================
;; REACTIVE/TIME-BASED LENSES
;; ============================================================================

#?(:clj
   (defn-spec debounce-> :metastructures/modlens
     "Delay updates until changes stop for specified ms."
     ([{:keys [source-atom path delay-ms]} :metastructures/map]
      (debounce-> source-atom path delay-ms))
     ([source-atom :metastructures/atom path :metastructures/vec delay-ms :metastructures/long]
      (let [debounced-atom (atom (get-in @source-atom path))
            timer (atom nil)]
        (add-watch source-atom :debouncer
          (fn [_ _ _ new-val]
            (when @timer (future-cancel @timer))
            (reset! timer 
              (future 
                (Thread/sleep delay-ms)
                (reset! debounced-atom (get-in new-val path))))))
        (link-> debounced-atom identity)))))

#?(:clj
   (defn-spec cache-> :metastructures/modlens
     "Cache expensive computations with TTL."
     ([{:keys [source-atom path compute-fn ttl-ms]} :metastructures/map]
      (cache-> source-atom path compute-fn ttl-ms))
     ([source-atom :metastructures/atom path :metastructures/vec compute-fn :metastructures/fn ttl-ms :metastructures/long]
      (let [cache (atom {:value nil :timestamp 0})]
        (link-> source-atom
          (fn [data]
            (let [now (System/currentTimeMillis)
                  cached @cache]
              (if (< (- now (:timestamp cached)) ttl-ms)
                (:value cached)
                (let [new-val (compute-fn (get-in data path))]
                  (reset! cache {:value new-val :timestamp now})
                  new-val)))))))))

;; ============================================================================
;; STATE MANAGEMENT LENSES
;; ============================================================================

(defn-spec toggle-> :metastructures/modlens
  "Boolean toggle with optional initial state."
  ([{:keys [source-atom path initial]} :metastructures/map]
   (toggle-> source-atom path (or initial false)))
  ([source-atom :metastructures/atom path :metastructures/vec] 
   (toggle-> source-atom path false))
  ([source-atom :metastructures/atom path :metastructures/vec initial :metastructures/?]
   (when-not (some? (get-in @source-atom path))
     (swap! source-atom assoc-in path initial))
   (link-> source-atom 
     #(get-in % path)
     (fn [state new-value]
       (if (boolean? new-value)
         (assoc-in state path new-value)
         (assoc-in state path (not (get-in state path))))))))

(defn-spec cycle-> :metastructures/modlens
  "Cycle through a sequence of values."
  ([{:keys [source-atom path values]} :metastructures/map]
   (cycle-> source-atom path values))
  ([source-atom :metastructures/atom path :metastructures/vec values :metastructures/vec]
   (link-> source-atom
     #(get-in % path)
     (fn [data _]
       (let [current (get-in data path)
             current-idx (.indexOf values current)
             next-idx (mod (inc current-idx) (count values))]
         (assoc-in data path (nth values next-idx)))))))

;; ============================================================================
;; DATA TRANSFORMATION LENSES
;; ============================================================================

(defn-spec merge-> :metastructures/modlens
  "Merge data from multiple paths."
  ([{:keys [source-atom paths]} :metastructures/map]
   (merge-> source-atom paths))
  ([source-atom :metastructures/atom paths :metastructures/vec]
   (link-> source-atom
     (fn [data]
       (reduce (fn [acc path]
                 (merge acc (get-in data path)))
         {} paths)))))

(defn-spec join-> :metastructures/modlens
  "SQL-like join between collections."
  ([{:keys [source-atom left-path right-path join-key]} :metastructures/map]
   (join-> source-atom left-path right-path join-key))
  ([source-atom :metastructures/atom left-path :metastructures/vec right-path :metastructures/vec join-key :metastructures/kw]
   (link-> source-atom
     (fn [data]
       (let [left-coll (get-in data left-path)
             right-coll (get-in data right-path)
             right-index (group-by join-key right-coll)]
         (map (fn [left-item]
                (assoc left-item :joined 
                  (get right-index (get left-item join-key))))
           left-coll))))))

(defn-spec normalize-> :metastructures/modlens
  "Normalize nested data into flat ID-based structure."
  ([{:keys [source-atom path id-key]} :metastructures/map]
   (normalize-> source-atom path id-key))
  ([source-atom :metastructures/atom path :metastructures/vec id-key :metastructures/kw]
   (link-> source-atom
     (fn [data]
       (let [items (get-in data path)]
         {:by-id (into {} (map (fn [item] [(get item id-key) item]) items))
          :all-ids (map #(get % id-key) items)})))))

(defn-spec frequency-> :metastructures/modlens
  "Count frequency of values, optionally by key."
  ([{:keys [source-atom path key-fn]} :metastructures/map]
   (frequency-> source-atom path (or key-fn identity)))
  ([source-atom :metastructures/atom path :metastructures/vec] 
   (frequency-> source-atom path identity))
  ([source-atom :metastructures/atom path :metastructures/vec key-fn :metastructures/fn-or-kw]
   (link-> source-atom
     #(frequencies (map key-fn (get-in % path))))))

(defn-spec diff-> :metastructures/modlens
  "Track what changed between updates."
  ([{:keys [source-atom path]} :metastructures/map]
   (diff-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec]
   (let [previous (atom nil)]
     (link-> source-atom
       (fn [data]
         (let [current (get-in data path)
               prev @previous
               changes (when prev
                         {:added (clojure.set/difference (set current) (set prev))
                          :removed (clojure.set/difference (set prev) (set current))
                          :unchanged (clojure.set/intersection (set current) (set prev))})]
           (reset! previous current)
           {:current current :changes changes}))))))

(defn-spec dirty-> :metastructures/modlens
  "Track if data has changed from original."
  ([{:keys [source-atom path]} :metastructures/map]
   (dirty-> source-atom path))
  ([source-atom :metastructures/atom path :metastructures/vec]
   (let [original (atom (get-in @source-atom path))]
     (link-> source-atom
       (fn [data]
         (let [current (get-in data path)]
           {:current current
            :original @original 
            :dirty? (not= current @original)
            :reset! #(reset! original current)}))))))
