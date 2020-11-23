(ns tarabulus.edge.database)

(defprotocol UserRepository
  (create-user! [user-database params])
  (find-user [user-database params])
  (delete-user! [user-database params])
  (restore-user! [user-database params])
  (reset-user-password! [user-database params]))
