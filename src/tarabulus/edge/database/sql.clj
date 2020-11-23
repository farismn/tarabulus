(ns tarabulus.edge.database.sql
  (:require
   [hugsql.core :refer [def-db-fns]]
   [rabat.components.hikari-cp]
   [tarabulus.edge.database :as trbls.edge.db]))

(def-db-fns "tarabulus/pg/user.sql")

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
