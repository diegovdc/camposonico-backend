(ns camposonico-backend.endpoints.histories.core
  (:require [camposonico-backend.utils :refer [try-or-throw validate-spec created]]
            [camposonico-backend.env :refer [db-url]]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor.error :as error]))

(s/def ::author (s/nilable (s/and string? #(<= (count %) 15))))
(s/def ::history string?)

(def validate-input
  {:name ::validate-input
   :enter
   (fn [ctx]
     (let [{:keys [history author]} (-> ctx :request :json-params)
           parsed-history (try-or-throw json/read-str "History is invalid" history)]
       (do (validate-spec ::author author "Author name must not exceed 15 characters in length")
           ;; TODO validate history correctly
           (validate-spec ::history parsed-history "History is invalid"))
       ctx))})

(def insert-history
  {:name ::insert-history
   :enter
   (fn [ctx]
     (let [{:keys [author history]} (-> ctx :request :json-params)]
       (jdbc/insert! db-url :histories {:author author
                                        :history history})
       (assoc ctx :response (created nil))))})


(comment (insert-history {:json-params {:author nil :history "\"Hello \""}}))

(def on-history-create-error
  (error/error-dispatch
   [ctx ex]
   [{:exception-type :org.postgresql.util.PSQLException :interceptor ::insert-history}]
   (assoc ctx :response {:status 500 :body "Could not save history"})
   [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::validate-input}]
   (assoc ctx :response {:status 422 :body (:message (ex-data ex))})
   :else (assoc ctx ::chain/error ex)))

(def create-history [on-history-create-error
                     (body-params/body-params)
                     validate-input
                     insert-history])
