(ns tarabulus.edge.database
  (:require
   [buddy.hashers :as buddy.hash]
   [tarabulus.data.user :as trbls.data.user]))

(defprotocol Migratable
  (migrate! [migratable])
  (rollback! [migratable]))

(defprotocol Creatable
  (create! [creatable])
  (drop! [creatable]))

(defprotocol UserRepository
  (create-user! [user-database new-user])
  (find-user [user-database username])
  (delete-user! [user-database username])
  (restore-user! [user-database username])
  (reset-user-password! [user-database username new-password]))

(defn add-user!
  [user-repo new-user]
  (create-user! user-repo (trbls.data.user/create-user new-user)))

(defn login-user
  [user-repo username password]
  (when-let [user (find-user user-repo username)]
    (trbls.data.user/authenticate-user user password)))

(defn set-user-password!
  [user-repo username new-password]
  (let [new-password' (buddy.hash/derive new-password)]
    (reset-user-password! user-repo username new-password')))

(defn update-user-password!
  [user-repo username new-password old-password]
  (when (some? (login-user user-repo username old-password))
    (set-user-password! user-repo username new-password)))
