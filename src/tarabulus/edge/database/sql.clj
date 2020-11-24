(ns tarabulus.edge.database.sql
  (:require
   [hugsql.core :refer [def-db-fns]]
   [migratus.core :as migratus]
   [rabat.components.hikari-cp]
   [taoensso.encore :as e]
   [tarabulus.edge.database :as trbls.edge.db]))

(declare create-db!)
(declare drop-db!)

(def-db-fns "tarabulus/pg.sql")
(def-db-fns "tarabulus/pg/user.sql")

(def ^:private adapter->def-db-name
  {"postgresql" "postgres"})

(defn- pool-spec->db-spec
  [{:keys [adapter database-name username password]}]
  (e/assoc-some {:dbtype adapter
                 :dbname database-name}
                :user username
                :password password))

(defn- pool-spec->def-db-spec
  [{:keys [adapter] :as pool-spec}]
  (when-let [database-name (adapter->def-db-name adapter)]
    (pool-spec->db-spec (assoc pool-spec :database-name database-name))))

(extend-protocol trbls.edge.db/UserRepository
  rabat.components.hikari_cp.HikariCP
  (create-user! [component params]
    (create-user! component params))
  (find-user [component params]
    (find-user component params))
  (delete-user! [component params]
    (delete-user! component params))
  (restore-user! [component params]
    (restore-user! component params))
  (reset-user-password! [component params]
    (reset-user-password! component params)))

(extend-protocol trbls.edge.db/Migratable
  rabat.components.hikari_cp.HikariCP
  (migrate! [{:keys [migration-settings] :as migratable}]
    (migratus/migrate (assoc migration-settings :db migratable)))
  (rollback! [{:keys [migration-settings] :as migratable}]
    (migratus/rollback (assoc migration-settings :db migratable))))

(extend-protocol trbls.edge.db/Creatable
  rabat.components.hikari_cp.HikariCP
  (create! [{:keys [pool-spec]}]
    (when-let [db-spec (pool-spec->def-db-spec pool-spec)]
      (create-db! db-spec pool-spec {} {:transaction? false})))
  (drop! [{:keys [pool-spec]}]
    (when-let [db-spec (pool-spec->def-db-spec pool-spec)]
      (drop-db! db-spec pool-spec {} {:transaction? false}))))
