(ns tarabulus.routes.token
  (:require
   [fmnoise.flow :as flow]
   [ring.util.http-response :as http.res]
   [ring.util.http-status :as http.sta]
   [tarabulus.data.token :as trbls.data.token]
   [tarabulus.data.user :as trbls.data.user]
   [tarabulus.edge.encoder :as trbls.edge.enc]
   [tarabulus.handler.exception :as trbls.handler.ex]
   [tarabulus.interceptors.auth :as trbls.icept.auth]))

(def AcceptedKinds
  [:enum :reset :restore])

(defn- request-token-handler
  [{:keys [auth-token-encoder]} {:keys [parameters]}]
  (let [{:keys [kind] :as params} (:path parameters)]
    (->> {:claims params}
         (trbls.data.token/sanitize-claims)
         (flow/then-call #(trbls.edge.enc/encode auth-token-encoder %))
         (flow/then #(http.res/ok {:token {kind %}}))
         (flow/else trbls.handler.ex/clj-ex-handler))))

(defn routes
  [component]
  [["/api/token/:kind/:username"
    {:name       ::target
     :parameters {:path [:map
                         [:kind [:and trbls.data.token/Kind AcceptedKinds]]
                         [:username trbls.data.user/Username]]}
     :get        {:responses    {http.sta/ok {:body [:map
                                                     [:token
                                                      [:map-of
                                                       trbls.data.token/Kind
                                                       trbls.data.token/Token]]]}}
                  :interceptors [(trbls.icept.auth/authentication-interceptor
                                   (trbls.icept.auth/tarabulus-token-auth-backend-opts component :api))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor
                                   trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      (partial request-token-handler component)}}]])
