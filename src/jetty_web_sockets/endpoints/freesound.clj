(ns jetty-web-sockets.endpoints.freesound
  (:require [environ.core :refer [env]]
            [clj-http.client :as http]
            [clojure.core.async :as a]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.chain :as chain]
            [clojure.string :as str]
            [io.pedestal.http.route :as route]))

(comment (require '[clj-utils.core :refer [spy]]))

(def api "https://freesound.org/apiv2/search/text")
(def api-token (env :freesound-token))
(def fields "id,username,tags,previews,url,description,duration,name")

(defn process-query [query]
  (-> query (str/split #" ")
      (->> (remove empty?)
           (str/join "+"))))

(def make-query
  {:name ::make-query
   :enter
   (fn [ctx]
     (let [result-chan (a/chan)
           {:keys [query page]} (-> ctx :request :query-params)]
       (http/get api
                 {:async? true
                  :accept :json
                  :query-params {"token" api-token
                                 "query" (process-query query)
                                 "page" page
                                 "fields" fields}}
                 (fn [data] (a/>!! result-chan
                                  (assoc ctx :response {:status 200
                                                        :body (data :body)})))
                 (fn [error] (a/>!! result-chan (assoc ctx ::chain/error error))))
       result-chan))})

(comment
  (a/<!! ((make-query :enter) {:request {:query-params {:query"ocean field recording" :page 1000}}})))

(def get-sounds [(body-params/body-params) make-query])

(def routes (route/expand-routes #{["/freesound/" :get get-sounds :route-name ::get-sounds]}))
