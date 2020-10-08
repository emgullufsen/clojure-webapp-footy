(ns rikhw.core
  (:require [ring.util.response]
            [ring.adapter.jetty :as jetty]
            [clojure.data.json  :as json]
            [clj-http.client    :as client]))

(def comps ((json/read-str 
   ((client/get "https://api.football-data.org/v2/competitions" 
         {:headers {:X-AUTH-TOKEN "1a65e8acccdb47949431186d2d4ea406"}}) 
    :body)) 
  "competitions"))

(def compnames (map (fn [c] (c "name")) comps))

(defn handler
  "I don't do a whole lot."
  [req]
  (ring.util.response/response (str "Hey first comp is " (first compnames)))
  ;;{ :status 200 :headers {"content-type" "text/html"} :body "Hey"}
  ;;(ring.util.response/response "Hey man whatup...")
  )  

(defn -main []
  ;; run that server boi! port three stacks
  (jetty/run-jetty handler {:port 3000}))
