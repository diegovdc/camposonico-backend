(ns camposonico-backend.service-test
  (:require [camposonico-backend.service :as service]
            [camposonico-backend.test-utils :refer [make-service url-for]]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [io.pedestal.test :refer :all]))

(comment (require '[clj-utils.core :refer [spy]]))

(def service (make-service service/service))

(def url-for* (partial url-for service/routes))

(deftest home-page-test
  (is (=
       (:body (response-for (service) :get "/"))
       "Hello World!")))


(deftest about-page-test
  (is (str/includes?
       (:body (response-for (service) :get "/about"))
       "Clojure 1.10.0")))
