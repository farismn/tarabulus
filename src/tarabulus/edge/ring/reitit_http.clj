(ns tarabulus.edge.ring.reitit-http
  (:require
   [rabat.components.reitit-http]
   [rabat.edge.reitit :as rbt.edge.reit]
   [reitit.core :as reit]
   [tarabulus.edge.ring :as trbls.edge.r]))

(extend-protocol trbls.edge.r/RouteMatcher
  rabat.components.reitit_http.RingRouter
  (match-by-handler
    ([component handler params]
     (let [router (rbt.edge.reit/request-router component)
           result (reit/match-by-name router handler params)]
       (:path result)))
    ([component handler]
     (let [router (rbt.edge.reit/request-router component)
           result (reit/match-by-name router handler)]
       (:path result)))))
