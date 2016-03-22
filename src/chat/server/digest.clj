(ns chat.server.digest
  (:import [java.security MessageDigest]
           [org.apache.commons.codec.binary Base64]))

(defn- sha256-digest [bs]
  (doto (MessageDigest/getInstance "SHA-256") (.update bs)))

(defn sha256 [msg]
  (-> msg .getBytes sha256-digest .digest))

(defn from-file
  [f]
  (-> (str "public" f)
      clojure.java.io/resource
      slurp
      sha256
      Base64/encodeBase64
      String.))
