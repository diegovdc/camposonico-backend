(defproject camposonico-backend "0.0.1-SNAPSHOT"
  :description ""
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [io.pedestal/pedestal.service "0.5.5"]
                 [org.clojure/core.async "0.4.474"]
                 [io.pedestal/pedestal.jetty "0.5.5"]
                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]

                 ;; data types
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/spec.alpha "0.2.187"]
                 [clj-http "3.10.1"]
                 [environ "1.2.0"]
                 [org.clojure/core.async "1.3.610"]
                 ;; pg
                 [org.clojure/java.jdbc "0.6.1"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [clj-postgresql "0.7.0"]
                 [clj-utils "0.1.0-SNAPSHOT"]]
  :plugins [[lein-environ "1.2.0"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  ;; :pedantic? :abort
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "jetty-web-sockets.server/run-dev"]}}
             :uberjar {:aot [jetty-web-sockets.server]}}
  :main ^{:skip-aot true} jetty-web-sockets.server)
