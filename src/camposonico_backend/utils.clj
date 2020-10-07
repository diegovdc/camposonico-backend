(ns camposonico-backend.utils
  (:require [clojure.spec.alpha :as s])
  (:import java.text.SimpleDateFormat
           java.util.TimeZone))

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
(def not-found (partial response 404))

(def iso-formater (let [sdf (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")]
                    (.setTimeZone sdf (TimeZone/getTimeZone "z"))
                    sdf))
(defn java-date->iso-string [date]
  (.format iso-formater date))

(defn now
  "Returns the current time in ms"
  []
  (System/currentTimeMillis))
