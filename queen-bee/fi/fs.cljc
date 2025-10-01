(ns workspace.net.drilling.care.adapters.fs
  "Care adapter for babashka/fs - everyday filesystem operations
   Map → filesystem operation → enriched map"
  (:require [babashka.fs :as fs]))

(defmulti care (fn [m] [(get m :care/adapter) 
                        (get m :care/verb) 
                        (get m :care/variant)]))

;; Check existence
(defmethod care ["fs" "exists?" "path"]
  [{:keys [fs/path] :as m}]
  (assoc m :fs/exists? (fs/exists? path)))

;; Read operations
(defmethod care ["fs" "read" "file"]
  [{:keys [fs/path] :as m}]
  (assoc m :fs/content (slurp (fs/file path))))

(defmethod care ["fs" "read" "lines"]
  [{:keys [fs/path] :as m}]
  (assoc m :fs/lines (fs/read-all-lines path)))

;; Write operations  
(defmethod care ["fs" "write" "file"]
  [{:keys [fs/path fs/content] :as m}]
  (spit (fs/file path) content)
  (assoc m :fs/written? true))

(defmethod care ["fs" "write" "lines"]
  [{:keys [fs/path fs/lines] :as m}]
  (fs/write-lines path lines)
  (assoc m :fs/written? true))

;; Copy and move
(defmethod care ["fs" "copy" "file"]
  [{:keys [fs/source fs/target] :as m}]
  (fs/copy source target)
  (assoc m :fs/copied? true))

(defmethod care ["fs" "move" "file"]
  [{:keys [fs/source fs/target] :as m}]
  (fs/move source target)
  (assoc m :fs/moved? true))

;; Delete operations
(defmethod care ["fs" "delete" "file"]
  [{:keys [fs/path] :as m}]
  (fs/delete path)
  (assoc m :fs/deleted? true))

(defmethod care ["fs" "delete" "tree"]
  [{:keys [fs/path] :as m}]
  (fs/delete-tree path)
  (assoc m :fs/deleted? true))

;; Directory operations
(defmethod care ["fs" "create" "dir"]
  [{:keys [fs/path] :as m}]
  (fs/create-dirs path)
  (assoc m :fs/created? true))

(defmethod care ["fs" "create" "temp-dir"]
  [{:keys [fs/prefix] :as m}]
  (let [temp-dir (fs/create-temp-dir {:prefix (or prefix "temp-")})]
    (assoc m 
      :fs/temp-path (str temp-dir)
      :fs/created? true)))

(defmethod care ["fs" "list" "dir"]
  [{:keys [fs/path] :as m}]
  (assoc m :fs/entries (mapv str (fs/list-dir path))))

(defmethod care ["fs" "glob" "files"]
  [{:keys [fs/path fs/pattern] :as m}]
  (assoc m :fs/matches (mapv str (fs/glob path pattern))))

;; File info
(defmethod care ["fs" "info" "file"]
  [{:keys [fs/path] :as m}]
  (let [f (fs/file path)]
    (assoc m
      :fs/directory? (fs/directory? f)
      :fs/regular-file? (fs/regular-file? f)
      :fs/size (when (fs/regular-file? f) (fs/size f))
      :fs/last-modified (str (fs/last-modified-time f)))))

(defmethod care ["fs" "info" "extension"]
  [{:keys [fs/path] :as m}]
  (assoc m :fs/extension (fs/extension path)))

;; Path operations
(defmethod care ["fs" "path" "absolute"]
  [{:keys [fs/path] :as m}]
  (assoc m :fs/absolute-path (str (fs/absolutize path))))

(defmethod care ["fs" "path" "normalize"]
  [{:keys [fs/path] :as m}]
  (assoc m :fs/normalized-path (str (fs/normalize path))))

(defmethod care ["fs" "path" "parent"]
  [{:keys [fs/path] :as m}]
  (assoc m :fs/parent (str (fs/parent path))))

(defmethod care ["fs" "path" "file-name"]
  [{:keys [fs/path] :as m}]
  (assoc m :fs/file-name (str (fs/file-name path))))

;; Path joining
(defmethod care ["fs" "path" "join"]
  [{:keys [fs/paths] :as m}]
  (assoc m :fs/joined-path (str (apply fs/path paths))))