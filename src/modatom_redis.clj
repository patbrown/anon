(ns modatom-redis
  (:require [functions]
            [modatom]
            [redis]
            [metastructures]
            [orchestra.core :refer [defn-spec]])
  (:import [java.util.concurrent.locks ReentrantLock]))

;; ### COMMIT!
(defn-spec commit! :metastructures/discard
  "Commits contents to a redis key."
  ([connection :metastructures/map id :metastructures/qkw contents :metastructures/any]
   (redis/*set connection (functions/redisize-key id) contents)))

;; ### SNAPSHOT
(defn-spec snapshot :metastructures/discard
  "Returns a snapshot from a file."
  ([connection :metastructures/map id :metastructures/qkw]
   (let [id (functions/redisize-key id)]
     (if (redis/*exists? connection id)
       (redis/*get connection id)
       (commit! connection id nil)))))

(defmethod modatom/commit-with :redis/default [{:keys [id write-with connection] :as this}]
  (commit! connection (functions/redisize-key id) (write-with @this)))

(defmethod modatom/snapshot-with :redis/default [{:keys [id read-with connection]}]
  (read-with (snapshot connection (functions/redisize-key id))))

(def modatom-redis-default-overlay {:variant :redis/default
                                    :write-with identity #_serialize/freeze
                                    :read-with identity #_serialize/thaw})

(defn modatom [config]
  (let [merged-config (merge modatom/modatom-default-base
                        (assoc
                          modatom-redis-default-overlay
                          :lock (ReentrantLock.)
                          :source-atom (atom {}))
                        config)
        {:keys [id source-atom write-with connection]} merged-config
        _ (when-let [ss (redis/*get connection id)] (reset! source-atom (write-with ss)))]
    (modatom/map->Modatom (assoc merged-config :id (functions/redisize-key id)))))

(defn redis-atom
  ([config] (modatom config))
  ([config id] (modatom {:id id
                         :connection config}))
  ([config id value]
   (let [ca (redis-atom config id)
         _ (reset! ca value)]
     ca)))

(comment
  (require '[taoensso.carmine :as car])
  (require '[secrets])
  (def conn {:pool (car/connection-pool {})
             :spec {:uri (:connection-string (secrets/get-secret :do/insecure-cache))
                    :ssl-fn :default}})
  
  ;; (def aaa (cache-atom conn :love/shack {:a 9}))
  ;; (def ->cache (partial cache-atom conn))
  ;; (def bbb (->cache :love/shack))
  
  ;; (redis/wcar conn (redis/keys "*"))
  ;; (defn connect-with [variant]
  ;;   {:pool (redis/connection-pool {})
  ;;    :spec {:uri (:connection-string
  ;;                 (!/get-secret :do/insecure-cache))
  ;;           :ssl-fn :default}})
  
  ;; (def hello (modatom {:id :smoke/test?
  ;;                      :connection conn}))
  ;; (:source-atom hello)
  
  ;; (swap! hello assoc :smoke? true)
  ;; (def gb (modatom {:id :smoke/test?
  ;;                   :connection conn}))
  
  ;; (redis/*keys (connect-with :no))
  
  ;; (swap! hello assoc :a 9)
  
                                        ;
  )
