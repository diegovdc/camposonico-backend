(ns camposonico-backend.endpoints.histories.get-history-test
  (:require [camposonico-backend.service :as service]
            [camposonico-backend.test-utils :refer [make-service url-for]]
            [camposonico-backend.endpoints.histories-test-utils :as h-test-utils]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http.route :as route]))

(def service (make-service #'service/service))

(def url-for*
  (url-for (route/expand-routes (service/routes))))

(deftest histories-test
  ;; This test might easily break because there might not be a history with an `id`equal to `1`
  (testing "Correct input"
    (is (= [200] ((juxt :status)
                  (response-for
                   (service)
                   :get (url-for* ::service/get-history
                                  :path-params {:id "1-author-title"}))))))
  ;; This test might easily break too
  (testing "Nonexisting id"
    (is (= [404] ((juxt :status)
                  (response-for
                   (service)
                   :get (url-for* ::service/get-history
                                  :path-params {:id "20000000000000-author-title"}))))))
  (testing "Bad id"
    (is (= [404] ((juxt :status)
                  (response-for
                   (service)
                   :get (url-for* ::service/get-history
                                  :path-params {:id "notInt-author-title"})))))))
