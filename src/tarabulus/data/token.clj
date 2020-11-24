(ns tarabulus.data.token
  (:require
   [malli.core :as ml]
   [malli.transform :as ml.trans]
   [tarabulus.data.token :as trbls.data.token]
   [taoensso.encore :as e]))

(def Token string?)
(def Kind keyword?)
(def Payload [:map [:kind Kind]])

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
