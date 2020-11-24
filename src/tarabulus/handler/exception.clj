(ns tarabulus.handler.exception
  (:require
   [ring.util.http-response :as http.res]))

(defn clj-ex-handler
  [ex]
  (let [data (ex-data ex)]
    (if (= (:kind data) :tarabulus/exception)
      (case (:category data)
        :unavailable (http.res/service-unavailable (:body data))
        :interrupted (http.res/internal-server-error (:body data))
        :incorrect   (http.res/bad-request (:body data))
        :anonymous   (http.res/unauthorized (:body data))
        :forbidden   (http.res/forbidden (:body data))
        :unsupported (http.res/bad-request (:body data))
        :not-found   (http.res/not-found (:body data))
        :conflict    (http.res/conflict (:body data))
        :fault       (http.res/internal-server-error (:body data))
        :busy        (http.res/service-unavailable (:body data))
        (http.res/internal-server-error {:error {:message "unknown error"}}))
      (http.res/internal-server-error {:error {:message "unknown error"}}))))

(defn pg-ex-handler
  [ex]
  (case (.getSQLState (.getServerErrorMessage ex))
    "23505" (http.res/conflict {:error {:message "unique constraint"}})
    (http.res/internal-server-error {:error {:message "unknown error"}})))
