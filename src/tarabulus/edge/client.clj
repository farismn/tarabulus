(ns tarabulus.edge.client)

(defprotocol TarabulusClient
  (request-condition [rabat-client req])
  (request-token [rabat-client req])
  (register-user [rabat-client req])
  (login-user [rabat-client req])
  (delete-user [rabat-client req])
  (update-user-password [rabat-client req])
  (restore-user [rabat-client req])
  (reset-user-password [rabat-client req]))
