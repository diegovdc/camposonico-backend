(ns camposonico-backend.ws-endpoints.core
  (:require [camposonico-backend.ws-endpoints.chat
             :refer
             [ws-clients new-ws-client on-text send-message-to-all!]]
            [camposonico-backend.ws-endpoints.collab
             :refer
             [new-ws-collab-client on-collab-text]]
            [clojure.core.async :as async]
            [io.pedestal.http.jetty.websockets :as ws]
            [io.pedestal.log :as log]))

(defn keep-alive* []
  (send-message-to-all! (str {:type :ping})))

(defonce keep-alive! (memoize (fn []
                           (async/go-loop []
                             (async/<! (async/timeout 10000))
                             (keep-alive*)
                             (recur)))))

;; FIXME How to start this?
(keep-alive!)

(def ws-paths
  {"/ws" {:on-connect (ws/start-ws-connection
                       (fn [ws-session send-ch]
                         (new-ws-client ws-session send-ch)))
          :on-text #'on-text
          :on-binary (fn [payload offset length] (log/info :msg "Binary Message!" :bytes payload))
          :on-error (fn [t] (log/error :msg "WS Error happened" :exception t))
          :on-close (fn [num-code reason-text]
                      (log/info :msg "WS Closed:" :reason reason-text)
                      (swap! ws-clients #(filter (fn [[client _]] (.isOpen client)))))}
   "/collab" {:on-connect (ws/start-ws-connection
                           (fn [ws-session send-ch]
                             (new-ws-collab-client ws-session send-ch)))
              :on-text #'on-collab-text
              :on-binary (fn [payload offset length] (log/info :msg "Binary Message!" :bytes payload))
              :on-error (fn [t] (log/error :msg "WS Error happened" :exception t))
              :on-close (fn [num-code reason-text]
                          (log/info :msg "WS Closed:" :reason reason-text)
                          (swap! ws-clients #(filter (fn [[client _]] (.isOpen client)))))}})