(ns just-a-map
  (:require [metastructures]
            [orchestra.core #?(:clj :refer :cljs :refer-macros) [defn-spec]]))


(defn-spec add-jam-default :metastructures/map
  "Add using assoc-in to JAM (just a map) with atom support"
  [m :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/jam for unqualified :jam, otherwise use adapter as-is
        db-key (if (= adapter :jam) :example/jam adapter)
        db-value (get m db-key)
        args (:care/args m)]
    (if (instance? #?(:clj clojure.lang.IAtom :cljs cljs.core/Atom) db-value)
      ;; For atoms, use swap! to ensure thread safety
      (let [result (apply swap! db-value assoc-in args)]
        (assoc m db-key db-value)) ;; Keep the atom reference
      ;; For non-atoms, work directly with the value
      (let [result (apply assoc-in db-value args)]
        (assoc m db-key result)))))

(defn-spec add-jam-assoc-in :metastructures/map
  "Add using assoc-in to JAM (just a map) with atom support"
  [m :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/jam for unqualified :jam, otherwise use adapter as-is
        db-key (if (= adapter :jam) :default/jam adapter)
        db-value (get m db-key)
        args (:care/args m)]
    (if (instance? #?(:clj clojure.lang.IAtom :cljs cljs.core/Atom) db-value)
      ;; For atoms, use swap! to ensure thread safety
      (let [result (apply swap! db-value assoc-in args)]
        (assoc m db-key db-value)) ;; Keep the atom reference
      ;; For non-atoms, work directly with the value
      (let [result (apply assoc-in db-value args)]
        (assoc m db-key result)))))


(defn-spec change-jam-default :metastructures/map
  "Change using update-in to JAM (just a map) with atom support"
  [m :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/jam for unqualified :jam, otherwise use adapter as-is
        db-key (if (= adapter :jam) :example/jam adapter)
        db-value (get m db-key)
        args (:care/args m)]
    (if (instance? #?(:clj clojure.lang.IAtom :cljs cljs.core/Atom) db-value)
      ;; For atoms, use swap! to ensure thread safety
      (let [result (apply swap! db-value update-in args)]
        (assoc m db-key db-value)) ;; Keep the atom reference
      ;; For non-atoms, work directly with the value
      (let [result (apply update-in db-value args)]
        (assoc m db-key result)))))

(defn-spec change-jam-update-in :metastructures/map
  "Change using update-in to JAM (just a map) with atom support"
  [m :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/jam for unqualified :jam, otherwise use adapter as-is
        db-key (if (= adapter :jam) :example/jam adapter)
        db-value (get m db-key)
        args (:care/args m)]
    (if (instance? #?(:clj clojure.lang.IAtom :cljs cljs.core/Atom) db-value)
      ;; For atoms, use swap! to ensure thread safety
      (let [result (apply swap! db-value update-in args)]
        (assoc m db-key db-value)) ;; Keep the atom reference
      ;; For non-atoms, work directly with the value
      (let [result (apply update-in db-value args)]
        (assoc m db-key result)))))

(defn-spec employ-jam-default :metastructures/map
  "Employ using get-in from JAM (just a map) with atom support"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/jam for unqualified :jam, otherwise use adapter as-is
        db-key (if (= adapter :jam) :example/jam adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        result (apply get-in safe-db args)
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))

(defn-spec employ-jam-get-in :metastructures/map
  "Employ using get-in from JAM (just a map) with atom support"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/jam for unqualified :jam, otherwise use adapter as-is
        db-key (if (= adapter :jam) :example/jam adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        result (apply get-in safe-db args)
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))


(defn-spec rm-jam-default :metastructures/map
  "Remove using dissoc-in from JAM (just a map) with atom support"
  [m :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/jam for unqualified :jam, otherwise use adapter as-is
        db-key (if (= adapter :jam) :example/jam adapter)
        db-value (get m db-key)
        args (:care/args m)]
    (if (instance? #?(:clj clojure.lang.IAtom :cljs cljs.core/Atom) db-value)
      ;; For atoms, use swap! to ensure thread safety
      (let [result (swap! db-value #(apply medley.core/dissoc-in % args))]
        (-> m
          (assoc db-key db-value) ;; Keep the atom reference
          (assoc :care/result result)))
      ;; For non-atoms, work directly with the value
      (let [result (apply medley.core/dissoc-in db-value args)]
        (-> m
          (assoc db-key result)
          (assoc :care/result result))))))

(defn-spec rm-jam-dissoc-in :metastructures/map
  "Remove using dissoc-in from JAM (just a map) with atom support"
  [m :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/jam for unqualified :jam, otherwise use adapter as-is
        db-key (if (= adapter :jam) :example/jam adapter)
        db-value (get m db-key)
        args (:care/args m)]
    (if (instance? #?(:clj clojure.lang.IAtom :cljs cljs.core/Atom) db-value)
      ;; For atoms, use swap! to ensure thread safety
      (let [result (swap! db-value #(apply medley.core/dissoc-in % args))]
        (assoc m db-key db-value)) ;; Keep the atom reference
      ;; For non-atoms, work directly with the value
      (let [result (apply medley.core/dissoc-in db-value args)]
        (assoc m db-key result)))))
