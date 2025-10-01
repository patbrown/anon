(ns workspace.net.drilling.care.adapters.shell
  "Care adapter for babashka/process - shell execution with safety"
  (:require [babashka.process :as p]
            [clojure.java.io :as io]))

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; Simple shell execution
(defmethod care ["shell" "exec" "simple"]
  [{:keys [shell/command] :as m}]
  (let [result (p/shell {:out :string :err :string} command)]
    (assoc m 
      :shell/output (:out result)
      :shell/error (:err result)
      :shell/exit (:exit result)
      :shell/success? (zero? (:exit result)))))

;; Execute with check (throws on non-zero exit)
(defmethod care ["shell" "exec" "checked"]
  [{:keys [shell/command] :as m}]
  (try
    (let [result (p/shell {:out :string :err :string :pre (fn [_] true)} command)]
      (assoc m
        :shell/output (:out result)
        :shell/success? true))
    (catch Exception e
      (assoc m
        :shell/success? false
        :shell/error (.getMessage e)))))

;; LIVE STREAMING - watch output as it happens
(defmethod care ["shell" "exec" "stream"]
  [{:keys [shell/command shell/on-line] :as m}]
  (let [on-line (or on-line println) ; default to println if no handler
        proc (p/process command {:out :stream :err :stream})
        output (StringBuilder.)]
    ;; Stream stdout line by line
    (with-open [rdr (io/reader (:out proc))]
      (doseq [line (line-seq rdr)]
        (on-line line) ; Call handler with each line
        (.append output line)
        (.append output "\n")))
    (assoc m
      :shell/output (str output)
      :shell/exit (:exit @proc)
      :shell/success? (zero? (:exit @proc)))))

;; Pipeline multiple commands
(defmethod care ["shell" "exec" "pipeline"]
  [{:keys [shell/commands] :as m}]
  (let [result (apply p/pipeline (map #(p/process %) commands))
        output (slurp (:out (last result)))]
    (assoc m
      :shell/output output
      :shell/success? true)))

;; Execute in specific directory
(defmethod care ["shell" "exec" "in-dir"]
  [{:keys [shell/command shell/dir] :as m}]
  (let [result (p/shell {:dir dir :out :string :err :string} command)]
    (assoc m
      :shell/output (:out result)
      :shell/exit (:exit result))))

;; Execute with environment
(defmethod care ["shell" "exec" "with-env"]
  [{:keys [shell/command shell/env] :as m}]
  (let [result (p/shell {:extra-env env :out :string :err :string} command)]
    (assoc m
      :shell/output (:out result)
      :shell/exit (:exit result))))

;; Execute with timeout
(defmethod care ["shell" "exec" "timeout"]
  [{:keys [shell/command shell/timeout-ms] :as m}]
  (let [proc (p/process command {:out :string :err :string})
        result (deref proc timeout-ms ::timeout)]
    (if (= result ::timeout)
      (do
        (p/destroy proc)
        (assoc m 
          :shell/timeout? true
          :shell/success? false
          :shell/error "Process timed out"))
      (assoc m
        :shell/output (:out @proc)
        :shell/exit (:exit @proc)
        :shell/success? (zero? (:exit @proc))))))