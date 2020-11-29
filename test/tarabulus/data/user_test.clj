(ns tarabulus.data.user-test
  (:require
   [buddy.hashers :as buddy.hash]
   [clojure.test.check.clojure-test :refer [defspec]]
   [com.gfredericks.test.chuck :as t.ck.ch]
   [com.gfredericks.test.chuck.properties :refer [for-all]]
   [malli.generator :as ml.gen]
   [malli.util :as ml.u]
   [tarabulus.data.user :as trbls.data.user]))

(defn- dummy-derive
  [s]
  (str s "!-!"))

(defspec create-user-generates-missing-value
  (t.ck.ch/times 100)
  (for-all [user (-> trbls.data.user/SmartNewUser
                     (ml.u/dissoc :id)
                     (ml.u/dissoc :date-created)
                     (ml.u/dissoc :exist?)
                     (ml.gen/generator))]
    (with-redefs [buddy.hash/derive dummy-derive]
      (let [result (trbls.data.user/create-user user)]
        (and (some? (:id result))
             (some? (:date-created result))
             (some? (:exist? result)))))))

(defspec create-user-process-some-value
  (t.ck.ch/times 100)
  (for-all [user (ml.gen/generator trbls.data.user/SmartNewUser)]
    (with-redefs [buddy.hash/derive dummy-derive]
      (let [result (trbls.data.user/create-user user)]
        (= (:password result) (buddy.hash/derive (:password user)))))))

(defspec authenticate-user-fails-if-no-password
  (t.ck.ch/times 100)
  (for-all [user (-> trbls.data.user/AuthenticableUser
                     (ml.u/dissoc :password)
                     (ml.gen/generator))
            pwd  (ml.gen/generator trbls.data.user/Password)]
    (with-redefs [buddy.hash/check =]
      (nil? (trbls.data.user/authenticate-user user pwd)))))

(defspec authenticate-user-checks-password
  (t.ck.ch/times 100)
  (for-all [user (-> trbls.data.user/AuthenticableUser
                     (ml.u/required-keys [:password])
                     (ml.gen/generator))
            pwd  (ml.gen/generator trbls.data.user/Password)]
    (with-redefs [buddy.hash/check =]
      (let [user' (assoc user :password pwd)]
        (= (trbls.data.user/authenticate-user user' pwd) user')))))
