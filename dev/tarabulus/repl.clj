(ns tarabulus.repl
  (:require
   [clojure.java.io :as io]
   [com.stuartsierra.component.repl :as c.repl]
   [tarabulus.edge.sql.data]
   [tarabulus.systems :as trbls.sys]))

(defn- new-system
  ([]
   (new-system :app/development))
  ([kind]
   (let [source (io/resource "tarabulus/config.edn")]
     (trbls.sys/new-system source kind))))

(defn get-component!
  [k]
  (or (get c.repl/system k)
      (throw (ex-info "missing component" {:component k}))))

(comment

  (c.repl/set-init (fn [_] (new-system)))

  (c.repl/start)

  (c.repl/stop)

  )
