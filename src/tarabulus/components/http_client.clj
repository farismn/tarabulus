(ns tarabulus.components.http-client
  (:refer-clojure :exclude [if-let])
  (:require
   [clj-http.client :as http.clt]
   [rabat.edge.logger :as rbt.edge.log :refer [debug]]
   [rabat.utils.component :as rbt.u.c]
   [taoensso.encore :as e :refer [if-let catching]]
   [tarabulus.data.token :as trbls.data.token]
   [tarabulus.edge.encoder :as trbls.edge.enc]
   [tarabulus.edge.client :as trbls.edge.clt]
   [tarabulus.edge.ring :as trbls.edge.r]
   [tarabulus.routes.meta :as trbls.rts.meta]
   [tarabulus.routes.token :as trbls.rts.token]
   [tarabulus.routes.user :as trbls.rts.user]))

(defn- get-logger
  [{:keys [logger] :as component}]
  (or logger (rbt.u.c/get-satisfied component rbt.edge.log/Logger)))

(defn- assoc-content-type
  [{:keys [form-params] :as req} {:keys [config]}]
  (let [content-type (:content-type config :json)]
    (e/assoc-when req
                  :accept content-type
                  :as content-type
                  :content-type (when (some? form-params) content-type))))

(defn- process-basic-auth
  [{:keys [basic-auth] :as req}]
  (if-let [username (:username basic-auth)
           password (:password basic-auth)]
    (assoc req :basic-auth [username password])
    req))

(defn- run-request
  [{:keys [config] :as http-client} http-method uri req]
  (let [caller (case http-method
                 :get     http.clt/get
                 :head    http.clt/head
                 :post    http.clt/post
                 :put     http.clt/put
                 :delete  http.clt/delete
                 :patch   http.clt/patch
                 :options http.clt/options
                 :copy    http.clt/copy
                 :move    http.clt/move)
        uri'   (str (:host-uri config) uri)
        req'   (-> req
                   (assoc-content-type http-client)
                   (process-basic-auth))
        logger (get-logger http-client)]
    (debug logger ::run-request {:raw-uri       uri
                                 :processed-uri uri'
                                 :request       req'})
    (caller uri' req')))

(defn- get-ring-router
  [{:keys [ring-router] :as component}]
  (or ring-router (rbt.u.c/get-satisfied component trbls.edge.r/RouteMatcher)))

(defn- build-uri
  [component {handler :name params :params}]
  (let [ring-router (get-ring-router component)]
    (if (empty? params)
      (trbls.edge.r/match-by-handler ring-router handler)
      (trbls.edge.r/match-by-handler ring-router handler params))))

(defn- assoc-authz-token
  [req scheme token]
  (assoc-in req [:headers "authorization"] (str scheme " " token)))

(defn- process-tarabulus-token
  [req {:keys [config auth-token-encoder api-token-encoder]} kind]
  (let [token-k (keyword (str "tarabulus-" (e/as-name kind) "-token-auth"))
        encoder (if (= kind :api) api-token-encoder auth-token-encoder)
        token   (let [token (get req token-k)]
                  (if (map? token)
                    (-> token
                        (trbls.data.token/sanitize-claims)
                        (trbls.data.token/assoc-kind kind)
                        (as-> <> (trbls.edge.enc/encode encoder <>)))
                    token))
        scheme  (get-in config [:token-name kind] "TarabulusToken")]
    (if (some? token)
      (-> req
          (dissoc token-k)
          (assoc-authz-token scheme token))
      req)))

(defn- run-request-tarabulus
  [tarabulus-client http-method target req]
  (let [uri    (if (string? target)
                 target
                 (build-uri tarabulus-client target))
        req'   (-> req
                   (process-tarabulus-token tarabulus-client :auth)
                   (process-tarabulus-token tarabulus-client :reset)
                   (process-tarabulus-token tarabulus-client :restore)
                   (process-tarabulus-token tarabulus-client :api))
        logger (get-logger tarabulus-client)]
    (catching
      (run-request tarabulus-client http-method uri req')
      err
      (do (debug logger ::run-request-tarabulus {:uri uri :request req'})
          (ex-data err)))))

(defrecord HttpClient []
  trbls.edge.clt/TarabulusClient
  (request-condition [component req]
    (let [target {:name ::trbls.rts.meta/anon}]
      (run-request-tarabulus component :get target req)))
  (request-token [component {:keys [target-username target-kind] :as req}]
    (let [target {:name   ::trbls.rts.token/target
                  :params {:username target-username
                           :kind     target-kind}}
          req'   (dissoc req :target-username :target-kind)]
      (run-request-tarabulus component :get target req')))
  (register-user [component req]
    (let [target {:name ::trbls.rts.user/anon}]
      (run-request-tarabulus component :post target req)))
  (login-user [component {:keys [target-username] :as req}]
    (let [target {:name   ::trbls.rts.user/target
                  :params {:username target-username}}
          req'   (dissoc req :target-username)]
      (run-request-tarabulus component :post target req')))
  (delete-user [component {:keys [target-username] :as req}]
    (let [target {:name   ::trbls.rts.user/target
                  :params {:username target-username}}
          req'   (dissoc req :target-username)]
      (run-request-tarabulus component :delete target req')))
  (update-user-password [component {:keys [target-username] :as req}]
    (let [target {:name   ::trbls.rts.user/target
                  :params {:username target-username}}
          req'   (dissoc req :target-username)]
      (run-request-tarabulus component :put target req')))
  (restore-user [component {:keys [target-username] :as req}]
    (let [target {:name   ::trbls.rts.user/restore
                  :params {:username target-username}}
          req'   (dissoc req :target-username)]
      (run-request-tarabulus component :post target req')))
  (reset-user-password [component {:keys [target-username] :as req}]
    (let [target {:name   ::trbls.rts.user/reset
                  :params {:username target-username}}
          req'   (dissoc req :target-username)]
      (run-request-tarabulus component :put target req'))))

(defn new-http-client
  [config]
  (map->HttpClient {:config config}))
