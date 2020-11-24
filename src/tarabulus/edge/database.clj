(ns tarabulus.edge.database)

(defprotocol Migratable
  (migrate! [migratable])
  (rollback! [migratable]))

(defprotocol Creatable
  (create! [creatable])
  (drop! [creatable]))

(defprotocol UserRepository
  (create-user! [user-database params])
  (find-user [user-database params])
  (delete-user! [user-database params])
  (restore-user! [user-database params])
  (reset-user-password! [user-database params]))
