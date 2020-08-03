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
  (fn [] (::http/service-fn (http/create-servlet (deref service)))))


;; Create the test url generator
(defn url-for [routes]
  "Test url generator."
  (fn  [path & args]
    (apply (route/url-for-routes routes) path args)))
