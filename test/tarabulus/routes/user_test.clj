(ns tarabulus.routes.user-test
  (:require
   [clojure.test :as t]
   [clojure.test.check.generators :as t.ch.gen]
   [expectations.clojure.test :refer [defexpect expecting expect]]
   [malli.generator :as ml.gen]
   [rabat.utils.test :as rbt.u.t]
   [tarabulus.data.user :as trbls.data.user]
   [tarabulus.edge.client :as trbls.edge.clt]
   [tarabulus.routes.user :as trbls.rts.user]
   [tarabulus.test.fixtures :as trbls.t.fxt]
   [ring.util.http-predicates :as http.pred]))

(t/use-fixtures :once
  (trbls.t.fxt/database-lifecycle :db/test)
  (rbt.u.t/with-system-fixture #(trbls.t.fxt/new-system :app/test))
  trbls.t.fxt/database-migration)

(defn- generate-schema
  ([schema]
   (t.ch.gen/generate (ml.gen/generator schema)))
  ([schema opts]
   (t.ch.gen/generate (ml.gen/generator schema opts))))

(defexpect register-user-test
  (expecting "user registered"
    (let [trbls-client (rbt.u.t/get-component! :tarabulus-client)
          registree    (generate-schema trbls.rts.user/Registree)
          run-api      (partial trbls.edge.clt/register-user trbls-client)]
      (expect http.pred/bad-request?
        (run-api {}))
      (expect http.pred/ok?
        (run-api {:form-params {:user {:registree registree}}}))
      (expect http.pred/conflict?
        (run-api {:form-params {:user {:registree registree}}})))))

(defexpect login-user-test
  (expecting "authentication token"
    (let [trbls-client (rbt.u.t/get-component! :tarabulus-client)
          reg          (generate-schema trbls.rts.user/Registree)
          run-api      (partial trbls.edge.clt/login-user trbls-client)]
      (trbls.edge.clt/register-user
        trbls-client
        {:form-params {:user {:registree reg}}})
      (expect http.pred/unauthorized?
        (run-api {:target-username (:username reg)}))
      (expect http.pred/forbidden?
        (let [username (generate-schema trbls.data.user/Username)]
              (run-api {:target-username username
                        :basic-auth      reg})))
      (expect http.pred/ok?
        (run-api {:target-username (:username reg)
                  :basic-auth      reg})))))

(defexpect delete-user-test
  (expecting "user deleted"
    (let [trbls-client (rbt.u.t/get-component! :tarabulus-client)
          reg          (generate-schema trbls.rts.user/Registree)
          run-api      (partial trbls.edge.clt/delete-user trbls-client)]
      (trbls.edge.clt/register-user trbls-client {:form-params {:user {:registree reg}}})
      (expect http.pred/unauthorized?
        (run-api {:target-username (:username reg)}))
      (expect http.pred/forbidden?
        (let [username (generate-schema trbls.data.user/Username)]
          (run-api {:target-username           username
                    :tarabulus-auth-token-auth reg})))
      (expect http.pred/no-content?
        (run-api {:target-username           (:username reg)
                  :tarabulus-auth-token-auth reg})))))

(defexpect update-user-password-test
  (expecting "user's password updated"
    (let [ trbls-client (rbt.u.t/get-component! :tarabulus-client)
          reg           (generate-schema trbls.rts.user/Registree)
          pwd-updatee   (generate-schema trbls.rts.user/PasswordUpdatee)
          run-api       (partial trbls.edge.clt/update-user-password  trbls-client )]
      (trbls.edge.clt/register-user  trbls-client  {:form-params {:user {:registree reg}}})
      (expect http.pred/bad-request?
        (run-api {:target-username (:username reg)}))
      (expect http.pred/unauthorized?
        (run-api {:target-username (:username reg)
                  :form-params     {:user {:password-updatee pwd-updatee}}}))
      (expect http.pred/forbidden?
        (let [username    (generate-schema trbls.data.user/Username)
              form-params {:user {:password-updatee pwd-updatee}}]
          (run-api {:target-username           username
                    :form-params               form-params
                    :tarabulus-auth-token-auth reg})))
      (expect http.pred/bad-request?
        (let [username    (:username reg)
              form-params {:user {:password-updatee pwd-updatee}}]
          (run-api {:target-username           username
                    :form-params               form-params
                    :tarabulus-auth-token-auth reg})))
      (expect http.pred/ok?
        (let [pwd-updatee' (assoc pwd-updatee :old-password (:password reg))
              form-params  {:user {:password-updatee pwd-updatee'}}]
          (run-api {:target-username           (:username reg)
                    :form-params               form-params
                    :tarabulus-auth-token-auth reg}))))))

(defexpect restore-user-test
  (expecting "user restored"
    (let [trbls-client (rbt.u.t/get-component! :tarabulus-client)
          reg          (generate-schema trbls.rts.user/Registree)
          run-api      (partial trbls.edge.clt/restore-user trbls-client)]
      (trbls.edge.clt/register-user trbls-client {:form-params {:user {:registree reg}}})
      (trbls.edge.clt/delete-user trbls-client {:target-username           (:username reg)
                                                :tarabulus-auth-token-auth reg})
      (expect http.pred/unauthorized?
        (run-api {:target-username (:username reg)}))
      (expect http.pred/forbidden?
        (let [username (generate-schema trbls.data.user/Username)]
          (run-api {:target-username              username
                    :tarabulus-restore-token-auth reg})))
      (expect http.pred/ok?
        (run-api {:target-username              (:username reg)
                  :tarabulus-restore-token-auth reg}))
      (expect http.pred/bad-request?
        (run-api {:target-username              (:username reg)
                  :tarabulus-restore-token-auth reg})))))

(defexpect reset-user-password-test
  (expecting "user's password updated"
    (let [trbls-client (rbt.u.t/get-component! :tarabulus-client)
          reg          (generate-schema trbls.rts.user/Registree)
          pwd-resetee  (generate-schema trbls.rts.user/PasswordResetee)
          run-api      (partial trbls.edge.clt/reset-user-password  trbls-client )]
      (trbls.edge.clt/register-user trbls-client {:form-params {:user {:registree reg}}})
      (expect http.pred/bad-request?
        (run-api {:target-username (:username reg)}))
      (expect http.pred/unauthorized?
        (run-api {:target-username (:username reg)
                  :form-params     {:user {:password-resetee pwd-resetee}}}))
      (expect http.pred/forbidden?
        (let [username    (generate-schema trbls.data.user/Username)
              form-params {:user {:password-resetee pwd-resetee}}]
          (run-api {:target-username            username
                    :form-params                form-params
                    :tarabulus-reset-token-auth reg})))
      (expect http.pred/ok?
        (let [form-params {:user {:password-resetee pwd-resetee}}]
          (run-api {:target-username            (:username reg)
                    :form-params                form-params
                    :tarabulus-reset-token-auth reg}))))))
