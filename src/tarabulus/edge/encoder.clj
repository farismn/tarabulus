(ns tarabulus.edge.encoder)

(defprotocol TokenEncoder
  (encode [token-encoder params])
  (decode [token-encoder params]))
