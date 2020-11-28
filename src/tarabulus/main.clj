(ns tarabulus.main
  (:gen-class)
  (:require
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]]
   [com.stuartsierra.component :as c]
   [tarabulus.edge.database :as trbls.edge.db]
   [tarabulus.edge.sql.data]
   [tarabulus.systems :as trbls.sys]))

(def ^:private cli-opts
  [["-o" "--operation OPERATION" "Type of program to run"
    :parse-fn keyword
    :validate [#{:migrate :server :rollback}]
    :default :server]
   ["-p" "--profile PROFILE" "Profile used to run the program"
    :parse-fn keyword
    :validate [#{:production :development}]
    :default :development]
   ["-h" "--help"]])

(defn- usage
  [opts-summary]
  (->> ["Tarabulus. A simple http authentication server."
        ""
        "Usage: program-name [options] <config-path>"
        ""
        "Options:"
        opts-summary
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn- error-message
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn- parse-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-opts)]
    (cond
      (some? (:help options))
      {:exit-message (usage summary) :ok? true}

      (seq errors)
      {:exit-message (error-message errors) :ok? false}

      (= 1 (count arguments))
      {:path [(first arguments)] :options options}

      :else
      {:exit-message (usage summary)})))

(defn- exit
  [status msg]
  (println msg)
  (System/exit status))

(defn- add-shutdown-hook!
  [f]
  (.addShutdownHook
    (Runtime/getRuntime)
    (Thread. f)))

(defn -main
  [& args]
  (let [{:keys [path options exit-message ok?]} (parse-args args)]
    (if (some? exit-message)
      (exit (if ok? 0 1) exit-message)
      (case (:operation options)
        :server   (let [kind   (keyword "app" (:profile options))
                        system (c/start (trbls.sys/new-system path kind))]
                    (add-shutdown-hook! #(c/stop system)))
        :migrate  (let [kind   (keyword "db" (:profile options))
                        system (c/start (trbls.sys/new-system path kind))]
                    (trbls.edge.db/migrate! (:database system))
                    (c/stop system)
                    (System/exit 0))
        :rollback (let [kind   (keyword "db" (:profile options))
                        system (c/start (trbls.sys/new-system path kind))]
                    (trbls.edge.db/rollback! (:database system))
                    (c/stop system)
                    (System/exit 0))))))
