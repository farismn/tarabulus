(ns tarabulus.routes.meta
  (:require
   [ring.util.http-response :as http.res]
   [ring.util.http-status :as http.sta]))

(defn routes
  [_component]
  [["/api/meta"
    {:name ::anon
     :get  {:responses {http.sta/ok {:body [:map
                                            [:meta
                                             [:map
                                              [:condition
                                               [:map
                                                [:name string?]
                                                [:healthy? boolean?]]]]]]}}
            :handler   (constantly
                         (http.res/ok {:meta {:condition {:name     "tarabulus"
                                                          :healthy? true}}}))}}]])
