(ns modrepl
  (:require [babashka.fs]
            [functions]
            [clojure.java.io]
            [clojure.pprint])
  (:import clojure.lang.LineNumberingPushbackReader))

;; API
(defmulti mod-enter  :enter-with)
(defmulti mod-repl   :repl-with)
(defmulti mod-prepend :prepend-with)
(defmulti mod-append :append-with)
(defmulti mod-read   :read-with)
(defmulti mod-eval   :eval-with)
(defmulti mod-print  :print-with)
(defmulti mod-loop   :loop-with)
(defmulti mod-exit   :exit-with)

(def default-config {:enter-with :default
                     :repl-with :default
                     :prepend-with :default
                     :append-with :default
                     :read-with :default
                     :eval-with :default
                     :print-with :default
                     :loop-with :default
                     :exit-with :default})

(defmacro with-read-known
  "Evaluates body with *read-eval* set to a \"known\" value,
   i.e. substituting true for :unknown if necessary."
  [& body]
  `(binding [*read-eval* (if (= :unknown *read-eval*) true *read-eval*)]
     ~@body))

;; Copied from clojure.main to satisfy portability requirements.
(defn skip-whitespace
  [s]
  (loop [c (.read s)]
    (cond
     (= c (int \newline)) :line-start
     (= c -1) :stream-end
     (= c (int \;)) (do (.readLine s) :line-start)
     (or (Character/isWhitespace (char c)) (= c (int \,))) (recur (.read s))
     :else (do (.unread s c) :body))))

;; Copied from clojure.main to satisfy portability requirements.
(defn skip-if-eol
  [s]
  (let [c (.read s)]
    (cond
     (= c (int \newline)) :line-start
     (= c -1) :stream-end
     :else (do (.unread s c) :body))))

(defn extract-value-leave-context [input]
  (let [read-eval *read-eval*
        value (binding [*read-eval* read-eval] (eval input))
        _ (set! *3 *2)
        _ (set! *2 *1)
        _ (set! *1 value)]
    value))

(defmethod mod-enter :default
  [config] config)

(defmethod mod-prepend :default
  [_] "")

(defmethod mod-append :default
  [_] "")

(defmethod mod-exit :default
  [config] config)

;;; ### Default Print
(defmethod mod-print :default [{:keys [value] :as config}]
  (let [_ (doall [(print "=> ")
                  (clojure.pprint/pprint value)
                  (println)])]
    config))

;;; ### Default Eval
(defmethod mod-eval :default
  [{:keys [input] :as config}]
  (assoc config :value (extract-value-leave-context input)))

;;; ### Default Read
(defmethod mod-read :default
  [{:keys [request-prompt request-exit] :as config}]
  (assoc config
         :input (with-read-known
                  (or ({:line-start request-prompt :stream-end request-exit}
                       (skip-whitespace *in*))
                      (let [*input (read {:read-cond :allow} *in*)]
                        (skip-if-eol *in*)
                        (case *input
                          :repl/quit request-exit
                          *input))))))

(defn read-eval-print
  [{:keys [request-prompt request-exit] :as config}]
  (let [read-results (mod-read config)
        {:keys [input] :as eval-results} (mod-eval read-results)]
    (if (#{request-prompt request-exit} input)
      eval-results
      (mod-print eval-results))))

;;; ### Default Loop
#?(:bb (def ^:dynamic *source-path* nil))
(defmethod mod-loop :default
  [config]
  ;; Sets up a classloader on JVM. And an atom that holds the context.
  (let [cl #?(:bb nil
              :clj (.getContextClassLoader (Thread/currentThread)))
        _ #?(:bb nil
             :clj (.setContextClassLoader
                   (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl)))
        config-holder (atom config)
        value-holder (atom [])]
    (try
      (loop []
        (let [{:keys [value request-exit] :as config} (read-eval-print config)
              _ (reset! config-holder config)
              _ (swap! value-holder conj value)]
          (when-not (identical? value request-exit)
            (recur))))
      (finally (mod-exit (assoc @config-holder :values (butlast @value-holder)))))))

;; # mod-repl
(defmethod mod-repl :default
  [config]
  (let [new-uuid (functions/create-uuid)
        {:keys [nmsp script] :or {nmsp (str "repl-" (str new-uuid))}} config
        ns (symbol nmsp)
        readable-file? (babashka.fs/readable? (str script))
        tmp-path (str "tmp/" (str new-uuid))
        tmp-path-fn (fn [s] (let [_ (spit tmp-path s)]
                              tmp-path))
        script (if readable-file?
                 script
                 (let [the-string (str (mod-prepend config) " "
                                       script " "
                                       (mod-append config))]
                   (tmp-path-fn the-string)))]
    (binding [*ns* *ns*]
      (in-ns ns)
      (clojure.core/use 'clojure.core)
      (with-open [rdr (LineNumberingPushbackReader. (clojure.java.io/reader script))]
        (binding [*source-path* script *in* rdr]
          (mod-loop (assoc config :tmp-path tmp-path)))))))

(defn play [config]
  (if (vector? config)
    (map play config)
    (let [config (mod-enter (merge default-config
                                   {:request-prompt (Object.) :request-exit (Object.)}
                                   config))]
      (mod-repl config))))
