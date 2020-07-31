(ns camposonico-backend.test-utils
  (:require [camposonico-backend.endpoints.freesound :as freesound]
            [camposonico-backend.service :as service]
            [camposonico-backend.utils :as sut]
            [clojure.data.json :as json]
            [clojure.test :as t :refer :all]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :refer :all]))

(defn make-service [service]
  (fn [] (::http/service-fn (http/create-servlet service))))


;; Create the test url generator
(defn url-for [routes path]
  "Test url generator."
  ((route/url-for-routes routes) path))
