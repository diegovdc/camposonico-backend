(ns camposonico-backend.endpoints.collab-history.get-collab-history
  (:require [camposonico-backend.ws-endpoints.collab :refer [collab-data]]
            [clojure.core.async :as a]
            [camposonico-backend.utils :refer [java-date->iso-string ok not-found]]
            [clojure.data.json :as json]))

(comment (require '[clj-utils.core :refer [spy]]))



(def get-history*
  {:name ::get-history
   :enter (fn [ctx]
            (let [session-id (-> ctx :request :path-params :id read-string)
                  editor-id 1 ;; TODO don't hardcode this
                  data (-> @collab-data :editor-histories
                           (get-in [session-id editor-id]))]
              (if data
                (assoc ctx :response (ok (json/write-str data)))
                (assoc ctx :response (not-found "Could not find history")))))})

(def get-history [#'get-history*])

(comment
  (-> {:request {:path-params {:id "43"}}}
      ((get-history* :enter))
      a/<!!
      :response
      :body
      json/read-str))

(def get-collab-history [#'get-history*])
