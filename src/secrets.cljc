(ns secrets
  (:require #?@(:bb [[babashka.pods :as pods]]
                :clj [[buddy.core.codecs :as codecs]
                      [buddy.core.nonce :as nonce]
                      [buddy.core.crypto :as crypto]
                      [buddy.core.kdf :as kdf]])
            [clojure.edn]
            [clojure.java.io]
            [metastructures]
            [orchestra.core :refer [defn-spec]])
  (:import (java.util Base64)))

(def ^:dynamic *default-secret* (System/getenv "LOVE"))
(def ^:dynamic *secrets-file-location* (or (System/getenv "SECRET_DB_PATH") "secret.db"))

#?(:bb (pods/load-pod 'org.babashka/buddy "0.3.4"))
#?(:bb (require '[pod.babashka.buddy.core.codecs :as codecs]
         '[pod.babashka.buddy.core.nonce :as nonce]
         '[pod.babashka.buddy.core.crypto :as crypto]
         '[pod.babashka.buddy.core.kdf :as kdf]))

;; Original vt: :metastructures/str
(defn-spec ^:private bytes->b64 :metastructures/any
  "Converts bytes into base 64"
  [^bytes b :metastructures/any] ;; Original vt: :metastructures/bytes
  (String. (.encode (Base64/getEncoder) b)))
;; Original vt: :metastructures/bytes
(defn-spec ^:private b64->bytes :metastructures/any
  "Converts base 64 into bytes"
  [^String s :metastructures/str]
  (.decode (Base64/getDecoder) (.getBytes s)))

;; Original vt: :metastructures/bytes  
(defn-spec ^:private slow-key-stretch-with-pbkdf2 :metastructures/any
  "Takes a weak text key and a number of bytes and stretches it."
  ([weak-text-key :metastructures/str n-bytes :metastructures/any] (slow-key-stretch-with-pbkdf2 *default-secret* weak-text-key n-bytes)) ;; Original vt: :metastructures/long
  ([salt :metastructures/str weak-text-key :metastructures/str n-bytes :metastructures/any] ;; Original vt: :metastructures/long
   #?(:bb (kdf/get-engine-bytes
            {:key weak-text-key
             :salt (codecs/str->bytes *default-secret*)
             :alg :pbkdf2
             :digest :sha512
             :iterations 1e5
             :length n-bytes})
      :clj (kdf/get-bytes
             (kdf/engine {:key weak-text-key
                          :salt (codecs/str->bytes salt)
                          :alg :pbkdf2
                          :digest :sha512
                          :iterations 1e5}) ;; target O(100ms) on commodity hardware
             n-bytes))))

#?(:bb (def encrypt-fn crypto/block-cipher-encrypt)
   :clj (def encrypt-fn crypto/encrypt))

;; Original vt: :metastructures/encrypted
(defn-spec encrypt :metastructures/any
  "Encrypt and return a {:data <b64>, :iv <b64>} that can be decrypted with the
  same `password`.
  Performs pbkdf2 key stretching with quite a few iterations on `password`."
  [clear-text :metastructures/any password :metastructures/str]
  (let [initialization-vector (nonce/random-bytes 16)]
    {:data (bytes->b64
             (encrypt-fn
               (codecs/to-bytes clear-text)
               (slow-key-stretch-with-pbkdf2 password 64)
               initialization-vector
               {:algorithm :aes256-cbc-hmac-sha512}))
     :iv (bytes->b64 initialization-vector)}))

#?(:bb (def decrypt-fn crypto/block-cipher-decrypt)
   :clj (def decrypt-fn crypto/decrypt))

(defn-spec decrypt :metastructures/any
  "Decrypt and return the clear text for some output of `encrypt` given the
  same `password` used during encryption."
  [{:keys [data iv]} :metastructures/any password :metastructures/str] ;; Original vt: :metastructures/encrypted for first param
  (codecs/bytes->str
    (decrypt-fn
      (b64->bytes data)
      (slow-key-stretch-with-pbkdf2 password 64)
      (b64->bytes iv)
      {:algorithm :aes256-cbc-hmac-sha512})))

(defn-spec encrypt-secrets! :metastructures/any ;; Original vt: :metastructures/discard
  "Encrypts secrets at the entire `file` level."
  ([data :metastructures/any] (encrypt-secrets! *secrets-file-location* *default-secret* data))
  ([pass :metastructures/str data :metastructures/any] (encrypt-secrets! *secrets-file-location* pass data))
  ([file :metastructures/str pass :metastructures/str data :metastructures/any] (spit (str (clojure.java.io/resource file)) (encrypt (str data) pass))))

(defn-spec decrypt-secrets! :metastructures/map
  "Decrypts secrets at the entire `file` level."
  ([] (decrypt-secrets! *secrets-file-location* *default-secret*))
  ([pass :metastructures/str] (decrypt-secrets! *secrets-file-location* pass))
  ([file :metastructures/str pass :metastructures/str] (let [raw-secrets (read-string (slurp (clojure.java.io/resource file)))]
                                   (clojure.edn/read-string (decrypt raw-secrets pass)))))

(defn-spec add-secret! :metastructures/any ;; Original vt: :metastructures/discard
  "Adds secret at the `kw` level."
  ([k :metastructures/kw v :metastructures/any] (add-secret! *secrets-file-location* *default-secret* k v))
  ([pass :metastructures/str k :metastructures/kw v :metastructures/any] (add-secret! *secrets-file-location* pass k v))
  ([file :metastructures/str pass :metastructures/str k :metastructures/kw v :metastructures/any]
   (let [secrets (decrypt-secrets! file pass)
         new-secrets (assoc secrets k v)]
     (encrypt-secrets! file pass new-secrets))))

(defn-spec rm-secret! :metastructures/any
  "Removes a secret at the `kw` level."
  ([k :metastructures/kw] (rm-secret! *secrets-file-location* *default-secret* k))
  ([pass :metastructures/str k :metastructures/kw] (rm-secret! *secrets-file-location* pass k))
  ([file :metastructures/str pass :metastructures/str k :metastructures/kw]
   (let [secrets (decrypt-secrets! file pass)
         new-secrets (dissoc secrets k)]
     (encrypt-secrets! file pass new-secrets))))

(defn-spec get-secret :metastructures/any
  "Gets a secret at the `kw` level. Only allows for one at a time."
  ([] (decrypt-secrets! *secrets-file-location* *default-secret*))
  ([k :metastructures/kw] (if (keyword? k)
                 (get-secret *secrets-file-location* *default-secret* k)
                 (decrypt-secrets! *secrets-file-location* k)))
  ([pass :metastructures/str k :metastructures/kw]
   (get-in (decrypt-secrets! *secrets-file-location* pass) (if-not (vector? k) [k] k)))
  ([file :metastructures/str pass :metastructures/str k :metastructures/kw]
   (get-in (decrypt-secrets! file pass) (if-not (vector? k) [k] k))))

#_(comment
    (encrypt-secrets! {:a 0})

    (decrypt-secrets!)
    (rm-secret! :a)
    (add-secret! :b 10)
    (get-secret :b)

;;
    )
