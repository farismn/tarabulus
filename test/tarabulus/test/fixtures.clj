(ns tarabulus.test.fixtures
  (:require
   [clojure.java.io :as io]
   [rabat.utils.test :as rbt.u.t]
   [tarabulus.edge.database :as trbls.edge.db]
   [tarabulus.edge.sql.data]
   [tarabulus.systems :as trbls.sys]))

(defn new-system
  [kind]
  (let [source (io/resource "tarabulus/config.edn")]
    (trbls.sys/new-system source kind)))

(defn database-lifecycle
  [kind]
  (fn [f]
    (let [db (:database (new-system kind))]
      (trbls.edge.db/drop! db)
      (trbls.edge.db/create! db)
      (f)
      (trbls.edge.db/drop! db))))

(defn database-migration
  [f]
  (let [db (rbt.u.t/get-component! :database)]
    (trbls.edge.db/migrate! db)
    (f)))
