(ns camposonico-backend.service
  (:require [camposonico-backend.endpoints.collab-history.get-collab-history
             :refer
             [get-collab-history]]
            [camposonico-backend.endpoints.freesound :as freesound]
            [camposonico-backend.endpoints.histories.create-history
             :refer
             [create-history]]
            [camposonico-backend.endpoints.histories.get-history
             :refer
             [get-history]]
            [camposonico-backend.jdbc.protocol-extensions :as jdbc-protocol]
            [camposonico-backend.ws-endpoints.core :refer [ws-paths]]
            [environ.core :refer [env]]
            [io.pedestal.http :as http]
            [io.pedestal.http.jetty.websockets :as ws]
            [io.pedestal.http.route :as route]
            [ring.util.response :as ring-resp]))

(comment (require '[clj-utils.core :refer [spy]]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn home-page
  [request]
  (ring-resp/response "Hello World!"))

(defn routes []
  #{["/" :get home-page :route-name :home]
    ["/about" :get about-page :route-name ::about-page]
    ["/history" :post create-history :route-name ::create-history]
    ["/history/:id" :get get-history :route-name ::get-history]
    ["/freesound/" :get freesound/get-sounds :route-name ::get-sounds]
    ["/collab-history/:id" :get get-collab-history :route-name ::get-collab-history]})

;; NOTE side effect
(jdbc-protocol/run-extensions!)

;; Consumed by jetty-web-sockets.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes (routes)

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
              ::http/container-options {:context-configurator
                                        #(ws/add-ws-endpoints % ws-paths)}
              ::http/allowed-origins {:creds true :allowed-origins (constantly true)}
              ::http/host "0.0.0.0"
              ::http/port (read-string (env :port "3456"))})
