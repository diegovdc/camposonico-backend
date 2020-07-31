(ns camposonico-backend.endpoints.histories.core-test
  (:require [camposonico-backend.service :as service]
            [camposonico-backend.test-utils :refer [make-service url-for]]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [io.pedestal.test :refer :all]))

(def service (make-service service/service))

(def url-for* (partial url-for service/routes))

(deftest histories-test
  (testing "Correct input"
    (is (= 201 (:status
                (response-for (service)
                              :post (url-for* ::service/create-history)
                              ;; Set the `Content-Type` so `body-params`
                              ;; can parse the body
                              :headers {"Content-Type" "application/json"}
                              ;; Encode the payload
                              :body (json/write-str {:author nil :history "\"Hello World\""}))))))

  (testing "`nil` history"
    (is (= [422 "History is invalid"]
           ((juxt :status :body) (response-for (service)
                                               :post (url-for* ::service/create-history)
                                               ;; Set the `Content-Type` so `body-params`
                                               ;; can parse the body
                                               :headers {"Content-Type" "application/json"}
                                               ;; Encode the payload
                                               :body (json/write-str {:author nil :history nil}))))))
  (testing "Invalid history value"
    ;; TODO improve this
    (is (= [422 "History is invalid"]
           ((juxt :status :body) (response-for (service)
                                               :post (url-for* ::service/create-history)
                                               ;; Set the `Content-Type` so `body-params`
                                               ;; can parse the body
                                               :headers {"Content-Type" "application/json"}
                                               ;; Encode the payload
                                               :body (json/write-str {:author nil :history "5"}))))))
  (testing "`author` name length"
    (is (= [422 "Author name must not exceed 15 characters in length"]
           ((juxt :status :body )
            (response-for (service)
                          :post (url-for* ::service/create-history)
                          ;; Set the `Content-Type` so `body-params`
                          ;; can parse the body
                          :headers {"Content-Type" "application/json"}
                          ;; Encode the payload
                          :body (json/write-str
                                 {:author "asuperlongstringwhatthefuckfsdflfggggggggggggggggggggggggggggggggggggggggggggggggggggggggg"
                                  :history "\"Hello World\""})))))))
