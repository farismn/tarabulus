(ns tarabulus.data.token
  (:require
   [malli.core :as ml]
   [malli.transform :as ml.trans]
   [taoensso.encore :as e]))

(def Claims [:map])
(def Token string?)
(def Kind keyword?)
(def Payload [:map [:kind Kind]])

(defn sanitize-claims
  [claims]
  (dissoc claims :password))

(defn assoc-kind
  [claims kind]
  (assoc claims :kind kind))

(defn coerce-payload
  [payload]
  (ml/decode Payload payload ml.trans/json-transformer))

(defn validate-payload
  [payload kind]
  (when (e/some= (:kind payload) kind)
    payload))
