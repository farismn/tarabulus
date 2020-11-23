(ns tarabulus.systems
  (:require
   [aero.core :as aero]
   [com.stuartsierra.component :as c]
   [muuntaja.core :as mtj]
   [rabat.components.hikari-cp :as rbt.c.hikari]
   [rabat.components.http-kit :as rbt.c.httpk]
   [rabat.components.jwt-encoder :as rbt.c.jwt]
   [rabat.components.reitit-http :as rbt.c.reit-h]
   [rabat.components.timbre :as rbt.c.timbre]
   [rabat.edge.logger :refer [log]]
   [rabat.edge.reitit :as rbt.edge.reit]
   [rabat.edge.timbre :as rbt.edge.timbre]
   [rabat.utils.system :as rbt.u.sys]
   [reitit.coercion.malli :as reit.coerce.ml]
   [reitit.http :as reit.h]
   [reitit.http.coercion :as reit.h.coerce]
   [reitit.http.interceptors.exception :as reit.h.icept.ex]
   [reitit.http.interceptors.muuntaja :as reit.h.icept.mtj]
   [reitit.http.interceptors.parameters :as reit.h.icept.params]
   [reitit.interceptor.sieppari :as reit.icept.siepp]
   [simple-cors.reitit.interceptor :as cors :as cors.reit.icept]
   [tarabulus.edge.database.sql]
   [tarabulus.edge.encoder.jwt]
   [tarabulus.routes.meta :as trbls.rts.meta]
   [tarabulus.routes.token :as trbls.rts.token]
   [tarabulus.routes.user :as trbls.rts.user]
   [taoensso.timbre :as timbre]
   [tunis.logger.reitit :as tns.log.reit]))

(def ^:private service-ks
  [:database :auth-token-encoder :api-token-encoder :logger])

(defn- tunis-config
  [logger]
  {:log-fn (fn [{:keys [level throwable message]}]
             (let [data (if (nil? throwable) message throwable)]
               (log logger level data)))})

(defn- make-ring-handler-opts
  [{:keys [config]}]
  {:executor     reit.icept.siepp/executor
   :interceptors [(cors.reit.icept/cors-interceptor config)]})

(defn- make-ring-router-opts
  [{:keys [config logger]}]
  {:data                             {:coercion     reit.coerce.ml/coercion
                                      :muuntaja     mtj/instance
                                      :tunis        (tunis-config logger)
                                      :interceptors [(reit.h.icept.params/parameters-interceptor)
                                                     (reit.h.icept.mtj/format-interceptor)
                                                     (reit.h.icept.ex/exception-interceptor)
                                                     (tns.log.reit/log-request-start-interceptor)
                                                     (tns.log.reit/log-request-params-interceptor)
                                                     (tns.log.reit/log-response-interceptor)
                                                     (reit.h.coerce/coerce-exceptions-interceptor)
                                                     (reit.h.coerce/coerce-request-interceptor)
                                                     (reit.h.coerce/coerce-response-interceptor)]}
   ::reit.h/default-options-endpoint (cors.reit.icept/make-default-options-endpoint (:cors config))})

(defn- make-timbre-println-appender
  [{:keys [config]}]
  (merge (timbre/println-appender config)
         (select-keys config [:min-level])))

(defn- new-app-base-system
  [{:tarabulus/keys [http-server
                     ring-handler-options
                     ring-router-options
                     database
                     auth-token-encoder
                     api-token-encoder
                     logger
                     println-logger]}]
  (let [system (c/system-map
                 :http-server (rbt.c.httpk/new-http-server http-server)
                 :ring-handler (rbt.c.reit-h/new-ring-handler)
                 :ring-router (rbt.c.reit-h/new-ring-router)
                 :ring-handler-options (rbt.c.reit-h/new-ring-options make-ring-handler-opts ring-handler-options)
                 :ring-router-options (rbt.c.reit-h/new-ring-options make-ring-router-opts ring-router-options)
                 :database (rbt.c.hikari/new-hikari-cp database)
                 :auth-token-encoder (rbt.c.jwt/new-jwt-encoder auth-token-encoder)
                 :api-token-encoder (rbt.c.jwt/new-jwt-encoder api-token-encoder)
                 :logger (rbt.c.timbre/new-timbre-logger logger)
                 :println-logger (rbt.c.timbre/new-timbre-appender make-timbre-println-appender println-logger)
                 :meta-routes (rbt.c.reit-h/new-ring-routes trbls.rts.meta/routes)
                 :token-routes (rbt.c.reit-h/new-ring-routes trbls.rts.token/routes)
                 :user-routes (rbt.c.reit-h/new-ring-routes trbls.rts.user/routes))
        deps   (-> {:http-server          [:ring-handler :logger]
                    :ring-handler         [:ring-router :ring-handler-options :logger]
                    :ring-router          [:ring-router-options :logger]
                    :ring-handler-options [:logger]
                    :ring-router-options  [:logger]
                    :database             [:logger]
                    :auth-token-encoder   [:logger]
                    :api-token-encoder    [:logger]}
                   (rbt.u.sys/merge-deps {:meta-routes  service-ks
                                          :token-routes service-ks
                                          :user-routes  service-ks})
                   (rbt.u.sys/inject-satisfying-deps system :ring-router rbt.edge.reit/RingRoutes)
                   (rbt.u.sys/inject-satisfying-deps system :logger rbt.edge.timbre/TimbreAppender))]
    (c/system-using system deps)))

(def ^:private systems-map
  {:app/development new-app-base-system})

(defn new-system
  [source kind]
  (let [profile (keyword (name kind))
        sys-f   (or (get systems-map kind)
                    (throw (ex-info "unsupported system kind" {:kind kind})))
        config  (if (map? source)
                  source
                  (aero/read-config source {:profile profile}))]
    (sys-f config)))
