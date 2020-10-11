(ns camposonico-backend.ws-endpoints.collab
  (:require [camposonico-backend.utils :refer [now]]
            [camposonico-backend.ws-endpoints.chat
             :refer
             [send-message-to-client! set-username!]]
            [clojure.core.async :as async]
            [clojure.edn :as edn]
            [io.pedestal.log :as log]
            [clojure.string :as str]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor :as interceptor]
            [clojure.spec.alpha :as s]
            [clojure.core.async :as a])
  (:import java.util.UUID
           org.eclipse.jetty.websocket.api.Session))

(defn chan? [x] (= clojure.core.async.impl.channels.ManyToManyChannel (type x)))
(def session? any?)                     ;; TODO maybe necessary to fix this

(s/def ::channel chan?)
(s/def ::session session?)

(s/def ::client (s/keys :req-un [::channel ::session]))

(s/def ::client-id uuid?)
(s/def ::type #{:ping})

(s/def ::in-msg (s/keys :req-un [::client-id ::type]))

(defonce collab-data (atom {:clients {} :editor-histories {}}))

(defn run-chain [msg interceptor-maps]
  (->> interceptor-maps (map interceptor/map->Interceptor) (chain/execute msg)))

(comment
  (reset! collab-data {:clients {} :editor-histories {}})
  (-> @collab-data :clients)
  (-> @collab-data :editor-histories keys))

(defn new-ws-collab-client
  [ws-session send-ch]
  (let [id (.toString (java.util.UUID/randomUUID))]
    (swap! collab-data assoc-in [:clients id] {:channel send-ch :session ws-session})
    (async/put! send-ch
                (str {:type :id
                      :msg {:client-id id
                            :available-sessions (-> @collab-data
                                                    :editor-histories
                                                    keys)}}))))

(defn on-connect [ws-session send-ch]
  (new-ws-collab-client ws-session send-ch))

(def get-client
  {:name ::get-client
   :enter (fn [ctx]
            (assoc ctx :client (-> (ctx :collab-data) deref :clients (get (ctx :client-id)))))})

(defn get-collab-clients [collab-data-map]
  (collab-data-map :clients))

(def add-collab-data
  {:name ::add-collab-data
   :enter #(assoc % :collab-data collab-data)})


(defn remove-closed-clients! []
  (let [clients-to-remove (remove (fn [[_ {:keys [session]}]] (.isOpen session))
                                  (:clients @collab-data))
        client-ids (map first clients-to-remove)]
    (doseq [[_ {:keys [channel]}] clients-to-remove]
      (try (a/close! channel) (catch Exception e (println "Couldn't close channel" e))))
    (swap! collab-data update :clients #(apply dissoc % client-ids))))

(remove-closed-clients!)

(-> @collab-data :clients count)

(defn send-to-all-except-originator! [{:keys [msg originator-id]}]
  (let [clients (-> @collab-data get-collab-clients (dissoc originator-id))]
    (doseq [[client-id {:keys [^Session session channel]}] clients]
      (println "sending collab msg" client-id)
      (when (.isOpen session) (async/put! channel (str msg))))))

(defn send-to-all-except-client! [{:keys [response client-id collab-data]}]
  (let [clients (-> @collab-data get-collab-clients (dissoc client-id))]
    (doseq [[client-id* {:keys [^Session session channel]}] clients]
      (println "sending collab msg" client-id*)
      (when (.isOpen session) (async/put! channel (str response))))))

(defn format-event [{:keys [client-id event event-type editor-id]}]
  {:data event
   :event-type event-type
   :originator-id client-id
   :editor-id editor-id
   :time (now)})

(def save-collab-typing-event!
  {:name ::save-collab-typing-event!
   :enter (fn [{:keys [editor-id session-id] :as ctx}]
            (let [formatted-event (format-event ctx)]
              (swap! collab-data update-in [:editor-histories session-id editor-id]
                     (fn [history] (conj (or history []) formatted-event)) )
              (assoc ctx :formatted-event formatted-event)))})

(def prepare-collab-typing-msg-broadcast
  {:name ::prepare-collab-typing-msg-broadcast
   :enter (fn [{:keys [formatted-event session-id] :as ctx}]
            (assoc ctx :response (merge {:type :collab-event-broadcast
                                         :session-id session-id}
                                        formatted-event)))})

(defn on-collab-typing-event [msg]
  (println msg)
  ;; TODO figure out session-id
  ;; TODO figure out editor-id management
  (let [session-id 1]
    (run-chain (assoc msg :session-id session-id)
               [add-collab-data
                save-collab-typing-event!
                prepare-collab-typing-msg-broadcast])
    #_(-> msg
        #_(save-collab-typing-event! session-id)
        (prepare-collab-typing-msg-broadcast session-id)
        send-to-all-except-originator!)))
(:response (on-collab-typing-event {:event-type :editor-change, :client-id "d27ea650-e459-4c4c-b887-9390796d78f6", :editor-id 1, :event "{\"from\":{\"line\":0,\"ch\":6,\"sticky\":null},\"to\":{\"line\":0,\"ch\":6,\"sticky\":null},\"text\":[\"d\"],\"removed\":[\"\"],\"origin\":\"+input\"}", :type :collab-event, :opts {}}))
(defn validate-create-session [data]
  (let [missing-fields (->> (dissoc data :session-action)
                            (filter (comp #(or (nil? %) (empty? %)) second))
                            (map first))
        messages {:session-name "please give a name to you session"
                  :password "please set a password"
                  :username "please choose a username"}]
    (when-not (empty? missing-fields)
      (str "One or more fields in the form are empty: "
           (str/join ", " ((apply juxt missing-fields) messages))))) )
(defn validate-join-session [data]
  (let [missing-fields (->> (dissoc data :password :session-action)
                            (filter (comp
                                     #(or (nil? %) (empty? %))
                                     second))
                            (map first))
        messages {:session-name "please select a session"
                  :username "please choose a username"}]
    (when-not (empty? missing-fields)
      (str "One or more fields in the form are empty: "
           (str/join ", " ((apply juxt missing-fields) messages))))) )

(do
  (defn get-all-usernames-as-map [clients-map]
    (into {} (map (juxt (comp :username second) first) clients-map)))
  (get-all-usernames-as-map (@collab-data :clients)))

(def a (interceptor/map->Interceptor {:name ::a :enter (fn [ctx] (assoc ctx :holi :boli))}))

(def b (interceptor/map->Interceptor
        {:name ::b
         :enter (fn [ctx]
                  (assoc ctx :response {:body  {:b-response :boli} :status 200}))}))
(def c (interceptor/map->Interceptor
        {:name ::c :enter (fn [ctx] (assoc ctx :response {:c-response :boli}))}))
count

(defn set-usernamex [msg]
  (let [id (-> msg :opts :user-id)
        username (-> msg :msg :username)
        clients-map (@collab-data :clients)
        client (clients-map id)
        usernames (get-all-usernames-as-map clients-map)]
    (log/info :x "Setting username"
              :usernames usernames
              :username* username
              :username (get usernames username))
    (if (and client (not (get usernames username)))
      (do
        #_(swap! ws-clients assoc-in [id :username] username)
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
(defn validate-start-session [msg]
  (let [validation-message (case (msg :session-action)
                             :create (validate-create-session msg)
                             :join (validate-join-session msg)
                             "Invalid session action")]
    (if-not validation-message
      msg
      {:response {:error validation-message}})))

(do
  (defn start-session [msg]
    (-> msg :msg validate-start-session)
    ( set-usernamex msg))
  #_(start-session {:msg {:session-action :join, :session-name "s", :password "1", :username "s"}
                    :type :start-session
                    :opts {:user-id "0972b065-f03a-401b-9fc2-3bcbd3f3ad60"}}))

(def respond-pong
  {:name ::respond-pong
   :enter (fn [ctx] (assoc ctx :response {:type :still-connected?
                                         :time (now)}))})

(run-chain {:client-id "f3aa4668-b570-4579-b34a-cd4ed53649a0", :type :pong, :opts {}}
           [add-collab-data get-client])

(defn send-response-to-client! [{:keys [client response]}]
  (when-not response (log/error :response "Missing response `key`"))
  (async/put! (client :channel) (str response)))

(defn on-collab-text [msg]
  (try (let [msg* (edn/read-string msg)]
         (when (= :collab-event (msg* :type)) (log/info :collab-event msg*))
         (condp = (:type msg*)
           :pong (send-response-to-client! (run-chain msg* [add-collab-data get-client respond-pong]))
           :set-username (when-let [{:keys [client response]} (set-username! msg*)]
                           (send-message-to-client! client
                                                    response))
           :collab-event (send-to-all-except-client! (on-collab-typing-event msg*))
           :start-session (start-session msg*)
           (log/error "Unknow message type on web-socket" msg)))
       (catch Exception e (log/error "Could not read web-socket message" e)))
  #_(send-message-to-all! (str {:type :default :msg (str "You said: " (:msg  (clojure.edn/read-string msg)))})))
