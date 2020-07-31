(ns jetty-web-sockets.test-utils
  (:require [jetty-web-sockets.utils :as sut]
            [clojure.test :as t]
            [clojure.test :refer :all]
            [io.pedestal.http :as http]
            [clojure.data.json :as json]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :refer :all]
            [jetty-web-sockets.endpoints.freesound :as freesound]
            [jetty-web-sockets.service :as service]))


(defn make-service [service]
  (fn [] (::http/service-fn (http/create-servlet service))))


;; Create the test url generator
(defn url-for [routes path]
  "Test url generator."
  ((route/url-for-routes routes) path))
