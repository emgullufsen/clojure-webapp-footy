(ns rikhw.core
  (:require [ring.util.response]
            [ring.adapter.jetty :as jetty]
            [clojure.data.json  :as json]
            [clj-http.client    :as client]
            [net.cgrand.enlive-html :as html]))

(def base-url    "https://api.football-data.org/v2/")
(def matches-url (str base-url "matches"))
(def comps-url   (str base-url "competitions"))
(def headersmap  {:headers {:X-AUTH-TOKEN "1a65e8acccdb47949431186d2d4ea406"}}) 

(def comps 
  ((json/read-str ((client/get comps-url headersmap) :body)) "competitions"))

(defn get-games [] 
  (let [resp (client/get matches-url headersmap)
        bod  (resp :body)
        jb   (json/read-str bod)
        ms   (jb  "matches")]
    ms
))

(defn get-games2 []
  (-> (client/get matches-url headersmap) :body json/read-str (#(% "matches"))))

(def compnames (map (fn [c] (c "name")) comps))

(html/defsnippet singlematchsnippet "rikhwtemplates/main.html" [:td]
  [team1]
  [:td] (html/content team1))

(html/deftemplate matchesindex "rikhwtemplates/main.html"
  [matches]
  [:tbody] (html/content (map #(singlematchsnippet %) matches)))

(defn handler
  "I don't do a whole lot."
  [req]
  (ring.util.response/response (reduce str (matchesindex compnames)))
  ;;{ :status 200 :headers {"content-type" "text/html"} :body "Hey"}
  ;;(ring.util.response/response "Hey man whatup...")
  )  

(defn -main []
  ;; run that server boi! port three stacks
  (jetty/run-jetty handler {:port 3000}))
