(ns tarabulus.data.token-test
  (:require
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.generators :as t.gen]
   [com.gfredericks.test.chuck :as t.ck.ch]
   [com.gfredericks.test.chuck.properties :refer [for-all]]
   [malli.core :as ml]
   [malli.generator :as ml.gen]
   [malli.util :as ml.u]
   [tarabulus.data.token :as trbls.data.token]
   [tarabulus.data.user :as trbls.data.user]))

(defspec sanitize-claims-remove-censored-key
  (t.ck.ch/times 100)
  (for-all [claims (-> trbls.data.token/Claims
                       (ml.u/assoc :password trbls.data.user/Password)
                       (ml.gen/generator))]
    (nil? (:password (:claims (trbls.data.token/sanitize-claims claims))))))

(defspec assoc-kind-as-its-name-implies
  (t.ck.ch/times 100)
  (for-all [claims (ml.gen/generator trbls.data.token/Claims)
            kind   (ml.gen/generator trbls.data.token/Kind)]
    (= (trbls.data.token/assoc-kind claims kind)
       (assoc claims :kind kind))))

(def ^:private RawPayload
  [:map [:kind [:or string? keyword?]]])

(defspec coerce-payload-coerce-some-value
  (t.ck.ch/times 100)
  (for-all [raw-payload (ml.gen/generator RawPayload)]
    (let [result (trbls.data.token/coerce-payload raw-payload)]
      (ml/validate trbls.data.token/Payload result))))

(defspec validate-payload-fails-if-payload-contains-no-kind
  (t.ck.ch/times 100)
  (for-all [payload (t.gen/such-that
                      (fn [m] (nil? (:kind m)))
                      (t.gen/map t.gen/keyword t.gen/any))
            kind    (ml.gen/generator trbls.data.token/Kind)]
    (nil? (trbls.data.token/validate-payload payload kind))))

(defspec validate-payload-fails-if-kind-doesnt-match
  (t.ck.ch/times 100)
  (for-all [payload (ml.gen/generator trbls.data.token/Payload)
            kind    (ml.gen/generator trbls.data.token/Kind)]
    (nil? (trbls.data.token/validate-payload payload kind))))

(defspec validate-payload-success-if-kind-match
  (t.ck.ch/times 100)
  (for-all [payload (ml.gen/generator trbls.data.token/Payload)
            kind    (ml.gen/generator trbls.data.token/Kind)]
    (let [payload' (assoc payload :kind kind)]
      (= (trbls.data.token/validate-payload payload' kind) payload'))))
