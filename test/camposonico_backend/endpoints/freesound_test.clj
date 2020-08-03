(ns camposonico-backend.endpoints.freesound-test
  (:require [camposonico-backend.endpoints.freesound :as freesound]
            [camposonico-backend.service :as service]
            [camposonico-backend.test-utils :refer [make-service url-for]]
            [clojure.data.json :as json]
            [clojure.test :refer [deftest is]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :refer [response-for]]))

(comment (require '[clj-utils.core :refer [spy]]))

(def service (make-service #'service/service))

(def url-for* (url-for (route/expand-routes (service/routes))))

(deftest freesound-get-sounds-test
  (let [res (response-for (service)
                          :get (url-for* ::service/get-sounds
                                         :query-params {:query "ocean" :page 1}))]
    (is (= 200 (:status res)))
    (is (= #{"count" "next" "results" "previous"}
           (-> res :body json/read-str keys set)))))
