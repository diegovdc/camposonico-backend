(ns camposonico-backend.endpoints.histories.create-history
  (:require [camposonico-backend.utils :refer [try-or-throw validate-spec created]]
            [camposonico-backend.env :refer [db-url]]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor.error :as error]
            [camposonico-backend.endpoints.histories.spec :as hspec]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [io.pedestal.log :as log]))

(s/def ::title (s/nilable (s/and string? #(<= (count %) 50))))
(s/def ::author (s/nilable (s/and string? #(<= (count %) 15))))
(s/def ::tags (s/nilable (s/coll-of (s/and string? #(<= (count %) 20)))))

(defn validate-input-enter
  [ctx]
  (let [{:keys [history author title tags]} (-> ctx :request :json-params)
        tags* (some-> tags (str/split #",") (->> (map str/trim)))
        parsed-history (try-or-throw (comp walk/keywordize-keys json/read-str) "History could not be parsed" history)]
    (do (validate-spec ::author author "Author name must not exceed 15 characters in length")
        (validate-spec ::title title "Title must not exceed 50 characters in length")
        (validate-spec ::tags tags* "Tags must be strings separated by commas (\",\"), and each one must not exceed 20 characters")
        (validate-spec ::hspec/history parsed-history "History is invalid"))
    (assoc ctx ::data-to-insert {:author author
                                 :tags tags*
                                 :title title
                                 :history history})))
(def validate-input
  {:name ::validate-input
   :enter #(#'validate-input-enter %)})

(def insert-history
  {:name ::insert-history
   :enter (fn [ctx]
            (jdbc/insert! db-url :histories (-> ctx ::data-to-insert))
            (assoc ctx :response (created nil)))})


(comment (insert-history {:json-params {:author nil :history "\"Hello \""}}))

(def on-history-create-error
  (error/error-dispatch [ctx ex]
   [{:exception-type :org.postgresql.util.PSQLException :interceptor ::insert-history}]
   (do (log/error :exception ex)
       (assoc ctx :response {:status 500 :body "Could not save history"}))
   [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::validate-input}]
   (assoc ctx :response {:status 422 :body (:message (ex-data ex))})
   :else (assoc ctx ::chain/error ex)))

(def create-history [on-history-create-error
                     (body-params/body-params)
                     validate-input
                     insert-history])
