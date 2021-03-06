(defproject rikhw "0.1.1-SNAPSHOT"
  :description "app for showing footy scores"
  :url "https://rickysquid.org/clojure-footy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring/ring-core "1.8.2"]
                 [ring/ring-jetty-adapter "1.8.2"]
                 [clj-http "3.10.3"]
                 [org.clojure/data.json "0.2.7"]
                 [enlive "1.1.6"]
                 [com.ashafa/clutch "0.4.0"]
                 [cheshire "5.10.0"]
                 [clj-time "0.15.2"]
                 [environ "1.2.0"]]
  :plugins [[lein-ring "0.12.5"]]
  :test-selectors {:default (complement :with-db)
                 :with-db :with-db}
  :ring {:handler rikhw.core/wrapped-handler}
  :main rikhw.core
  :profiles {:uberjar {:aot :all}})
