(ns append-only-log
  (:require [functions]
            [metastructures]
            [orchestra.core #?(:clj :refer :cljs :refer-macros) [defn-spec]]))

(defn-spec add-log-default :metastructures/map
  "Add to LOG (append-only vector) using conj with atom support"
  [m :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :default/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :default/log adapter)
        db-value (get m db-key)
        items (:care/args m)]
    (if (instance? #?(:clj clojure.lang.IAtom :cljs cljs.core/Atom) db-value)
      ;; For atoms, use swap! to ensure thread safety
      (let [result (swap! db-value #(vec (concat % items)))]
        (assoc m db-key db-value)) ;; Keep the atom reference
      ;; For non-atoms, work directly with the value
      (let [;; Ensure we have a vector to maintain order
            current (vec (or db-value []))
            result (vec (concat current items))]
        (assoc m db-key result)))))

(defn-spec add-log-conj :metastructures/map
  "Add to LOG (append-only vector) using conj with atom support"
  [m :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :example/log adapter)
        db-value (get m db-key)
        items (:care/args m)]
    (if (instance? #?(:clj clojure.lang.IAtom :cljs cljs.core/Atom) db-value)
      ;; For atoms, use swap! to ensure thread safety
      (let [result (swap! db-value #(vec (concat % items)))]
        (assoc m db-key db-value)) ;; Keep the atom reference
      ;; For non-atoms, work directly with the value
      (let [;; Ensure we have a vector to maintain order
            current (vec (or db-value []))
            result (vec (concat current items))]
        (assoc m db-key result)))))

(defn-spec employ-log-default :metastructures/map
  "Employ LOG (append-only vector) - returns entire log with atom support"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :example/log adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        result safe-db
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))

(defn-spec employ-log-head :metastructures/map
  "Get first item from LOG with atom support"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :example/log adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        result (first safe-db)
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))

(defn-spec employ-log-tail :metastructures/map
  "Get last item from LOG with atom support"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :example/log adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        result (last safe-db)
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))

(defn-spec employ-log-slice :metastructures/map
  "Get slice of LOG with atom support. Args: [start] or [start end]"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :example/log adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        [start end] args
        result (cond
                 (and start end) (subvec (vec safe-db) start end)
                 start (subvec (vec safe-db) start)
                 :else safe-db)
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))

(defn-spec employ-log-index :metastructures/map
  "Get item at index from LOG with atom support. Args: [index]"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :example/log adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        [index] args
        result (when (and index (< index (count safe-db)))
                 (nth safe-db index))
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))

(defn-spec employ-log-count :metastructures/map
  "Get count of items in LOG with atom support"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :example/log adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        result (count safe-db)
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))

(defn-spec employ-log-filter :metastructures/map
  "Filter LOG items with predicate. Args: [predicate-fn]"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :example/log adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        [pred-fn] args
        result (if pred-fn
                 (filterv pred-fn safe-db)
                 safe-db)
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))

(defn-spec employ-log-reverse :metastructures/map
  "Get LOG items in reverse order with atom support"
  [{:care/keys [args return-with] :as m} :metastructures/map]
  (let [adapter (:care/adapter m)
        ;; Use :example/log for unqualified :log, otherwise use adapter as-is
        db-key (if (= adapter :log) :example/log adapter)
        db-value (get m db-key)
        [is-atom? safe-db] (functions/handle-possible-atom db-value)
        result (reverse safe-db)
        final-result (if (fn? return-with)
                       (return-with result)
                       result)]
    (assoc m :care/result final-result)))
