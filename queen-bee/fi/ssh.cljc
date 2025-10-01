(ns workspace.net.drilling.care.adapters.ssh
  "Care adapter for bbssh - SSH operations with great responsibility
   Map → session → remote operation → enriched map
   
   SAFETY: Sessions are stateful. Always close them.
   SECURITY: Never log credentials. Handle with care."
  (:require [babashka.ssh :as bbssh]))

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; Session management
(defmethod care ["ssh" "session" "create"]
  [{:keys [ssh/host ssh/username ssh/password ssh/private-key ssh/port] :as m}]
  (try
    (let [config (cond-> {:host host
                          :username username
                          :port (or port 22)}
                   password (assoc :password password)
                   private-key (assoc :private-key private-key))
          session (bbssh/ssh config)]
      (assoc m 
        :ssh/session session
        :ssh/connected? true))
    (catch Exception e
      (assoc m
        :ssh/connected? false
        :ssh/error (.getMessage e)))))

(defmethod care ["ssh" "session" "close"]
  [{:keys [ssh/session] :as m}]
  (when session
    (.close session))
  (assoc m 
    :ssh/session nil
    :ssh/connected? false))

;; Execute commands
(defmethod care ["ssh" "exec" "simple"]
  [{:keys [ssh/session ssh/command] :as m}]
  (if-not session
    (assoc m :ssh/error "No session established")
    (try
      (let [result (bbssh/exec session command)]
        (assoc m
          :ssh/output (:out result)
          :ssh/error (:err result)
          :ssh/exit (:exit result)
          :ssh/success? (zero? (:exit result))))
      (catch Exception e
        (assoc m
          :ssh/success? false
          :ssh/error (.getMessage e))))))

;; SCP operations - namespace deeper as requested
(defmethod care ["ssh" "scp" "to"]
  [{:keys [ssh/session ssh/local-path ssh/remote-path] :as m}]
  (if-not session
    (assoc m :ssh/error "No session established")
    (try
      (bbssh/scp-to session local-path remote-path)
      (assoc m 
        :ssh/transferred? true
        :ssh/direction :upload)
      (catch Exception e
        (assoc m
          :ssh/transferred? false
          :ssh/error (.getMessage e))))))

(defmethod care ["ssh" "scp" "from"]
  [{:keys [ssh/session ssh/remote-path ssh/local-path] :as m}]
  (if-not session
    (assoc m :ssh/error "No session established")
    (try
      (bbssh/scp-from session remote-path local-path)
      (assoc m
        :ssh/transferred? true
        :ssh/direction :download)
      (catch Exception e
        (assoc m
          :ssh/transferred? false
          :ssh/error (.getMessage e))))))

;; Execute with input stream (for commands that need input)
(defmethod care ["ssh" "exec" "with-input"]
  [{:keys [ssh/session ssh/command ssh/input] :as m}]
  (if-not session
    (assoc m :ssh/error "No session established")
    (try
      (let [result (bbssh/exec session command {:in input})]
        (assoc m
          :ssh/output (:out result)
          :ssh/exit (:exit result)))
      (catch Exception e
        (assoc m
          :ssh/success? false
          :ssh/error (.getMessage e))))))

;; Safe session wrapper - auto-closes
(defmethod care ["ssh" "with-session" "exec"]
  [{:keys [ssh/config ssh/command] :as m}]
  (try
    (let [session (bbssh/ssh config)
          result (bbssh/exec session command)]
      (.close session)
      (assoc m
        :ssh/output (:out result)
        :ssh/exit (:exit result)
        :ssh/success? (zero? (:exit result))))
    (catch Exception e
      (assoc m
        :ssh/success? false
        :ssh/error (.getMessage e)))))

;; SFTP operations (if needed)
(defmethod care ["ssh" "sftp" "list"]
  [{:keys [ssh/session ssh/remote-dir] :as m}]
  (if-not session
    (assoc m :ssh/error "No session established")
    (try
      (let [files (bbssh/sftp-list session remote-dir)]
        (assoc m :ssh/files files))
      (catch Exception e
        (assoc m
          :ssh/success? false
          :ssh/error (.getMessage e))))))