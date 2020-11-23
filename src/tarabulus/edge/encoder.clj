(ns tarabulus.edge.encoder
  (:require
   [malli.core :as ml]
   [malli.transform :as ml.trans]
   [tarabulus.data.token :as trbls.data.token]))

(defprotocol TokenEncoder
  (encode [token-encoder data])
  (decode [token-encoder token]))

(defn make-token
  [token-encoder data kind]
  (let [claims (-> data (dissoc :password) (assoc :kind kind))]
    (encode token-encoder claims)))

(defn read-token
  [token-encoder token kind]
  (let [payload      (decode token-encoder token)
        payload'     (ml/decode trbls.data.token/Payload
                                payload
                                ml.trans/json-transformer)
        payload-kind (:kind payload')]
    (when (and (some? kind) (some? payload-kind) (= payload-kind kind))
      payload')))
