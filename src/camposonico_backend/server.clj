(ns camposonico-backend.server
  (:gen-class)
  (:require [camposonico-backend.service :as service]
            [io.pedestal.http :as server]
            [io.pedestal.http.route :as route]))

;; This is an adapted service map, that can be started and stopped
;; From the REPL you can call server/start and server/stop on this service
(defonce runnable-service (server/create-server service/service))

(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
  (-> service/service ;; start with production configuration
      (merge {:env :dev
              ;; do not block thread that starts web server
              ::server/join? false
              ;; Routes can be a function that resolve routes,
              ;;  we can use this to set the routes to be reloadable
              ::server/routes #(route/expand-routes (service/routes))
              ;; all origins are allowed in dev mode
              ::server/allowed-origins {:creds true :allowed-origins (constantly true)}})
      ;; Wire up interceptor chains
      server/default-interceptors
      server/dev-interceptors
      server/create-server
      server/start))

(defonce dev-server (atom nil))

(defn restart []
  (when @dev-server (server/stop @dev-server))
  (reset! dev-server (run-dev))
  "-----------(re)started server")

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (println "\nStarting production server...")
  (server/start runnable-service))

(comment
  (restart)
  (server/stop @dev-server))
