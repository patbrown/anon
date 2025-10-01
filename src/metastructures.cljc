(ns metastructures
  (:require [clojure.spec.alpha :as s]
            [clojure.string]
            [com.yetanalytics.squuid]
            [tick.core]
            [orchestra.core #?(:clj :refer :cljs :refer-macros) [defn-spec]]))

;; FUNCTIONS REPEAT, AVOID CYCLICAL

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



;; ### BASE SPECS
(s/def ::? boolean?)
(s/def ::any any?)
(s/def ::bool ::?)
(s/def ::coll coll?)
(s/def ::discard ::any)
(s/def ::double double?)
(s/def ::false false?)
(s/def ::fn fn?)
(s/def ::inst inst?)
(s/def ::int integer?)
(s/def ::kw keyword?)
(s/def ::long ::int)
(s/def ::map map?)
(s/def ::nil nil?)
(s/def ::num (s/or ::long ::long ::double ::double))
(s/def ::qkw (s/and ::kw qualified-keyword?))
(s/def ::seq seq?)
(s/def ::set set?)
(s/def ::str string?)
(s/def ::string ::str)
(s/def ::sym symbol?)
(s/def ::true true?)
(s/def ::vec vector?)
(s/def :db.type/boolean boolean?)
(s/def :db.type/double double?)
(s/def :db.type/instant inst?)
(s/def :db.type/keyword keyword?)
(s/def :db.type/long integer?)
(s/def :db.type/string string?)
;; END BASE SPECS

;; ### OPTIONALITY SPECS
(s/def ::?-or-nil (s/or ::? ::? ::nil ::nil))
(s/def ::atom-kw-or-str (s/or ::atom ::atom ::kw ::kw ::str ::str))
(s/def ::atom-kw-str-or-vec (s/or ::atom ::atom ::kw ::kw ::str ::str ::vec ::vec))
(s/def ::atom-kw-map-or-str (s/or ::atom ::atom ::kw ::kw ::map ::map ::str ::str))
(s/def ::atom-map-or-vec (s/or ::atom ::atom ::map ::map ::vec ::vec))
(s/def ::atom-or-fn (s/or ::atom ::atom ::fn ::fn))
(s/def ::atom-or-map (s/or ::atom ::atom ::map ::map))
(s/def ::atom-map-or-str (s/or ::atom ::atom ::map ::map ::str ::str))
(s/def ::atom-or-vec (s/or ::atom ::atom ::vec ::vec))
(s/def ::coll-or-nil (s/or ::coll ::coll ::nil ::nil))
(s/def ::coll-or-str (s/or ::coll ::coll ::str ::str))
(s/def ::double-or-nil (s/or ::double ::double ::nil ::nil))
(s/def ::fn-kw-or-str (s/or ::fn ::fn ::kw ::kw ::str ::str))
(s/def ::fn-or-kw (s/or ::fn ::fn ::kw ::kw))
(s/def ::fn-or-nil (s/or ::fn ::fn ::nil ::nil))
(s/def ::fn-or-str (s/or ::fn ::fn ::str ::str))
(s/def ::fn-or-vec (s/or ::fn ::fn ::vec ::vec))
(s/def ::fn-qkw-or-str (s/or ::qkw ::qkw ::str ::str ::fn ::fn))
(s/def ::kw-map-or-vec (s/or ::vec ::vec ::map ::map ::kw ::kw))
(s/def ::kw-map-or-str (s/or ::str ::str ::map ::map ::kw ::kw))
(s/def ::kw-map-set-or-vec (s/or ::kw ::kw ::map ::map ::set ::set ::vec ::vec))
(s/def ::kw-map-str-or-vec (s/or ::kw ::kw ::map ::map ::str ::str ::vec ::vec))
(s/def ::kw-nil-or-str (s/or ::kw ::kw ::nil ::nil ::str ::str))
(s/def ::kw-or-map (s/or ::kw ::kw ::map ::map))
(s/def ::kw-or-nil (s/or ::kw ::kw ::nil ::nil))
(s/def ::kw-or-str (s/or ::kw ::kw ::str ::str))
(s/def ::kw-set-or-vec (s/or ::kw ::kw ::set ::set ::vec ::vec))
(s/def ::kw-str-or-vec (s/or ::kw ::kw ::str ::str ::vec ::vec))
(s/def ::long-or-nil (s/or ::long ::long ::nil ::nil))
(s/def ::map-or-nil (s/or ::map ::map ::nil ::nil))
(s/def ::map-or-str (s/or ::map ::map ::str ::str))
(s/def ::map-or-vec (s/or ::vec ::vec ::map ::map))
(s/def ::num-or-nil (s/or ::num ::num ::nil ::nil))
(s/def ::qkw-or-nil (s/or ::qkw ::qkw ::nil ::nil))
(s/def ::qkw-or-str (s/or ::qkw ::qkw ::str ::str))
(s/def ::qkw-or-vec (s/or ::qkw ::qkw ::vec ::vec))
(s/def ::qkw-or-vec-of-qkws (s/or ::qkw ::qkw ::vec-of-qkws ::vec-of-qkws))
(s/def ::qkw-set-or-vec (s/or ::qkw ::qkw ::str ::str ::vec ::vec))
(s/def ::qkw-str-or-vec (s/or ::qkw ::qkw ::str ::str ::vec ::vec))
(s/def ::seq-or-nil (s/or ::seq ::seq ::nil ::nil))
(s/def ::set-or-nil (s/or ::set ::set ::nil ::nil))
(s/def ::set-or-vec (s/or ::set ::set ::vec ::vec))
(s/def ::str-or-nil (s/or ::str ::str ::nil ::nil))
(s/def ::vec-or-nil (s/or ::vec ::vec ::nil ::nil))
;; END OPTIONALITY SPECS

;; ### HOMOGENOUS SPECS
(s/def ::all-colls (s/and ::coll #(every? coll? %)))
(s/def ::all-doubles (s/and ::coll #(every? double? %)))
(s/def ::all-fns (s/and ::coll #(every? fn? %)))
(s/def ::all-kws (s/and ::coll #(every? keyword? %)))
(s/def ::all-longs (s/and ::coll #(every? integer? %)))
(s/def ::all-maps (s/and ::coll #(every? map? %)))
(s/def ::all-nils (s/and ::coll #(every? nil? %)))
(s/def ::all-nums (s/and ::coll #(every? number? %)))
(s/def ::all-qkws (s/and ::coll #(every? qualified-keyword? %)))
(s/def ::all-seqs (s/and ::coll #(every? seq? %)))
(s/def ::all-sets (s/and ::coll #(every? set? %)))
(s/def ::all-strs (s/and ::coll #(every? string? %)))
(s/def ::all-vecs (s/and ::coll #(every? vector? %)))
(s/def ::set-of-kws (s/and ::set ::all-kws))
(s/def ::set-of-qkws (s/and ::set ::all-qkws))
(s/def ::set-of-strs (s/and ::set ::all-strs))
(s/def ::vec-of-kws (s/and ::vec ::all-kws))
(s/def ::vec-of-maps (s/and ::vec ::all-maps))
(s/def ::vec-of-qkws (s/and ::vec ::all-qkws))
(s/def ::vec-of-strs (s/and ::vec ::all-strs))
(s/def ::vec-of-vecs (s/and ::vec ::all-vecs))
;; ### END homogenous

;; ### Start Extended
(s/def ::ukw (s/and ::kw unqualified-keyword?))
(s/def ::ukw-or-nil (s/or ::ukw ::ukw ::nil ::nil))
(s/def ::all-ukws (s/and ::coll #(every? unqualified-keyword? %)))
(s/def ::atom #(atom? %))
(s/def ::atom-or-nil (s/or ::atom ::atom ::nil ::nil))
(s/def ::all-atoms (s/and ::coll #(every? atom? %)))
(s/def ::idkw (s/and ::qkw #(id-qkw? %)))
(s/def ::has-2 #(= 2 (count %)))
(s/def ::ident entity-ident?)
(s/def ::idents entity-idents?)
(s/def ::ident-or-idents entity-ident-or-idents?)
(s/def ::ident-or-qkw (s/or ::ident ::ident ::qkw ::qkw))
(s/def ::ident-or-map (s/or ::ident ::ident ::map ::map))
(s/def ::ident-or-var-path (s/or ::ident ::ident ::var-path ::var-path))
(s/def ::ident-kw-or-str (s/or ::ident ::ident ::kw ::kw ::str ::str))
(s/def ::ident-map-or-qkw (s/or ::ident ::ident ::qkw ::qkw ::map ::map))
(s/def ::ident-idents-or-nil (s/or ::ident ::ident ::idents ::idents ::nil ::nil))
(s/def ::atom-ident-or-map (s/or ::atom ::atom ::ident ::ident ::map ::map))
(s/def ::fn-ident-or-map (s/or ::fn ::fn ::ident ::ident ::map ::map))
(s/def ::fn-or-ident (s/or ::fn ::fn ::ident ::ident))
(s/def ::has-64 (s/and ::coll (fn [thing] (= 64 (count thing)))))
(s/def ::qid (s/and ::str ::has-64 ::str-without-numbers))
(s/def ::valuetype (s/and ::map #(contains-keys? % ::id ::valuetype) #(clojure.string/includes? (namespace (::id %)) "valuetype")))
(s/def ::valuetype-id (s/and ::qkw #(clojure.string/includes? (namespace %) "valuetype")))
(s/def ::reentrant-lock #(type-of? % "java.util.concurrent.locks.ReentrantLock"))
(s/def ::core-async-channel #(type-of? % "clojure.core.async"))
(s/def ::bytes #?(:clj bytes? :cljs any?))
(s/def ::encrypted (map-with? :data :iv))
(s/def ::file #(type-of? % "java.io.File"))
(s/def ::vec-of-files (s/and ::vec (s/coll-of ::file)))
(s/def ::vec-of-2 (s/and ::vec ::has-2))
(s/def ::nm (s/and ::map #(is-normalized? %)))
(s/def ::atom-or-nm (s/or ::atom ::atom ::nm ::nm))
(s/def ::atom-map-or-str (s/or ::atom ::atom ::map ::map ::str ::str))
(s/def ::atom-map-str-or-vec (s/or ::atom ::atom ::map ::map ::str ::str ::vec ::vec))
(s/def ::instant #?(:clj #(type-of? % "java.time.Instant")
                    :cljs ::any))
(s/def ::time-pair (fn [x] (and (vector? x) (integer? (first x)) (keyword? (second x)))))
(s/def ::instant-or-long (s/or ::instant ::instant ::long ::long))
(s/def ::instant-or-num (s/or ::instant ::instant ::num ::num))
(s/def ::java-io-file #(type-of? % "java.io.file"))
(s/def ::permissive-file (s/or ::str ::str ::java-io-file ::java-io-file))
(s/def ::long-or-qkw (s/or ::long ::long ::qkw ::qkw))

;; EXTRA SPECS
(s/def ::encrypted (map-with? :data :iv))
(s/def ::cardinality #{:one :many})
(s/def ::uniqueness #{:identity :value})
(s/def ::valuetype-id (s/and ::qkw #(clojure.string/includes? (namespace %) "valuetype")))
(s/def ::thing-id (s/and ::qkw #(clojure.string/includes? (namespace %) "thing")))
(s/def ::trait-id (s/and ::qkw #(= (namespace %) "trait")))

;; HOMOGENOUS SPECS
(s/def ::all-qkws (s/and ::coll #(every? qualified-keyword? %)))
(s/def ::all-trait-ids (s/and ::coll (fn [x] (every? #(s/valid? ::trait-id %) x))))
(s/def ::vec-of-kws (s/and ::vec ::all-kws))
(s/def ::set-of-trait-ids (s/and ::set ::all-trait-ids))
(s/def ::id-qkw (s/and ::qkw #(= "id" (name %))))
(s/def ::instant tick.core/instant?)
(s/def ::reentrant-lock (partial type-of? "java.util.concurrent.locks.ReentrantLock"))
(s/def ::atom atom?)
(s/def ::ident entity-ident?)
(s/def ::idents entity-idents?)

;; Or NIL SPECS
(s/def ::id-qkw-or-nil (s/or ::id-qkw ::id-qkw ::nil ::nil))

;; ================
;; # end valuetype
;; ================
;; # start attribute
;; ================

(defn- ->attribute
  ([id] (->attribute id ::qkw))
  ([id valuetype] (->attribute id valuetype :one))
  ([id valuetype cardinality] (->attribute id valuetype cardinality {}))
  ([id valuetype cardinality extras]
   (merge {:attribute/id id
           :attribute/valuetype valuetype
           :attribute/cardinality cardinality}
     extras)))

(defn- ->id-attribute [id]
  (->attribute id ::idkw :one {:attribute/uniqueness :identity}))

(def create-attribute ->attribute)
(def create-unique-attribute ->id-attribute)

;; ========
;; # end attribute
;; ========
;; # start datastructure
;; ========

(defn- ->datastructure
  ([id] (->datastructure id [] [] {}))
  ([id req] (->datastructure id req [] {}))
  ([id req opt] (->datastructure id req opt {}))
  ([id req opt & extras]
   (apply merge (flatten [{:datastructure/id (keyword "datastructure" (namespace id))
                           :datastructure/req (into [id] (if (empty? req)
                                                   [(keyword (namespace id) (namespace id))]
                                                   req))}
                          (when-not (empty? opt) {:datastructure/opt opt})
                          extras]))))

(def create-datastructure ->datastructure)
;; ========
;; # end datastructure
;; ========
;; # start instance
;; ========

(defn-spec map-id-kw ::id-qkw-or-nil
  "Takes the id-kw from a map."
  [m ::map]
  (let [ks (keys m)
        id-kw (filter #(= "id" (name %)) ks)]
    (first id-kw)))

(defn-spec map-id ::id-qkw-or-nil
  "Takes the id-kw from a map."
  [m ::map]
  (get m (map-id-kw m)))


(defn- ->instance [m]
  (let [id-kw (map-id-kw m)
        id (map-id m)
        now (tick.core/instant (tick.core/now))]
    (merge {:instance/id (com.yetanalytics.squuid/time->uuid now)
            :instance/created-at now
            :instance/last-updated-at now
            :instance/datastructure (keyword "datastructure" (namespace id-kw))
            :instance/ident [id-kw id]}
           m)))

(def create-instance ->instance)
;; ========
;; # end instance
;; ========
