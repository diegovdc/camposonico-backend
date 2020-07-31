(ns camposonico-backend.utils
  (:require [clojure.spec.alpha :as s]))

(defn try-or-throw
  ([f message data] (try-or-throw f message (constantly nil) data))
  ([f message on-error-fn data]
   (try (f data)
        (catch Exception e
          (throw (ex-info message
                          (merge (on-error-fn data)
                                 {:message message})))))))

(defn validate-spec [spec val error-msg]
  (if (s/valid? spec val) val
      (throw (ex-info error-msg {spec val :message error-msg}))))

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok       (partial response 200))
(def created  (partial response 201))
(def accepted (partial response 202))
