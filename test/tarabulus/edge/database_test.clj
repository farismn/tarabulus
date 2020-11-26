(ns tarabulus.edge.database-test
  (:require
   [clojure.test :as t]
   [clojure.test.check.generators :as t.ch.gen]
   [expectations.clojure.test :refer [defexpect expecting expect in]]
   [malli.generator :as ml.gen]
   [malli.util :as ml.u]
   [rabat.utils.test :as rbt.u.t]
   [tarabulus.data.user :as trbls.data.user]
   [tarabulus.edge.database :as trbls.edge.db]
   [tarabulus.test.fixtures :as trbls.t.fxt]))

(t/use-fixtures :once
  (trbls.t.fxt/database-lifecycle :db/test)
  (rbt.u.t/with-system-fixture #(trbls.t.fxt/new-system :db/test))
  trbls.t.fxt/database-migration)

(defn- generate-schema
  ([schema]
   (t.ch.gen/generate (ml.gen/generator schema)))
  ([schema opts]
   (t.ch.gen/generate (ml.gen/generator schema opts))))

(defn- assoc-gen-in
  [schema ks gen]
  (ml.u/update-in schema ks ml.u/update-properties assoc :gen/gen gen))

(def ExistUser
  (assoc-gen-in trbls.data.user/User [:exist?] (t.ch.gen/return true)))

(def NonExistUser
  (assoc-gen-in trbls.data.user/User [:exist?] (t.ch.gen/return false)))

(defexpect create-user!-test
  (expecting "user created"
    (let [db   (rbt.u.t/get-component! :database)
          user (generate-schema trbls.data.user/User)]
      (expect user
        (trbls.edge.db/create-user! db user))
      (expect org.postgresql.util.PSQLException
        (trbls.edge.db/create-user! db user)))))

(defexpect find-user-test
  (expecting "user fetched"
    (let [db       (rbt.u.t/get-component! :database)
          euser    (generate-schema ExistUser)
          neuser   (generate-schema NonExistUser)
          username (generate-schema trbls.data.user/Username)]
      (trbls.edge.db/create-user! db euser)
      (trbls.edge.db/create-user! db neuser)
      (expect nil?
        (trbls.edge.db/find-user db {:username username}))
      (expect nil?
        (trbls.edge.db/find-user db neuser))
      (expect euser
        (trbls.edge.db/find-user db euser)))))

(defexpect delete-user!-test
  (expecting "user deleted"
    (let [db       (rbt.u.t/get-component! :database)
          euser    (generate-schema ExistUser)
          neuser   (generate-schema NonExistUser)
          username (generate-schema trbls.data.user/Username)]
      (trbls.edge.db/create-user! db euser)
      (trbls.edge.db/create-user! db neuser)
      (expect {:database/happened? false}
        (in (trbls.edge.db/delete-user! db {:username username})))
      (expect {:database/happened? false}
        (in (trbls.edge.db/delete-user! db neuser)))
      (expect {:database/happened? true}
        (in (trbls.edge.db/delete-user! db euser)))
      (expect {:database/happened? false}
        (in (trbls.edge.db/delete-user! db euser))))))

(defexpect restore-user!-test
  (expecting "user restored"
    (let [db       (rbt.u.t/get-component! :database)
          euser    (generate-schema ExistUser)
          neuser   (generate-schema NonExistUser)
          username (generate-schema trbls.data.user/Username)]
      (trbls.edge.db/create-user! db euser)
      (trbls.edge.db/create-user! db neuser)
      (trbls.edge.db/delete-user! db euser)
      (expect nil?
        (trbls.edge.db/restore-user! db {:username username}))
      (expect (assoc neuser :exist? true)
        (trbls.edge.db/restore-user! db neuser))
      (expect euser
        (trbls.edge.db/restore-user! db euser))
      (expect nil?
        (trbls.edge.db/restore-user! db euser)))))

(defexpect reset-user-password!-test
  (expecting "user's password changed"
    (let [db       (rbt.u.t/get-component! :database)
          euser    (generate-schema ExistUser)
          neuser   (generate-schema NonExistUser)
          username (generate-schema trbls.data.user/Username)
          password (generate-schema trbls.data.user/Password)]
      (trbls.edge.db/create-user! db euser)
      (trbls.edge.db/create-user! db neuser)
      (expect nil?
        (let [params {:username username :new-password password}]
          (trbls.edge.db/reset-user-password! db params)))
      (expect nil?
        (let [params (assoc neuser :new-password password)]
          (trbls.edge.db/reset-user-password! db params)))
      (expect (assoc euser :password password)
        (let [params (assoc euser :new-password password)]
          (trbls.edge.db/reset-user-password! db params))))))
