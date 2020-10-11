(ns camposonico-backend.ws-endpoints.core
  (:require [camposonico-backend.ws-endpoints.chat
             :refer
             [new-ws-client on-text send-message-to-all! ws-clients]]
            [camposonico-backend.ws-endpoints.collab
             :refer
             [on-connect new-ws-collab-client on-collab-text remove-closed-clients!]]
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
   "/collab" {:on-connect (ws/start-ws-connection #'on-connect)
              :on-text #'on-collab-text
              :on-binary (fn [payload offset length] (log/info :msg "Binary Message!" :bytes payload))
              :on-error (fn [t] (log/error :msg "WS Error happened" :exception t))
              :on-close (fn [num-code reason-text]
                          (log/info :msg "WS Closed:" :reason reason-text)
                          (remove-closed-clients!))}})
