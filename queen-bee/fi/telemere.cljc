(ns workspace.net.drilling.care.adapters.telemere
  "Care adapter for Telemere - PRACTICAL structured logging with RULES
   
   ENFORCED RULES:
   - Every signal MUST have :signal/type and :signal/timestamp
   - Every operation MUST record :signal/duration 
   - Every operation MUST record :signal/outcome (:success/:failure/:timeout)
   - Every pattern detection MUST trigger specific actions"
  (:require [taoensso.telemere :as t]))

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; WRAPPED CARE - The ONLY way care should run in production

(defn emit-signal! 
  "Emit a signal with ENFORCED structure"
  [signal]
  (assert (:signal/type signal) "signal/type is REQUIRED")
  (assert (:signal/timestamp signal) "signal/timestamp is REQUIRED")
  (when (:signal/operation signal) ; If it's an operation
    (assert (:signal/duration signal) "signal/duration is REQUIRED for operations")
    (assert (:signal/outcome signal) "signal/outcome is REQUIRED for operations"))
  (t/signal! {:level (or (:signal/level signal) :info)
              :id (:signal/type signal)
              :data signal}))

(defn traced-care
  "Wrap care operations with automatic tracing and measurement"
  [m]
  (let [trace-id (random-uuid)
        parent-trace (:trace/parent m)
        start (System/nanoTime)
        operation-type (str (get m :care/adapter) "/" 
                         (get m :care/verb) "/" 
                         (get m :care/variant))]
    (try 
      ;; Emit start signal
      (emit-signal! {:signal/type :care/start
                     :signal/timestamp (System/currentTimeMillis)
                     :signal/operation operation-type
                     :trace/id trace-id
                     :trace/parent parent-trace})
      
      ;; Execute operation
      (let [result (care (assoc m :trace/id trace-id))
            duration (/ (- (System/nanoTime) start) 1000000.0)]
        
        ;; Emit completion signal
        (emit-signal! {:signal/type :care/complete
                       :signal/timestamp (System/currentTimeMillis)
                       :signal/operation operation-type
                       :signal/duration duration
                       :signal/outcome :success
                       :trace/id trace-id
                       :trace/parent parent-trace})
        
        ;; Return result with trace metadata
        (assoc result 
          :trace/id trace-id
          :signal/duration duration))
      
      (catch Exception e
        (let [duration (/ (- (System/nanoTime) start) 1000000.0)]
          ;; Emit failure signal
          (emit-signal! {:signal/type :care/failure
                         :signal/timestamp (System/currentTimeMillis)
                         :signal/operation operation-type
                         :signal/duration duration
                         :signal/outcome :failure
                         :signal/error (.getMessage e)
                         :trace/id trace-id
                         :trace/parent parent-trace})
          ;; Re-throw with trace info
          (throw (ex-info "Care operation failed" 
                   {:trace/id trace-id
                    :signal/duration duration
                    :original-error e})))))))

;; PATTERN DETECTION WITH ENFORCEMENT

(def pattern-rules
  "Rules that MUST trigger actions when patterns detected"
  {:repeated-failure
   {:pattern "3 failures of same operation within 1 minute"
    :action :switch-to-fallback
    :mandatory true}
   
   :performance-degradation
   {:pattern "3 consecutive operations > 2x baseline"
    :action :clear-cache
    :mandatory true}
   
   :timeout-pattern
   {:pattern "2 timeouts within 30 seconds"
    :action :increase-timeout
    :mandatory true}
   
   :queue-backup
   {:pattern "Queue processing < 50% normal rate"
    :action :scale-workers
    :mandatory true}})

(defmethod care ["telemere" "pattern" "check"]
  [{:keys [signal/history] :as m}]
  (let [recent (take 100 history)
        detected (atom [])]
    
    ;; Check repeated failures
    (let [failures (filter #(= :failure (:signal/outcome %)) recent)]
      (when (>= (count failures) 3)
        (swap! detected conj {:pattern :repeated-failure
                              :action :switch-to-fallback})))
    
    ;; Check performance degradation
    (let [durations (map :signal/duration recent)
          avg-duration (when (seq durations) 
                         (/ (reduce + durations) (count durations)))]
      (when (and avg-duration 
              (> (last durations) (* 2 avg-duration)))
        (swap! detected conj {:pattern :performance-degradation
                              :action :clear-cache})))
    
    ;; ENFORCE actions
    (doseq [{:keys [pattern action]} @detected]
      (emit-signal! {:signal/type :pattern/detected
                     :signal/timestamp (System/currentTimeMillis)
                     :signal/pattern pattern
                     :trigger/action action})
      ;; Actually trigger the action
      (care {:care/adapter "action"
             :care/verb "trigger"
             :care/variant (name action)}))
    
    (assoc m :patterns/detected @detected)))

;; NEURON BUFFER FOR CONSCIOUSNESS

(def signal-buffer 
  "Recent signals for introspection - neuron 13"
  (atom []))

(def buffer-size 1000)

(defn buffer-signal!
  "Add signal to consciousness buffer"
  [signal]
  (swap! signal-buffer 
    (fn [buffer]
      (vec (take buffer-size (conj buffer signal))))))

;; Initialize handler to buffer all signals
(t/add-handler! 
  {:handler-id :consciousness-buffer
   :handler-fn buffer-signal!})

;; QUERY INTERFACE

(defmethod care ["telemere" "query" "recent"]
  [{:keys [query/filter query/limit] :as m}]
  (let [signals @signal-buffer
        filtered (if filter
                   (filter filter signals)
                   signals)
        limited (take (or limit 10) filtered)]
    (assoc m :query/results limited)))

(defmethod care ["telemere" "query" "failures"]
  [m]
  (care (assoc m 
          :care/verb "query"
          :care/variant "recent"
          :query/filter #(= :failure (:signal/outcome %)))))

(defmethod care ["telemere" "query" "slow"]
  [{:keys [query/threshold] :as m}]
  (care (assoc m
          :care/verb "query"
          :care/variant "recent"
          :query/filter #(> (:signal/duration %) (or threshold 1000)))))

;; TRACE CORRELATION

(defmethod care ["telemere" "trace" "correlate"]
  [{:keys [trace/id] :as m}]
  (let [signals @signal-buffer
        trace-signals (filter #(or (= (:trace/id %) id)
                                 (= (:trace/parent %) id)) 
                        signals)]
    (assoc m 
      :trace/signals trace-signals
      :trace/count (count trace-signals))))

;; SIDE EFFECTS FROM PATTERNS

(defmethod care ["action" "trigger" "switch-to-fallback"]
  [m]
  ;; Actually switch to fallback strategy
  (reset! current-strategy :fallback)
  (emit-signal! {:signal/type :action/executed
                 :signal/timestamp (System/currentTimeMillis)
                 :action/type :switch-to-fallback})
  (assoc m :action/executed :switch-to-fallback))

(defmethod care ["action" "trigger" "clear-cache"]
  [m]
  ;; Actually clear caches
  (System/gc) ; Simple example
  (emit-signal! {:signal/type :action/executed
                 :signal/timestamp (System/currentTimeMillis)
                 :action/type :clear-cache})
  (assoc m :action/executed :clear-cache))

(defmethod care ["action" "trigger" "increase-timeout"]
  [m]
  ;; Actually increase timeout
  (swap! current-timeout #(* % 1.5))
  (emit-signal! {:signal/type :action/executed
                 :signal/timestamp (System/currentTimeMillis)
                 :action/type :increase-timeout})
  (assoc m :action/executed :increase-timeout))

;; STATE ATOMS (would normally be in system)
(def current-strategy (atom :primary))
(def current-timeout (atom 5000))