(ns redis
  (:require [clojure.string]
            [functions]
            [taoensso.carmine :as redis]
            [taoensso.carmine.message-queue :as mq]
            [taoensso.telemere :as t]))

(defn *get [config k]
  (redis/wcar config (redis/get (functions/redisize-key k))))

(defn *set [config k v]
  (redis/wcar config (redis/set (functions/redisize-key k) v)))

(defn *delete [config k]
  (redis/wcar config (redis/del (functions/redisize-key k))))

(defn *keys
  ([config] (*keys config "*"))
  ([config k]
   (redis/wcar config (redis/keys (functions/redisize-key k)))))

(defn *exists? [config k]
  (= 1 (redis/wcar config (redis/exists (functions/redisize-key k)))))

(defn create-worker [conn queue-id on-message]
  (t/log! {:level :info :id :redis-worker/created :queue-id queue-id})
  (mq/worker conn (functions/redisize-key queue-id)
    {:handler
     (fn [{:keys [message]}]
       (try
         (on-message message)
         (t/log! {:level :info :id :redis-worker/sent-message :msg message})
         (catch Throwable e
           (t/log! {:level :error :err e :queue-id queue-id :msg (str "REDIS_WORKER: error - " e)}))))}))

(defn close-worker! [worker]
  (.close worker))

(defn enqueue [conn queue-id message]
  (redis/wcar conn  (mq/enqueue (functions/redisize-key queue-id) message))
  (t/log! {:level :info :id :redis/message-equeued :queue-id queue-id :message message}))

(defmacro create-subscriber [conn publication-id on-message]
  (doall [`(t/log! {:level :info :id :redis/subscriber-created :publication-id ~publication-id})
          `(redis/with-new-pubsub-listener (:spec ~conn)
             {~publication-id ~on-message}
             (redis/subscribe (functions/redisize-key ~publication-id)))]))

(defn publish [conn publication-id message]
  (t/log! {:level :info :id :redis/message-published :publication-id publication-id :message message})
  (redis/wcar conn (redis/publish (functions/redisize-key publication-id) message)))

(defn unsubscribe-subscriber [conn subscriber publication-id]
  (t/log! {:level :info :id :subscriber/unsubscribed :publication publication-id})
  (redis/wcar conn (redis/with-open-listener subscriber
                     (redis/unsubscribe (functions/redisize-key publication-id)))))

#_(comment
    ;; (require '[net.drilling.modules.secrets :as secrets])
    ;; (def conn {:pool (redis/connection-pool {})
    ;;            :spec {:uri (:connection-string (secrets/get-secret :do/insecure-cache))
    ;;                   :ssl-fn :default}})
    
    (def message-queue-id "dev:message-queue")
    (def events-queue-id "dev:event-queue")
    (def message-publication-id "dev:message-publication")
    (def events-publication-id "dev:event-publication")

    (def enqueue-message (partial enqueue conn message-queue-id))

    (defn message-queue-handler [message]
      (let [_ (publish conn message-publication-id message)]
        (println "MQ-handler saw: " message)))

    (def message-queue-worker (create-worker conn
                                             message-queue-id
                                             #'message-queue-handler))

    (defn message-publication-handler [message]
      (println (str "TROUBLE MAN " (last message) "!")))

    (def message-publication-subscriber (create-subscriber conn
                                                           message-publication-id
                                                           #'message-publication-handler))
    (def message-subscriber (fn [f] (create-subscriber conn message-publication-id f)))
    (defn new-handles [message]
      (println (str "Kyrie burns the sage on ... " (-> message last :a))))
    (def message-pub-s2 (message-subscriber #'new-handles))
    (enqueue-message {:a 102})

    (enqueue conn message-queue-id {:b 13})

    ;;
    )
