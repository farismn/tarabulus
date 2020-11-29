(ns tarabulus.edge.encoder.jwt
  (:require
   [clj-time.core :as time]
   [rabat.components.jwt-encoder]
   [rabat.edge.jwt-encoder :as rbt.edge.jwt]
   [tarabulus.edge.encoder :as trbls.edge.enc]))

(defn- assoc-time
  [claims duration]
  (let [iat (time/now)
        exp (when (some? duration)
              (time/plus iat (time/seconds duration)))]
    (cond-> (assoc claims :iat iat)
      (some? exp)
      (assoc :exp exp))))

(extend-protocol trbls.edge.enc/TokenEncoder
  rabat.components.jwt_encoder.SHASigner
  (encode [{:keys [config] :as component} claims]
    (let [duration (:duration config)
          claims'  (assoc-time claims duration)]
      (rbt.edge.jwt/encode component claims')))
  (decode [component token]
    (rbt.edge.jwt/decode component token))

  rabat.components.jwt_encoder.AsymmetricSigner
  (encode [{:keys [config] :as component} claims]
    (let [duration (:duration config)
          claims'  (assoc-time claims duration)]
      (rbt.edge.jwt/encode component claims')))
  (decode [component token]
    (rbt.edge.jwt/decode component token)))
