(ns tarabulus.edge.ring)

(defprotocol RouteMatcher
  (match-by-handler
    [component handler params]
    [component handler]))
