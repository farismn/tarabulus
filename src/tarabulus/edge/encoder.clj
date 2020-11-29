(ns tarabulus.edge.encoder)

(defprotocol TokenEncoder
  (encode [token-encoder claims])
  (decode [token-encoder token]))
