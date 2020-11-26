(ns tarabulus.data.token
  (:require
   [malli.core :as ml]
   [malli.transform :as ml.trans]
   [taoensso.encore :as e]))

(def Claims map?)
(def Token string?)
(def Kind keyword?)
(def Payload [:map [:kind Kind]])

(def EncodeParams
  [:map
   [:claims Claims]])

(defn sanitize-claims
  [params]
  (update params :claims dissoc :password))

(defn assoc-kind
  [params kind]
  (assoc-in params [:claims :kind] kind))

(defn coerce-payload
  [payload]
  (ml/decode Payload payload ml.trans/json-transformer))

(defn validate-payload
  [payload kind]
  (when (e/some= (:kind payload) kind)
    payload))
