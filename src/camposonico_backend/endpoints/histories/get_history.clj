(ns camposonico-backend.endpoints.histories.get-history
  (:require [camposonico-backend.env :refer [db-url]]
            [camposonico-backend.utils :refer [java-date->iso-string ok not-found]]
            [clojure.core.async :as a]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor.error :as error]))

(comment (require '[clj-utils.core :refer [spy]]))

(def get-history*
  {:name ::get-history
   :enter (fn [ctx]
            (a/go (let [id (-> ctx :request :path-params :id (str/split #"-")
                               first read-string)
                        get-data  #(some-> (jdbc/get-by-id db-url "histories" id)
                                           (update :created_at java-date->iso-string)
                                           json/write-str)
                        data (when (int? id) (get-data))]
                    (if data
                      (assoc ctx :response (ok data))
                      (assoc ctx :response (not-found "Could not find history"))))))})

(def get-history [#'get-history*])
