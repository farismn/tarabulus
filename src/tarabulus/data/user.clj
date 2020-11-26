(ns tarabulus.data.user
  (:require
   [buddy.hashers :as buddy.hash]
   [clj-time.core :as time]
   [clj-time.spec :as time.s]
   [clj-time.types :as time.types]
   [clojure.spec.alpha :as s]
   [clojure.test.check.generators :as t.gen]
   [com.gfredericks.test.chuck.generators :as t.gen']
   [malli.util :as ml.u]
   [taoensso.encore :as e :refer [catching]]))

(def Id
  [uuid? {:gen/gen t.gen/uuid}])

(def Username
  (letfn [(length-ok?
            [x]
            (<= 4 (count x) 20))]
    [:fn {:gen/gen (t.gen'/string-from-regex #"[a-zA-Z0-9]{4,20}")}
     #(and (string? %) (length-ok? %))]))

(def Password
  [:fn {:gen/gen (t.gen/such-that e/nblank? t.gen/string-alphanumeric)}
   e/nblank-str?])

(def DateCreated
  [:fn {:gen/gen (s/gen ::time.s/date-time)} time.types/date-time?])

(def Exist?
  [boolean? {:gen/gen t.gen/boolean}])

(def User
  [:map
   [:id Id]
   [:username Username]
   [:password Password]
   [:date-created DateCreated]
   [:exist? Exist?]])

(def SmartNewUser
  (ml.u/optional-keys User [:id :date-created :exist?]))

(def AuthenticableUser
  (-> User
      (ml.u/select-keys [:username :password])
      (ml.u/optional-keys [:password])))

(def PasswordResetee
  [:map
   [:username Username]
   [:new-password Password]])

(defn create-user
  [{:keys [id date-created exist?] :as new-user}]
  (cond-> (update new-user :password buddy.hash/derive)
    (nil? id)
    (assoc :id (java.util.UUID/randomUUID))

    (nil? date-created)
    (assoc :date-created (time/now))

    (nil? exist?)
    (assoc :exist? true)))

(defn authenticate-user
  [{:keys [password] :as user} attempt-password]
  (when (catching (buddy.hash/check attempt-password password))
    user))

(defn reset-user-password
  [attrs]
  (update attrs :new-password buddy.hash/derive))
