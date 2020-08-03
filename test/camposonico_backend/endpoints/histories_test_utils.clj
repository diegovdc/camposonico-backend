(ns camposonico-backend.endpoints.histories-test-utils
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.walk :as walk]))

(comment (require '[clj-utils.core :refer [spy]]))

(defonce get-sample-history
  (memoize (fn [] (:body (http/get "https://gist.githubusercontent.com/diegovdc/5354a0f51e47680384bbf75c8d13c510/raw/6308ed271ca6aac59981746775eb97df9109446b/postcard.json")))))

(def history (-> (get-sample-history) json/read-str walk/keywordize-keys))

(def history-json (get-sample-history))
