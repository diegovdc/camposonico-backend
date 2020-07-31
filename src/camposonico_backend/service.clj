(ns camposonico-backend.service
  (:require [camposonico-backend.endpoints.freesound :as freesound]
            [camposonico-backend.utils :refer [try-or-throw validate-spec]]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.jetty.websockets :as ws]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor.error :as error]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-resp])
  (:import org.eclipse.jetty.websocket.api.Session))

(comment (require '[clj-utils.core :refer [spy]]))

(defonce db-url "postgresql://postgres:root@localhost:5432/camposonico")


(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok       (partial response 200))
(def created  (partial response 201))
(def accepted (partial response 202))


(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(s/def ::author (s/nilable (s/and string? #(<= (count %) 15))))
(s/def ::history string?)

(def validate-input
  {:name ::validate-input
   :enter
   (fn [ctx]
     (let [{:keys [history author]} (-> ctx :request :json-params)
           parsed-history (try-or-throw json/read-str "History is invalid" history)]
       (do (validate-spec ::author author "Author name must not exceed 15 characters in length")
           ;; TODO validate history correctly
           (validate-spec ::history parsed-history "History is invalid"))
       ctx))})

(def insert-history
  {:name ::insert-history
   :enter
   (fn [ctx]
     (let [{:keys [author history]} (-> ctx :request :json-params)]
       (jdbc/insert! db-url :histories {:author author
                                        :history history})
       (assoc ctx :response (created nil))))})

(comment (insert-history {:json-params {:author nil :history "\"Hello \""}}))


(defn home-page
  [request]
  (ring-resp/response "Hello World!"))

(def on-history-create-error {:name :on-error
                              :error (fn [ctx ex] ex)})
(def on-history-create-error
  (error/error-dispatch
   [ctx ex]
   [{:exception-type :org.postgresql.util.PSQLException :interceptor ::insert-history}]
   (assoc ctx :response {:status 500 :body "Could not save history"})
   [{:exception-type :clojure.lang.ExceptionInfo :interceptor ::validate-input}]
   (assoc ctx :response {:status 422 :body (:message (ex-data ex))})
   :else (assoc ctx ::chain/error ex)))

(def create-history [on-history-create-error
                     (body-params/body-params)
                     validate-input
                     insert-history])

(def routes
  ;; Defines "/" and "/about" routes with their associated :get handlers.
  ;; The interceptors defined after the verb map (e.g., {:get home-page}
  ;; apply to / and its children (/about).
  (route/expand-routes
   #{["/" :get home-page :route-name :home]
     ["/about" :get about-page :route-name ::about-page]
     ["/history" :post create-history :route-name ::create-history]
     ["/freesound/" :get freesound/get-sounds :route-name ::get-sounds]}))

(def ws-clients (atom {}))

(defn new-ws-client
  [ws-session send-ch]
  (async/put! send-ch "This will be a text message")
  (swap! ws-clients assoc ws-session send-ch))

;; This is just for demo purposes
(defn send-and-close! []
  (let [[ws-session send-ch] (first @ws-clients)]
    (async/put! send-ch "A message from the server")
    ;; And now let's close it down...
    (async/close! send-ch)
    ;; And now clean up
    (swap! ws-clients dissoc ws-session)))

;; Also for demo purposes...
(defn send-message-to-all!
  [message]
  (doseq [[^Session session channel] @ws-clients]
    ;; The Pedestal Websocket API performs all defensive checks before sending,
    ;;  like `.isOpen`, but this example shows you can make calls directly on
    ;;  on the Session object if you need to
    (when (.isOpen session)
      (async/put! channel message))))

(def ws-paths
  {"/ws" {:on-connect (ws/start-ws-connection new-ws-client)
          :on-text (fn [msg] (log/info :msg (str "A client sent - " msg)))
          :on-binary (fn [payload offset length] (log/info :msg "Binary Message!" :bytes payload))
          :on-error (fn [t] (log/error :msg "WS Error happened" :exception t))
          :on-close (fn [num-code reason-text]
                      (log/info :msg "WS Closed:" :reason reason-text))}})

;; Consumed by jetty-web-sockets.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::http/type :jetty
              ::http/container-options {:context-configurator #(ws/add-ws-endpoints % ws-paths)}
              ;;::http/host "localhost"
              ::http/port 8080})