(ns tarabulus.interceptors.auth
  (:require
   [buddy.auth]
   [buddy.auth.backends :as buddy.backends]
   [buddy.auth.middleware :as buddy.auth.mdw]
   [rabat.edge.logger :refer [debug]]
   [ring.util.http-response :as http.res]
   [taoensso.encore :as e :refer [catching]]
   [tarabulus.data.user :as trbls.data.user]
   [tarabulus.edge.database :as trbls.edge.db]
   [tarabulus.edge.encoder :as trbls.edge.enc]))

(def ^:private backends-map
  {:basic   buddy.backends/basic
   :session buddy.backends/session
   :token   buddy.backends/token
   :jws     buddy.backends/jws
   :jwe     buddy.backends/jwe})

(defn- make-buddy-backend
  [{:keys [backend] :as opts}]
  (let [backend-f (or (get backends-map backend)
                      (throw (ex-info "unknown backend" {:backend backend})))
        opts'     (dissoc opts :backend)]
    (backend-f opts')))

(defn authenticated-interceptor
  []
  {:name  ::authenticated
   :enter (fn [{:keys [request] :as ctx}]
            (if (buddy.auth/authenticated? request)
              ctx
              (let [res (http.res/unauthorized
                          {:error {:category :unauthenticated
                                   :message  "unauthenticated"}})]
                (assoc ctx :queue [] :response res))))})

(defn authentication-interceptor
  [opts]
  {:name  ::authentication
   :enter (fn [ctx]
            (update ctx
                    :request
                    buddy.auth.mdw/authentication-request
                    (make-buddy-backend opts)))})

(defn basic-auth-backend-opts
  [{:keys [database logger]}]
  {:backend :basic
   :authfn  (fn [_ {:keys [username password] :as credential}]
              (when (and (some? username) (some? password))
                (catching
                  (some-> (trbls.edge.db/find-user database credential)
                          (trbls.data.user/authenticate-user password))
                  err
                  (debug logger ::basic-auth-backend-opts err))))})

(defn tarabulus-token-auth-backend-opts
  [{:keys [config auth-token-encoder api-token-encoder logger]} kind]
  {:backend    :token
   :authfn     (fn [_ token]
                 (let [token-encoder (if (= kind :api)
                                       api-token-encoder
                                       auth-token-encoder)]
                   (catching
                     (trbls.edge.enc/read-token token-encoder token kind)
                     err
                     (debug logger ::tarablus-token-auth-backend-opts err))))
   :token-name (get-in config [:token-name kind] "TarabulusToken")})

(defn authorized-interceptor
  []
  {:name  ::authorized
   :enter (fn [{:keys [request] :as ctx}]
            (if (::authorized? (:self request))
              ctx
              (let [res (http.res/forbidden
                          {:error {:category :unauthorized
                                   :message  "unauthorized"}})]
                (assoc ctx :queue [] :response res))))})

(defn authorization-interceptor
  [check-permission]
  {:name  ::authorization
   :enter (fn [{:keys [request] :as ctx}]
            (let [self (:self request)]
              (if (and (some? self) (check-permission request self))
                (assoc-in ctx [:request :identity ::authorized?] true)
                ctx)))})

(defn path-username-ok?
  [request self]
  (let [path-username (-> request :parameters :path :username)
        self-username (:username self)]
    (e/some= path-username self-username)))
