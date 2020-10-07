(ns camposonico-backend.ws-endpoints.chat
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
            [io.pedestal.log :as log])
  (:import java.util.UUID
           org.eclipse.jetty.websocket.api.Session))

(defonce ws-clients (atom {}))

(defn new-ws-client
  [ws-session send-ch]
  #_(async/put! send-ch "This will be a text message")
  (let [id (.toString (java.util.UUID/randomUUID))]
    (swap! ws-clients assoc id {:channel send-ch :session ws-session})
    (async/put! send-ch (str {:type :id :msg id}))))

(defn send-and-close! []
  (let [[ws-session send-ch] (first @ws-clients)]
    (async/put! send-ch (java.util.UUID/randomUUID))
    ;; And now let's close it down...
    #_(async/close! send-ch)
    ;; And now clean up
    #_(swap! ws-clients dissoc ws-session)))

(defn send-message-to-all!
  [message]
  (doseq [[_ {:keys [^Session session channel]}] @ws-clients]
    ;; The Pedestal Websocket API performs all defensive checks before sending,
    ;;  like `.isOpen`, but this example shows you can make calls directly on
    ;;  on the Session object if you need to
    (when (.isOpen session)
      (async/put! channel (str message)))))

(defn get-username [clients message]
  (let [user-id (-> message :opts :user-id)]
    (:username (clients user-id))))

(defn prepare-chat-message [clients message]
  (let [username (get-username clients message)
        new-opts (-> message :opts (dissoc :user-id) (assoc :username username))]
    (assoc message :opts new-opts)))

(do
  (defn get-all-usernames [clients-map]
    (into {} (map (juxt (comp :username second) first) clients-map)))
  (get-all-usernames @ws-clients))
(-> @ws-clients vals )

(defn set-username! [msg]
  (let [id (-> msg :opts :user-id)
        username (msg :msg)
        clients-map @ws-clients
        client (clients-map id)
        usernames (get-all-usernames clients-map)]
    (log/info :x "Setting username"
              :usernames usernames
              :username* username
              :username (get usernames username))
    (if (and client (not (get usernames username)))
      (do
        (swap! ws-clients assoc-in [id :username] username)
        (assoc msg
               :client client
               :response {:type :username-has-been-set?
                          :msg {:username-has-been-set? true
                                :username username}}))
      (assoc msg
             :client client
             :response {:type :username-has-been-set?
                        :msg {:username-has-been-set? false
                              :error "Username has already been chosen"}}))))

(defn send-message-to-client! [client msg]
  (async/put! (client :channel) (str msg)))

(defn on-text [msg]
  #_(log/info :msg (str "A client sent - " msg))
  (try (let [msg* (edn/read-string msg)]
         (condp = (:type msg*)
           :pong nil ;; Client is alive.
           :chat (send-message-to-all! (prepare-chat-message @ws-clients msg*))
           :set-username (when-let [{:keys [client response]} (set-username! msg*)]
                           (send-message-to-client! client
                                                    response))
           (log/error "Unknow message type on web-socket" msg)))
       (catch Exception e (log/error "Could not read web-socket message" e)))
  #_(send-message-to-all! (str {:type :default :msg (str "You said: " (:msg  (clojure.edn/read-string msg)))})))
