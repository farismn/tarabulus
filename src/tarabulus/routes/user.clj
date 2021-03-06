(ns tarabulus.routes.user
  (:require
   [fmnoise.flow :as flow]
   [tarabulus.data.token :as trbls.data.token]
   [tarabulus.data.user :as trbls.data.user]
   [tarabulus.edge.database :as trbls.edge.db]
   [tarabulus.edge.encoder :as trbls.edge.enc]
   [tarabulus.handler.exception :as trbls.handler.ex]
   [tarabulus.interceptors.auth :as trbls.icept.auth]
   [ring.util.http-response :as http.res]
   [ring.util.http-status :as http.sta])
  (:import
   [org.postgresql.util PSQLException]))

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
  [auth-token-encoder claims]
  (-> claims
      (trbls.data.token/sanitize-claims)
      (trbls.data.token/assoc-kind :auth)
      (as-> <> (trbls.edge.enc/encode auth-token-encoder <>))))

(defn- register-user
  [{:keys [database auth-token-encoder]} {:keys [parameters]}]
  (->> (-> parameters :body :user :registree)
       (flow/then-call #(trbls.edge.db/add-user! database %))
       (flow/then-call #(make-auth-token auth-token-encoder %))
       (flow/then #(http.res/ok {:token {:auth %}}))
       (flow/else-if PSQLException trbls.handler.ex/pg-ex-handler)
       (flow/else trbls.handler.ex/clj-ex-handler)))

(defn- request-auth-token
  [{:keys [auth-token-encoder]} {:keys [parameters]}]
  (->> (:path parameters)
       (flow/then-call #(make-auth-token auth-token-encoder %))
       (flow/then #(http.res/ok {:token {:auth %}}))
       (flow/else trbls.handler.ex/clj-ex-handler)))

(defn- erase-user
  [{:keys [database]} {:keys [parameters]}]
  (->> (-> parameters :path :username)
       (flow/then-call #(trbls.edge.db/delete-user! database %))
       (flow/then (fn [result]
                    (if (:database/happened? result)
                      result
                      (let [message "unable to delete the user"
                            body    {:error {:message message}}]
                        (ex-info message {:kind     :tarabulus/exception
                                          :category :incorrect
                                          :body     body})))))
       (flow/then (constantly (http.res/no-content)))
       (flow/else-if PSQLException trbls.handler.ex/pg-ex-handler)
       (flow/else trbls.handler.ex/clj-ex-handler)))

(defn- modify-user-password
  [{:keys [database auth-token-encoder]} {:keys [parameters]}]
  (let [username (-> parameters :path :username)
        new-pwd  (-> parameters :body :user :password-updatee :new-password)
        old-pwd  (-> parameters :body :user :password-updatee :old-password)]
    (->> (flow/call trbls.edge.db/update-user-password!
                    database
                    username
                    new-pwd
                    old-pwd)
         (flow/then (fn [user]
                      (if (nil? user)
                        (let [message "unable to reset the user's password"
                              body    {:error {:message message}}]
                          (ex-info message {:kind     :tarabulus/exception
                                            :category :incorrect
                                            :body     body}))
                        user)))
         (flow/then #(make-auth-token auth-token-encoder %))
         (flow/then #(http.res/ok {:token {:auth %}}))
         (flow/else-if PSQLException trbls.handler.ex/pg-ex-handler)
         (flow/else trbls.handler.ex/clj-ex-handler))))

(defn- revive-user
  [{:keys [database auth-token-encoder]} {:keys [parameters]}]
  (->> (-> parameters :path :username)
       (flow/then-call #(trbls.edge.db/restore-user! database %))
       (flow/then (fn [user]
                    (if (nil? user)
                      (let [message "unable to restore the user"
                            body    {:error {:message message}}]
                        (ex-info message {:kind     :tarabulus/exception
                                          :category :incorrect
                                          :body     body}))
                      user)))
       (flow/then #(make-auth-token auth-token-encoder %))
       (flow/then #(http.res/ok {:token {:auth %}}))
       (flow/else-if PSQLException trbls.handler.ex/pg-ex-handler)
       (flow/else trbls.handler.ex/clj-ex-handler)))

(defn- overwrite-user-password
  [{:keys [database auth-token-encoder]} {:keys [parameters]}]
  (let [username (-> parameters :path :username)
        new-pwd  (-> parameters :body :user :password-resetee :new-password)]
    (->> (flow/call trbls.edge.db/set-user-password! database username new-pwd)
         (flow/then (fn [user]
                      (if (nil? user)
                        (let [message "unable to reset the user's password"
                              body    {:error {:message message}}]
                          (ex-info message {:kind     :tarabulus/exception
                                            :category :incorrect
                                            :body     body}))
                        user)))
         (flow/then #(make-auth-token auth-token-encoder %))
         (flow/then #(http.res/ok {:token {:auth %}}))
         (flow/else-if PSQLException trbls.handler.ex/pg-ex-handler)
         (flow/else trbls.handler.ex/clj-ex-handler))))

(defn routes
  [component]
  [["/api/user"
    {:name ::anon
     :post {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
            :parameters {:body [:map [:user [:map [:registree Registree]]]]}
            :handler    #(register-user component %)}}]
   ["/api/user/:username"
    {:name       ::target
     :parameters {:path [:map [:username trbls.data.user/Username]]}
     :post       {:responses    {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/basic-auth-backend-opts component))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      #(request-auth-token component %)}
     :put        {:responses    {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :parameters   {:body [:map [:user [:map [:password-updatee PasswordUpdatee]]]]}
                  :interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/tarabulus-token-auth-backend-opts component :auth))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      #(modify-user-password component %)}
     :delete     {:interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/tarabulus-token-auth-backend-opts component :auth))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      #(erase-user component %)}}]
   ["/api/user/:username/restore"
    {:name       ::restore
     :parameters {:path [:map [:username trbls.data.user/Username]]}
     :post       {:responses    {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/tarabulus-token-auth-backend-opts component :restore))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      #(revive-user component %)}}]
   ["/api/user/:username/reset"
    {:name       ::reset
     :parameters {:path [:map [:username trbls.data.user/Username]]}
     :put        {:responses    {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :parameters   {:body [:map [:user [:map [:password-resetee PasswordResetee]]]]}
                  :interceptors [(trbls.icept.auth/authentication-interceptor (trbls.icept.auth/tarabulus-token-auth-backend-opts component :reset))
                                 (trbls.icept.auth/authenticated-interceptor)
                                 (trbls.icept.auth/authorization-interceptor trbls.icept.auth/path-username-ok?)
                                 (trbls.icept.auth/authorized-interceptor)]
                  :handler      #(overwrite-user-password component %)}}]])
