(ns tarabulus.routes.user
  (:require
   [tarabulus.data.token :as trbls.data.token]
   [tarabulus.data.user :as trbls.data.user]
   [tarabulus.edge.database :as trbls.edge.db]
   [tarabulus.edge.encoder :as trbls.edge.enc]
   [tarabulus.interceptors.auth :as trbls.icept.auth]
   [ring.util.http-response :as http.res]
   [ring.util.http-status :as http.sta]))

(def ^:private WrappedAuthToken
  [:map [:auth trbls.data.token/Token]])

(def Registree
  [:map
   [:username trbls.data.user/Username]
   [:password trbls.data.user/Password]])

(def PasswordUpdatee
  [:map
   [:new-password trbls.data.user/Password]
   [:old-password string?]])

(def PasswordResetee
  [:map
   [:new-password trbls.data.user/Password]])

(defn- make-auth-token
  [auth-token-encoder attrs]
  (trbls.edge.enc/make-token auth-token-encoder attrs :auth))

(defn- register-user
  [{:keys [database auth-token-encoder]} {:keys [parameters]}]
  (let [attrs (-> parameters :body :user :registree trbls.data.user/create-user)
        user  (trbls.edge.db/create-user! database attrs)
        token (make-auth-token auth-token-encoder user)]
    (http.res/ok {:token {:auth token}})))

(defn- request-auth-token
  [{:keys [auth-token-encoder]} {:keys [parameters]}]
  (let [params (:path parameters)
        token  (make-auth-token auth-token-encoder params)]
    (http.res/ok {:token {:auth token}})))

(defn- erase-user
  [{:keys [database]} {:keys [parameters]}]
  (let [params (:path parameters)
        result (trbls.edge.db/delete-user! database params)]
    (if (:database/happened? result)
      (http.res/no-content)
      (http.res/bad-request {:error {:category :incorrect
                                     :message  "can't delete the user"}}))))

(defn- modify-user-password
  [{:keys [database auth-token-encoder]} {:keys [parameters]}]
  (letfn [(user-exist?
            [{:keys [old-password] :as params}]
            (some? (some-> (trbls.edge.db/find-user database params)
                           (trbls.data.user/authenticate-user old-password))))]
    (let [params (merge (:path parameters)
                        (-> parameters :body :user :password-updatee))
          user   (when (user-exist? params)
                   (-> params
                       (trbls.data.user/reset-user-password)
                       (as-> <> (trbls.edge.db/reset-user-password! database <>))))
          _      (when (nil? user)
                   (http.res/bad-request!
                     {:error {:category :incorrect
                              :message  "can't update the user's password"}}))
          token  (make-auth-token auth-token-encoder user)]
      (http.res/ok {:token {:auth token}}))))

(defn- revive-user
  [{:keys [database auth-token-encoder]} {:keys [parameters]}]
  (let [params (:path parameters)
        user   (trbls.edge.db/restore-user! database params)]
    (if (nil? user)
      (http.res/bad-request {:error {:category :incorrect
                                     :message  "can't restore the user"}})
      (let [token (make-auth-token auth-token-encoder user)]
        (http.res/ok {:token {:auth token}})))))

(defn- overwrite-user-password
  [{:keys [database auth-token-encoder]} {:keys [parameters]}]
  (let [username    (-> parameters :path :username)
        pwd-resetee (-> parameters :body :user :password-resetee)
        user        (-> (assoc pwd-resetee :username username)
                        (trbls.data.user/reset-user-password)
                        (as-> <> (trbls.edge.db/reset-user-password! database <>)))]
    (if (nil? user)
      (http.res/bad-request {:error {:category :incorrect
                                     :message  "can't reset the user's password"}})
      (let [token (make-auth-token auth-token-encoder user)]
        (http.res/ok {:token {:auth token}})))))

(defn routes
  [component]
  [["/api/user"
    {:name ::anon
     :post {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
            :parameters {:body [:map [:user [:map [:registree Registree]]]]}
            :handler    (partial register-user component)}}]
   ["/api/user/:username"
    {:name       ::target
     :parameters {:path [:map [:username trbls.data.user/Username]]}
     :post       {:responses    {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/basic-auth-backend-opts component))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      (partial request-auth-token component)}
     :put        {:responses    {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :parameters   {:body [:map [:user [:map [:password-updatee PasswordUpdatee]]]]}
                  :interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/tarabulus-token-auth-backend-opts component :auth))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      (partial modify-user-password component)}
     :delete     {:interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/tarabulus-token-auth-backend-opts component :auth))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      (partial erase-user component)}}]
   ["/api/user/:username/restore"
    {:name       ::restore
     :parameters {:path [:map [:username trbls.data.user/Username]]}
     :post       {:responses    {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/tarabulus-token-auth-backend-opts component :restore))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      (partial revive-user component)}}]
   ["/api/user/:username/reset"
    {:name       ::reset
     :parameters {:path [:map [:username trbls.data.user/Username]]}
     :put        {:responses    {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :parameters   {:body [:map [:user [:map [:password-resetee PasswordResetee]]]]}
                  :interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/tarabulus-token-auth-backend-opts component :reset))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      (partial overwrite-user-password component)}}]])
