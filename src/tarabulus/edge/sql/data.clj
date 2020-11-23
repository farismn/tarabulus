(ns tarabulus.edge.sql.data
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [clj-time.jdbc]
   [clojure.java.jdbc :as jdbc]
   [clojure.set :as set]
   [hugsql.adapter]
   [hugsql.core :as hug]))

(extend-protocol jdbc/IResultSetReadColumn
  org.postgresql.util.PGobject
  (result-set-read-column [this _ _]
    (let [kind  (.getType this)
          value (.getValue this)]
      (case kind
        "citext" (str value)
        :else    value))))

(def ^:private app-key->sql-key
  {:exist? :is-exist})

(def ^:private sql-key->app-key
  (into {} (map (fn [[k v]] [v k])) app-key->sql-key))

(defn result-one-snake->kebab
  [this result options]
  (-> (hugsql.adapter/result-one this result options)
      (as-> <> (cske/transform-keys csk/->kebab-case-keyword <>))
      (set/rename-keys sql-key->app-key)))

(defn result-many-snake->kebab
  [this result options]
  (into []
        (comp
          (map (partial cske/transform-keys csk/->kebab-case-keyword))
          (map #(set/rename-keys % sql-key->app-key)))
        (hugsql.adapter/result-many this result options)))

(defn result-affected-wrapped
  [this result options]
  (let [result' (hugsql.adapter/result-affected this result options)]
    {:database/happened? (pos-int? result')
     :database/result    result'}))

(defmethod hug/hugsql-result-fn :1
  [_]
  'tarabulus.edge.sql.data/result-one-snake->kebab)

(defmethod hug/hugsql-result-fn :one
  [_]
  'tarabulus.edge.sql.data/result-one-snake->kebab)

(defmethod hug/hugsql-result-fn :*
  [_]
  'tarabulus.edge.sql.data/result-many-snake->kebab)

(defmethod hug/hugsql-result-fn :many
  [_]
  'tarabulus.edge.sql.data/result-many-snake->kebab)

(defmethod hug/hugsql-result-fn :n
  [_]
  'tarabulus.edge.sql.data/result-affected-wrapped)

(defmethod hug/hugsql-result-fn :affected
  [_]
  'tarabulus.edge.sql.data/result-affected-wrapped)
