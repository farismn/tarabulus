(ns tarabulus.routes.token-test
  (:require
   [clojure.test :as t]
   [clojure.test.check.generators :as t.ch.gen]
   [expectations.clojure.test :refer [defexpect expecting expect from-each]]
   [malli.core :as ml]
   [malli.generator :as ml.gen]
   [rabat.utils.test :as rbt.u.t]
   [tarabulus.data.token :as trbls.data.token]
   [tarabulus.data.user :as trbls.data.user]
   [tarabulus.edge.client :as trbls.edge.clt]
   [tarabulus.routes.token :as trbls.rts.token]
   [tarabulus.test.fixtures :as trbls.t.fxt]
   [ring.util.http-predicates :as http.pred]))

(t/use-fixtures :once
  (trbls.t.fxt/database-lifecycle :db/test)
  (rbt.u.t/with-system-fixture #(trbls.t.fxt/new-system :app/test)))

(def ^:private acceptable-kinds
  (into []
        (comp
          (filter keyword?)
          (filter #(not= % :enum)))
        (ml/form trbls.rts.token/AcceptedKinds)))

(defn- generate-schema
  ([schema]
   (t.ch.gen/generate (ml.gen/generator schema)))
  ([schema opts]
   (t.ch.gen/generate (ml.gen/generator schema opts))))

(defexpect request-token-test
  (expecting "token fetched"
    (let [trbls-client (rbt.u.t/get-component! :tarabulus-client)
          username     (generate-schema trbls.data.user/Username)
          run-api      #(trbls.edge.clt/request-token trbls-client %)]
      (expect http.pred/bad-request?
        (let [kind (generate-schema trbls.data.token/Kind)]
          (run-api {:target-username username
                    :target-kind     kind})))
      (expect http.pred/unauthorized?
        (from-each [kind acceptable-kinds]
          (run-api {:target-username username
                    :target-kind     kind})))
      (expect http.pred/forbidden?
        (from-each [kind acceptable-kinds]
          (let [username' (generate-schema trbls.data.user/Username)
                auth      {:username username'}]
            (run-api {:target-username          username
                      :target-kind              kind
                      :tarabulus-api-token-auth auth}))))
      (expect http.pred/ok?
        (from-each [kind acceptable-kinds]
          (let [auth {:username username}]
            (run-api {:target-username          username
                      :target-kind              kind
                      :tarabulus-api-token-auth auth})))))))
