(ns camposonico-backend.ws-endpoints.collab
  (:require [camposonico-backend.utils :refer [now]]
            [camposonico-backend.ws-endpoints.chat
             :refer
             [send-message-to-client! set-username!]]
            [clojure.core.async :as async]
            [clojure.edn :as edn]
            [io.pedestal.log :as log])
  (:import java.util.UUID
           org.eclipse.jetty.websocket.api.Session))

(defonce collab-data (atom {:clients {}
                            :editor-histories {}}))

(defn new-ws-collab-client
  [ws-session send-ch]
  #_(async/put! send-ch "New collab client")
  (let [id (.toString (java.util.UUID/randomUUID))]
    (swap! collab-data assoc-in [:clients id] {:channel send-ch :session ws-session})
    (async/put! send-ch (str {:type :id :msg id}))))

(defn get-collab-clients [collab-data-map]
  (collab-data-map :clients))

(defn send-to-all-except-originator! [{:keys [msg originator-id]}]
  (let [clients (-> @collab-data get-collab-clients (dissoc originator-id))]
    (doseq [[_ {:keys [^Session session channel]}] clients]
      (println "sending collab msg")
      (when (.isOpen session) (async/put! channel (str msg))))))

(defn format-event [{:keys [opts msg]}]
  {:data msg
   :type (opts :type)
   :originator-id (opts :id)
   :editor-id (opts :editor-id)
   :time (now)})

(defn save-collab-typing-event! [{:keys [opts] :as message} session-id]
  (let [event (format-event message)]
    (swap! collab-data update-in [:editor-histories session-id (opts :editor-id)]
           (fn [history]
             (conj (or history []) event)) )
    event))

(defn prepare-collab-typing-msg-broadcast [event session-id]
  {:msg {:type :collab-event-broadcast
         :msg event}
   ;; FIXME remove test thing below
   :originator-id (event :originator-id)})

(defn on-collab-typing-event [msg]
  (println msg)
  ;; TODO figure out session-id
  ;; TODO figure out editor-id management
  (let [session-id 1]
    (-> msg
        (save-collab-typing-event! session-id)
        (prepare-collab-typing-msg-broadcast session-id)
        send-to-all-except-originator!)))

(defn on-collab-text [msg]
  (log/info :msg (str "collab " msg))
  (try (let [msg* (edn/read-string msg)]
         (condp = (:type msg*)
           :pong nil ;; Client is alive.
           :set-username (when-let [{:keys [client response]} (set-username! msg*)]
                           (send-message-to-client! client
                                                    response))
           :collab-event (on-collab-typing-event msg*)
           (log/error "Unknow message type on web-socket" msg)))
       (catch Exception e (log/error "Could not read web-socket message" e)))
  #_(send-message-to-all! (str {:type :default :msg (str "You said: " (:msg  (clojure.edn/read-string msg)))})))
