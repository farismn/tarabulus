-- :name- create-user! :<! :1
-- :doc create a new user and returns it
INSERT INTO
   users (id, username, password, date_created, is_exist)
VALUES
  (:id, :username, :password, :date-created, :exist?)
RETURNING
  *

-- :name- find-user :? :1
-- :doc find user with matching username
SELECT * FROM
  users
WHERE
  username = :username AND is_exist = TRUE
LIMIT
  1

-- :name- delete-user! :! :n
-- :doc delete user with matching username
UPDATE users SET
  is_exist = FALSE
WHERE
  username = :username AND is_exist = TRUE

-- :name- restore-user! :<! :1
-- :doc restore deleted user with matching username
UPDATE users SET
  is_exist = TRUE
WHERE
  username = :username AND is_exist = FALSE
RETURNING
  *

-- :name- reset-user-password! :<! :1
-- :doc reset user's password with matching username
UPDATE users SET
  password = :new-password
WHERE
  username = :username AND is_exist = TRUE
RETURNING
  *
