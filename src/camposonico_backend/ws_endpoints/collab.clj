(ns camposonico-backend.ws-endpoints.collab
  (:require [camposonico-backend.utils :refer [now]]
            [camposonico-backend.ws-endpoints.chat
             :refer
             [send-message-to-client!]]
            [clojure.core.async :as async]
            [clojure.edn :as edn]
            [io.pedestal.log :as log]
            [clojure.string :as str]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.error :as error]
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
  (->> interceptor-maps (map interceptor/interceptor) (chain/execute msg)))

(comment
  (reset! collab-data {:clients {} :editor-histories {}})
  (-> @collab-data :clients first second :session type)
  (-> @collab-data :clients count)
  (-> @collab-data :editor-histories keys))

(defn new-ws-collab-client
  [ws-session send-ch]
  (let [id (.toString (java.util.UUID/randomUUID))]
    (swap! collab-data assoc-in [:clients id] {:channel send-ch :session ws-session})
    (async/put! send-ch
                (str {:type :id
                      :msg {:client-id id
                            :available-sessions (-> @collab-data
                                                    :sessions
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
  (interceptor/interceptor
   {:name ::add-collab-data
    :enter #(assoc % :collab-data collab-data)}))

(defn get-sessions* [collab-data-map]
  (->> collab-data-map :sessions keys set))

(defn get-usernames* [collab-data-map]
  (->> collab-data-map :clients (map (juxt (comp :username second) first)) (into {})))
(do
  (def get-usernames
    (interceptor/interceptor
     {:name ::get-usernames
      :enter (fn [ctx]
               (->> ctx :collab-data deref get-usernames*))}))
  ((:enter get-usernames) {:collab-data collab-data}))

(defn remove-closed-clients! []
  (let [clients-to-remove
        (remove (fn [[_ {:keys [session]}]]
                  (when (= (type session)
                           org.eclipse.jetty.websocket.common.WebSocketSession )
                    (.isOpen ^Session session)))
         (:clients @collab-data))
        client-ids (map first clients-to-remove)]
    (println "Removing clients" client-ids)
    (doseq [[_ {:keys [channel]}] clients-to-remove]
      (try (a/close! channel) (catch Exception e (println "Couldn't close channel" e))))
    (swap! collab-data update :clients #(apply dissoc % client-ids))))

(comment
(remove-closed-clients!)
  (def test-atom (atom {:b 1}))
  (swap! test-atom assoc :a 2)
  (swap! test-atom (fn [data]
                     (if (data :a)
                       (throw (ex-info "err" {}))
                       (update data :b inc))))
  (deref test-atom))
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

(defn send-to-all-recipients!
  [{:keys [response recipients collab-data]}]
  (let [clients (-> @collab-data get-collab-clients (select-keys (map first recipients)))]
    (doseq [[client-id* {:keys [^Session session channel]}] clients]
      (println "sending collab msg" client-id*)
      (when (.isOpen session) (async/put! channel (str response))))))

(defn send-to-all!
  [{:keys [response collab-data]}]
  (let [clients (-> @collab-data get-collab-clients)]
    (doseq [[client-id* {:keys [^Session session channel]}] clients]
      (println "sending msg to all" client-id*)
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
(comment
  (:response (on-collab-typing-event {:event-type :editor-change, :client-id "d27ea650-e459-4c4c-b887-9390796d78f6", :editor-id 1, :event "{\"from\":{\"line\":0,\"ch\":6,\"sticky\":null},\"to\":{\"line\":0,\"ch\":6,\"sticky\":null},\"text\":[\"d\"],\"removed\":[\"\"],\"origin\":\"+input\"}", :type :collab-event, :opts {}})))

(defn missing-fields-error [missing-fields messages]
  (ex-info "MissingFields"
           {:printable-error
            (str "One or more fields in the form are empty: "
                 (str/join ", " ((apply juxt missing-fields) messages)))}))

(def validate-create-session
  (interceptor/interceptor
   {:name ::validate-create-session
    :enter (fn [ctx]
             (let [missing-fields (->> (select-keys ctx [:session-name :password :username])
                                       (filter (comp #(or (nil? %) (empty? %)) second))
                                       (map first))
                   messages {:session-name "please give a name to you session"
                             :password "please set a password"
                             :username "please choose a username"}]
               (if-not (empty? missing-fields)
                 (throw (missing-fields-error missing-fields messages))
                 ctx)))}) )

(declare session-exists?)

(defn get-session-password [collab-data-map session-name]
  (get-in collab-data-map [:sessions session-name :password]))

(def validate-join-session
  (interceptor/interceptor
   {:name ::validate-join-session
    :enter (fn [{:keys [collab-data session-name password] :as ctx}]
             (let [missing-fields (->> (select-keys ctx [:session-name :username])
                                       (filter (comp
                                                #(or (nil? %) (empty? %))
                                                second))
                                       (map first))
                   messages {:session-name "please select a session"
                             :username "please choose a username"}
                   valid-password? (= password
                                      (get-session-password @collab-data
                                                            session-name))]
               (cond
                 (not (session-exists? @collab-data session-name))
                 (throw (ex-info
                         "Session does not exist"
                         {:printable-error "The chosen session does not exist, please select another or create one."}))
                 (not (empty? missing-fields))
                 (throw (missing-fields-error missing-fields messages))
                 :else (assoc ctx :valid-password? valid-password?))))}))

(do
  (defn get-all-usernames-as-map [clients-map]
    (into {} (map (juxt (comp :username second) first) clients-map)))
  (get-all-usernames-as-map (@collab-data :clients)))

(defn session-exists? [collab-data-map session-name]
  (let [sessions (get-sessions* collab-data-map)]
    (not (nil? (sessions session-name)))))

(do
  (defn username-taken-by-other-user?
    [collab-data-map client-id username]
    (let [usernames (get-usernames* collab-data-map)]
      (-> usernames (get username)
          (#(and (not (nil? %))
                 (not= % client-id))))))
  (username-taken-by-other-user? {:clients {"a" {:username "b-un"}
                                            "b" {}}}
                                 "b"
                                 "b-un"))

(defn set-session!
  "Will throw if a session with that name already exists"
  [collab-data-map session-name password]
  (if (session-exists? collab-data-map session-name)
    (throw
     (ex-info "Session already exists"
              {:printable-error "A session with that name already exists, please choose another one."}))
    (assoc-in collab-data-map [:sessions session-name] {:password password
                                                        :histories {}})))

(defn set-username!*
  "Will throw if a username with that name already exists"
  [collab-data-map client-id username]
  (if (username-taken-by-other-user? collab-data-map client-id username)
    (throw
     (ex-info "Username already taken"
              {:printable-error "Username already taken, please choose anotherone."}))
    (assoc-in collab-data-map [:clients client-id :username] username)))

(def create-session!
  (interceptor/interceptor
   {:name ::create-session!
    :enter
    (fn [{:keys [collab-data client-id username session-name password] :as ctx}]
      (swap! collab-data
             (fn [data]
               (-> data
                   (set-session! session-name password)
                   (set-username!* client-id username))))
      (assoc ctx :valid-password? true))}))

(def add-client-to-session!
  (interceptor/interceptor
   {:name ::add-client-to-session!
    :enter (fn [{:keys [collab-data client-id session-name] :as ctx}]
             (swap! collab-data assoc-in
                    [:clients client-id :session-name] session-name)
             ctx)}))
(-> collab-data deref :clients first)
((:enter add-client-to-session!) {:collab-data collab-data :client-id "9656bc7d-856f-4aed-87b5-0bb517ece720" :session-name "pepe"})

(defn make-session-response
  [{:keys [session-name username password valid-password?]}]
  {:success? true
   :username username
   :session-name session-name
   :session-path (str "/live/?session=" session-name)
   :password password
   :valid-password? valid-password?})

(def create-session-response
  (interceptor/interceptor
   {:name ::create-session-response
    :enter (fn [ctx]
             (assoc ctx :response
                    (merge (make-session-response ctx)
                           {:type :start-session
                            :session-action :create})))}))
(def alert-when-invalid-password
  (interceptor/interceptor
   {:name ::alert-when-invalid-password
    :enter (fn [{:keys [password valid-password?] :as ctx}]
             (if (and (not (empty? password)) (not valid-password?))
               (throw (ex-info "InvalidInputPassword" {:printable-error "The password you are trying to use is incorrect, please try again or leave the password field empty."}))
               ctx))}))

(def join-session-response
  (interceptor/interceptor
   {:name ::join-session-response
    :enter (fn [ctx]
             (assoc ctx :response
                    (merge (make-session-response ctx)
                           {:type :start-session
                            :session-action :join})))}))

(def set-username!
  "Sets a username if not taken, else throws an error."
  (interceptor/interceptor
   {:name ::set-username!
    :enter
    (fn [{:keys [collab-data client-id username] :as ctx}]
      (swap! collab-data set-username!* client-id username)
      (assoc ctx :username-set? true))}))

#_((:enter set-username!) {:collab-data (atom {:clients {"a" {:username "b-un"}
                                                         "b" {}}})
                           :client-id "b"
                           :username "b-un"})


(def process-session-action
  {:name ::process-session-action
   :enter (fn [ctx]
            (case (ctx :session-action)
              :create (chain/enqueue ctx [validate-create-session
                                          create-session!
                                          add-client-to-session!
                                          create-session-response])
              :join (chain/enqueue ctx [validate-join-session
                                        set-username!
                                        alert-when-invalid-password
                                        add-client-to-session!
                                        join-session-response])
              (throw (ex-info
                      "Invalid session action"
                      {:printable-error "Invalid session action"}))))})

(def on-start-session-error
  (error/error-dispatch
   [ctx ex]
   [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::process-session-action}]
   (assoc ctx :response {:type :start-session :status 422 :body (:printable-error (ex-data ex))})
   [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::validate-create-session}]
   (assoc ctx :response {:type :start-session :status 422 :body (:printable-error (ex-data ex))})
   [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::validate-join-session}]
   (assoc ctx :response {:type :start-session :status 422 :body (:printable-error (ex-data ex))})
   [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::set-username!}]
   (assoc ctx :response {:type :start-session :status 422 :body (:printable-error (ex-data ex))})
   [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::create-session!}]
   (assoc ctx :response {:type :start-session :status 422 :body (:printable-error (ex-data ex))})
   [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::alert-when-invalid-password}]
   (assoc ctx :response {:type :start-session :status 422 :body (:printable-error (ex-data ex))})
   :else {:type :start-session :status 422 :body "An unknown error ocurred"}))

(-> @collab-data)

(defn start-session [msg]
  (run-chain msg [on-start-session-error add-collab-data get-client process-session-action]))
#_(do
    #_(defn start-session [msg]
        (-> msg :msg validate-start-session)
        ( set-usernamex msg))
    (println (run-chain
              {:session-action :join,
               :session-name "ssa",
               :password "10",
               :username "Saar",
               :client-id "cf5c32e-4cc6-4004-b46a-f36191c2f7d7",
               :type :start-session,
               :opts {}}
              [on-start-session-error add-collab-data process-session-action]))
    #_(start-session {:msg {:session-action :join, :session-name "s", :password "1", :username "s"}
                      :type :start-session
                      :opts {:user-id "0972b065-f03a-401b-9fc2-3bcbd3f3ad60"}}))

(def respond-pong
  {:name ::respond-pong
   :enter (fn [ctx] (assoc ctx :response {:type :still-connected?
                                         :timestamp (now)}))})

(run-chain {:client-id "f3aa4668-b570-4579-b34a-cd4ed53649a0", :type :pong, :opts {}}
           [add-collab-data get-client])

(defn send-response-to-client! [{:keys [client response]}]
  (when-not response (log/error :response "Missing response `key`"))
  (async/put! (client :channel) (str response)))


(def prepare-chat-message
  (interceptor/interceptor
   {:name ::prepare-chat-message-response
    :enter (fn [{:keys [message client] :as ctx}]
             (assoc ctx
                    :recipients (->> @collab-data :clients
                                     (filter (fn [[_ data]]
                                               (= (:session-name data)
                                                  (:session-name client)))))
                    :response {:type :chat
                               :message message
                               :username (:username client)
                               :session-name (:session-name client)
                               :timestamp (now)}))}))

(defn chat-message [msg]
  (run-chain msg [add-collab-data get-client prepare-chat-message]))

(chat-message {:client-id "9656bc7d-856f-4aed-87b5-0bb517ece720" :message "holi" :timestamp "now"})

(defn on-collab-text [msg]
  (try (let [msg* (edn/read-string msg)]
         (when (= :start-session (msg* :type)) (log/info :collab-event msg*))
         (condp = (:type msg*)
           :pong (send-response-to-client! (run-chain msg* [add-collab-data get-client respond-pong]))
           :set-username (when-let [{:keys [client response]} (set-username! msg*)]
                           (send-message-to-client! client
                                                    response))
           :collab-event (send-to-all-except-client! (on-collab-typing-event msg*))
           :start-session  (send-response-to-client! (start-session msg*))
           :chat-message (send-to-all-recipients! (chat-message msg*))
           (log/error "Unknow message type on web-socket" msg)))
       (catch Exception e (log/error "Could not read web-socket message" e)))
  #_(send-message-to-all! (str {:type :default :msg (str "You said: " (:msg  (clojure.edn/read-string msg)))})))
