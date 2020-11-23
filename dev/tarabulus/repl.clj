(ns tarabulus.repl
  (:require
   [clojure.java.io :as io]
   [com.stuartsierra.component.repl :as c.repl]
   [tarabulus.edge.sql]
   [tarabulus.systems :as trbls.sys]))

(defn- new-system
  ([]
   (new-system :app/development))
  ([kind]
   (let [source (io/resource "tarabulus/config.edn")]
     (trbls.sys/new-system source kind))))

(comment

  (c.repl/set-init (fn [_] (new-system)))

  (c.repl/start)

  (c.repl/stop)

  )
