(ns jetty-web-sockets.endpoints.freesound-test
  (:require [clojure.test :refer :all]
            [io.pedestal.http :as http]
            [clojure.data.json :as json]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :refer :all]
            [jetty-web-sockets.endpoints.freesound :as freesound]
            [jetty-web-sockets.test-utils :refer [url-for make-service]]
            [jetty-web-sockets.service :as service]))
(comment (require '[clj-utils.core :refer [spy]]))

(def service (make-service (assoc service/service ::http/routes freesound/routes)))

(def url-for* (partial url-for freesound/routes))

(deftest freesound-get-sounds-test
  (let [res (response-for (service)
                          :get (str (url-for* ::freesound/get-sounds)
                                    "?query=ocean&page=1"))]
    (is (= 200 (:status res)))
    (is (= #{"count" "next" "results" "previous"}
           (-> res :body json/read-str keys set)))))
