(ns tarabulus.routes.meta-test
  (:require
   [clojure.test :as t]
   [expectations.clojure.test :refer [defexpect expecting expect]]
   [tarabulus.edge.client :as trbls.edge.clt]
   [tarabulus.test.fixtures :as trbls.t.fxt]
   [rabat.utils.test :as rbt.u.t]
   [ring.util.http-predicates :as http.pred]))

(t/use-fixtures :once
  (trbls.t.fxt/database-lifecycle :db/test)
  (rbt.u.t/with-system-fixture #(trbls.t.fxt/new-system :app/test)))

(defexpect request-condition-test
  (expecting "condition fetched"
    (let [trbls-client (rbt.u.t/get-component! :tarabulus-client)]
      (expect http.pred/ok?
        (trbls.edge.clt/request-condition trbls-client {})))))
