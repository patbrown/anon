(ns functions
  (:require [com.yetanalytics.squuid]
            [orchestra.core #?(:clj :refer :cljs :refer-macros) [defn-spec]]
            [exoscale.coax]
            [medley.core]
            [clojure.string]
            [clojure.spec.alpha :as s]
            [clojure.walk]
            [metastructures]
            [tick.core])
  #?(:cljs (:import [goog.async Debouncer])))

(defn-spec create-uuid :metastructures/any []
  (com.yetanalytics.squuid/time->uuid (tick.core/instant (tick.core/now))))

(defn-spec safe-string :metastructures/str [s :metastructures/str]
  (as-> s $
    (clojure.string/replace $ #"\." "_")
    (clojure.string/replace $ #"\!" "_bang")
    (clojure.string/replace $ #"\?" "_qmark")
    (clojure.string/replace $ #"\*" "_star_")))

(defn-spec sqlize-key :metastructures/str
  "Simplifies dealing with composite keys - rather than stitching them by hand
  using `str`, it supports transparently converting a vec of strings/keywords into
  `:` delimited string.
  NOTE: `nil` values will be omitted, but the vec cannot be empty!
  "
  [args :metastructures/kw-str-or-vec]
  (cond
    (string? args) args
    (qualified-keyword? args) (str (safe-string (namespace args)) "__" (safe-string (name args)))
    (keyword? args) (safe-string (name args))
    :else (->> args
               (remove nil?)
               (mapv (fn [segment]
                       (if (string? segment)
                         (safe-string segment)
                         (if (qualified-keyword? segment)
                           (str (safe-string (namespace segment)) "__" (safe-string (name segment)))
                           (name (safe-string segment))))))
               (clojure.string/join "___"))))

;; # exoscale coerce candy
(def coerce exoscale.coax/coerce)
(defn-spec coerce-with :metastructures/any [m :metastructures/map vt :metastructures/qkw thing :metastructures/any]
  (exoscale.coax/coerce vt thing {:idents m}))
(defn-spec close-map-as :metastructures/map [vt :metastructures/qkw m :metastructures/map]
  (exoscale.coax/coerce vt m {:closed true}))

(defn-spec header :metastructures/any
  [req :metastructures/map header-name :metastructures/str]
  (get (:headers req) header-name))

(defn-spec host :metastructures/any
  [req :metastructures/map]
  (header req "host"))

(defn-spec domain-url :metastructures/str
  [req :metastructures/map]
  (let [scheme (name (or (:scheme req) :https))
        port (:server-port req)
        default-port (if (#{:https "https"} scheme) 443 80)
        host-and-maybe-port (if (and port (not= port default-port))
                              (str (host req) ":" port)
                              (host req))]
    (str scheme "://" host-and-maybe-port)))

(defn-spec without-nils :metastructures/any
  "Given a map, return a map removing key-value
  pairs when value is `nil`."
  ([]
   (remove (comp nil? val)))
  ([data :metastructures/any]
   (reduce-kv (fn [data k v]
                (if (nil? v)
                  (dissoc data k)
                  data))
     data
     data)))

(defn-spec editable-collection? :metastructures/bool
  [m :metastructures/any]
  #?(:clj (instance? clojure.lang.IEditableCollection m)
     :cljs (implements? cljs.core/IEditableCollection m)))

(defn-spec without-keys :metastructures/any
  "Return a map without the keys provided
  in the `keys` parameter."
  [data :metastructures/any keys :metastructures/any]
  (if (editable-collection? data)
    (persistent! (reduce dissoc! (transient data) keys))
    (reduce dissoc data keys)))

(defn-spec parse-float :metastructures/any
  "Parse a string to a float."
  [v :metastructures/any]
  (try
    #?(:clj (Float/parseFloat v)
       :cljs (js/parseFloat v))
    (catch #?(:clj Throwable :cljs :default) _
      nil)))

(defn-spec atomic? :metastructures/bool
  "Return true if the value is an atom. Taken from: https://clojureverse.org/t/how-to-write-atom-in-cljc/9478/2"
  [x :metastructures/any]
  #?(:clj (instance? clojure.lang.Atom x)
     :cljs (satisfies? IAtom x)))

(defn-spec deep-merge :metastructures/any
  [& maps :metastructures/any]
  (letfn [(reconcile-keys [val-in-result val-in-latter]
            (if (and (map? val-in-result)
                  (map? val-in-latter))
              (merge-with reconcile-keys val-in-result val-in-latter)
              val-in-latter))
          (reconcile-maps [result latter]
            (merge-with reconcile-keys result latter))]
    (reduce reconcile-maps maps)))

(defn-spec vec->indexed-map :metastructures/map
  "Transforms a vector `v` into a map where keys are 0-based indices
  and values are the corresponding elements from `v`

  Example:
  (vec->indexed-map [:a :b :c])
  => {0 :a, 1 :b, 2 :c}"
  [v :metastructures/vec]
  (zipmap (range (count v)) v))

(defn-spec indexed-map->vec :metastructures/vec
  "Transforms a map `m` with integer keys (0, 1, 2...) into a vector,
  placing values at the index specified by their key. Handles sparse maps
  by inserting nils for missing indices up to the maximum key found.

  Example:
  (indexed-map-to-vec {0 :a, 1 :b, 2 :c})
  => [:a :b :c]

  (indexed-map-to-vec {0 :a, 2 :c, 1 :b}) ;; Order in map doesn't matter
  => [:a :b :c]

  (indexed-map-to-vec {1 :y, 0 :x, 3 :z}) ;; Sparse example
  => [:x :y nil :z]

  (indexed-map-to-vec {})
  => []"
  [m :metastructures/map]
  {:pre [(map? m) (every? integer? (keys m))]} ;; Preconditions
  (if (empty? m)
    [] ;; Handle empty map case explicitly
    (let [size (count (keys m))
          initial-vector (vec (repeat size nil))]
      ;; Use reduce-kv to iterate map and assoc values into the vector
      (vec (remove nil? (reduce-kv assoc initial-vector m))))))

;; dissoc-in is available in net.drilling.modules.care.core
;; Use CARE operations for state management instead of direct utilities

(defn-spec vt-dispatch :metastructures/qkw
  "Takes a thing and returns it's value-type qualified keyword.
   This is a dumber case-based type dispatch."
  [thing :metastructures/any]
  (if (fn? thing)
    :metastructures/vt
    (cond
      (nil? thing) :metastructures/nil
      (vector? thing) :metastructures/vec
      (set? thing) :metastructures/set
      (map? thing) :metastructures/map
      (keyword? thing) :metastructures/kw
      (string? thing) :metastructures/str
      (integer? thing) :metastructures/long
      (double? thing) :metastructures/double
      (boolean? thing) :metastructures/?
      :else :metastructures/any)))

(defn-spec rm-kw-ns :metastructures/any
  "Removes the namespace for a thing or things DWIM style."
  [thing :metastructures/any]
  (case (vt-dispatch thing)
    :metastructures/kw (keyword (name thing))
    :metastructures/map (medley.core/map-keys (comp keyword name) thing)
    :metastructures/set (set (map rm-kw-ns thing))
    :metastructures/vec (mapv rm-kw-ns thing)
    :else thing))

(defn-spec map-id-kw :metastructures/qkw-or-nil
  "Takes the id-kw from a map."
  [m :metastructures/map]
  (let [ks (keys m)
        id-kw (filter #(= "id" (name %)) ks)]
    (first id-kw)))

(defn-spec map-id :metastructures/qkw-or-nil
  "Takes the id-kw from a map."
  [m :metastructures/map]
  (get m (map-id-kw m)))

;; ### Making Keys Safe for Storage

(defn-spec redisize-key :metastructures/str
  "Simplifies dealing with composite keys - rather than stitching them by hand
  using `str`, it supports transparently converting a vec of strings/keywords into
  `:` delimited string.
  NOTE: `nil` values will be omitted, but the vec cannot be empty!
  "
  [args :metastructures/any]
  (cond
    (string? args) args
    (qualified-keyword? args) (str (namespace args) "/" (name args))
    (keyword? args) (name args)
    :else (->> args
            (remove nil?)
            (mapv (fn [segment]
                    (if (string? segment)
                      segment
                      (if (qualified-keyword? segment)
                        (str (namespace segment) "/" (name segment))
                        (name segment)))))
            (clojure.string/join ":"))))

(defn-spec safe-string :metastructures/str 
  [s :metastructures/str]
  (as-> s $
    (clojure.string/replace $ #"\." "_")
    (clojure.string/replace $ #"\-" "_dash_")
    (clojure.string/replace $ #"\!" "_bang_")
    (clojure.string/replace $ #"\?" "_qmark_")
    (clojure.string/replace $ #"\*" "_star_")))

(defn-spec sqlize-key :metastructures/str
  "Simplifies dealing with composite keys - rather than stitching them by hand
  using `str`, it supports transparently converting a vec of strings/keywords into
  `:` delimited string.
  NOTE: `nil` values will be omitted, but the vec cannot be empty!
  "
  [args :metastructures/any]
  (cond
    (string? args) args
    (qualified-keyword? args) (str (safe-string (namespace args)) "__" (safe-string (name args)))
    (keyword? args) (safe-string (name args))
    :else (->> args
            (remove nil?)
            (mapv (fn [segment]
                    (if (string? segment)
                      (safe-string segment)
                      (if (qualified-keyword? segment)
                        (str (safe-string (namespace segment)) "__" (safe-string (name segment)))
                        (name (safe-string segment))))))
            (clojure.string/join "___"))))

(defn-spec ensure-string :metastructures/str 
  [thing :metastructures/any]
  (if-not (string? thing) (str thing) thing))

;; ### JS ONLY Utilities

#?(:cljs
   (doall
     [;; Source: https://martinklepsch.org/posts/simple-debouncing-in-clojurescript.html
      (defn debounce [f interval]
        (let [dbnc (Debouncer. f interval)]
          ;; We use apply here to support functions of various arities
          (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))]))

(defn-spec unqualified-keyword? boolean?
  "Is a thing a keyword with no namespace."
  [thing any?]
  (and (keyword? thing) (not (qualified-keyword? thing))))

(defn-spec type-of? boolean?
  "Does the type of a thing as a string include the string provided?"
  [string string? thing any?]
  (clojure.string/includes? (str (type thing)) string))

(defn-spec atom? boolean?
  "Is a thing an atom?"
  [thing any?]
  #?(:clj (instance? clojure.lang.IDeref thing)
     #_(or (type-of? thing "clojure.lang.Atom")
         (type-of? thing "baby.pat.supatom.Supatom"))
     :cljs (instance? cljs.core/Atom thing)))

(defn-spec all-valid-examples-of? boolean? [things any? vt qualified-keyword?]
  (every? (fn [x] (s/valid? vt x)) things))

(defn-spec contains-keys? boolean?
  "Does a map contain all the provided keys?"
  ([m map? k keyword?]
   (contains? (set (keys m)) k))
  ([m map? k keyword? & ks #(and (coll? %) (every? keyword? %))]
   (every? true? (map #(contains-keys? m %) (flatten [k ks])))))

(defn-spec map-with? boolean?
  "Does a map contain all these keys?"
  [& qkws #(and (coll? %) (every? keyword? %))]
  (s/and map? #(apply contains-keys? (flatten [% qkws]))))

(defn-spec normalizable? boolean?
  "Can the provided map be normalized?"
  [m any?]
  (if-not (map? m)
    false
    ;; Purely functional approach - check if any keys end with '/id'
    (boolean (some #(and (qualified-keyword? %) (= "id" (name %))) 
               (tree-seq coll? seq m)))))

(defn-spec is-normalized? boolean?
  "Is the provided map normalized or empty?"
  [m any?]
  (if-not (or (map? m) (normalizable? m))
    false
    (let [all-keys-are-id-keys (every? #(= "id" %) (map name (keys m)))
          all-vals-are-maps (every? map? (vals m))
          monster-entity-predicate
          (when (every? true? (map map? (vals m)))
            (every? true?
              (vals (apply merge
                      (map #(medley.core/map-kv
                              (fn [k v]
                                (let [id-kw
                                      (ffirst (medley.core/filter-keys
                                                (fn [k] (= "id" (name k))) v))]
                                  [k (if (and (map? v)
                                           (contains?
                                             (set (map name (keys v))) "id")
                                           (= k (get v id-kw)))
                                       true
                                       (set (keys v)))])) %) (vals m))))))]
      (or (empty? m)
        (every? true? [monster-entity-predicate all-keys-are-id-keys all-vals-are-maps])))))

(defn-spec entity-ident? boolean?
  "Is a thing an entity id?"
  [thing any?]
  (and (vector? thing)
    (= 2 (count thing))
    (keyword? (first thing))
    (= "id" (name (first thing)))))

(defn-spec entity-idents? boolean?
  "Is this thing a vector of entity ids?"
  [thing any?]
  (and (vector? thing)
    (every? entity-ident? thing)))

(defn-spec entity-ident-or-idents? boolean?
  "Is this thing an ident or group of them?"
  [thing any?]
  (or (entity-ident? thing) (entity-idents? thing)))

(defn-spec id-qkw? boolean?
  "Is a thing a qualified id keyword?"
  [thing keyword?]
  (= "id" (name thing)))

(def normalizable? normalizable?)
(def is-normalized? is-normalized?)

(defn-spec follow-path :metastructures/any
  "Enhanced follow-path that handles many cardinality relationships.
   Returns single path vector for single results, or collection of path vectors for many results.
   Based on Fulcro's normalized state traversal but with proper many cardinality support."
  [m :metastructures/map path :metastructures/vec]
  (letfn [(process-path [current-path remaining-path]
            (if (empty? remaining-path)
              [current-path]
              (let [next-key (first remaining-path)
                    rest-keys (rest remaining-path)
                    test-path (conj current-path next-key)
                    value (get-in m test-path)]
                
                (cond
                  ;; Found many idents - expand each with remaining path
                  (and (sequential? value)
                    (every? #(and (vector? %) 
                               (= 2 (count %))
                               (keyword? (first %))) value))
                  (mapcat (fn [ident]
                            (process-path ident rest-keys))
                    value)
                  
                  ;; Found single ident - follow it with remaining path
                  (and (vector? value)
                    (= 2 (count value))
                    (keyword? (first value)))
                  (process-path value rest-keys)
                  
                  ;; Regular value - continue building path
                  :else
                  (process-path test-path rest-keys)))))]
    
    (let [result (process-path [] path)]
      ;; Return single path for backward compatibility, or collection for many
      (if (= 1 (count result))
        (first result)
        result))))

(defn-spec handle-possible-atom :metastructures/vec
  "A helpful function to help with atoms.
   Returns [is-atom? safe-thing]"
  [thing :metastructures/any]
  (if (instance? #?(:clj clojure.lang.IAtom
                    :cljs cljs.core/Atom) thing)
    [true @thing]
    [false thing]))

(defn-spec dispatch-possible-atom :metastructures/discard
  "A wrapper that helps make consistent return values for atoms and maps"
  [is-atom? :metastructures/? m :metastructures/any new-m :metastructures/any]
  (if is-atom?
    (reset! m new-m)
    new-m))

(defn-spec id->id-kw :metastructures/qkw
  "Infers the id-kw for a given qkw using it's namespace."
  [id :metastructures/qkw]
  (keyword (namespace id) "id"))

(defn-spec id->ident :metastructures/ident [id :metastructures/qkw]
  [(id->id-kw id) id])

(defn-spec as-ident-path :metastructures/vec
  "Takes a thing and if it's a keyword treats it as part of a default ident.
   If it's a vector, treats it as a path already."
  [thing :metastructures/qkw-or-vec]
  (if (keyword? thing)
    [(id->id-kw thing) thing]
    thing))

(defn-spec as-default-value-path :metastructures/vec
  [thing :metastructures/qkw-or-vec]
  (let [ident (as-ident-path thing)
        nmsp (-> ident first namespace)]
    (into ident [(keyword nmsp nmsp)])))
