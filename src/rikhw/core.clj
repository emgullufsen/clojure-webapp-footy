(ns rikhw.core
  (:require [ring.util.response]
            [ring.util.codec :as codec]
            [ring.adapter.jetty :as jetty]
            [clojure.data.json  :as json]
            [clj-http.client    :as client]
            [net.cgrand.enlive-html :as html]
            [com.ashafa.clutch :as clutch]
            [ring.middleware.params :refer [wrap-params]]
            [clj-time.core :as t]
            [clj-time.local :as l]))

(def dbs "http://admin:gullie06@localhost:5984/testeric2")
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

(defn get-date-key [gamesdata] ((gamesdata "filters") "dateFrom"))

(defn save-or-update-games [data]
  (clutch/with-db dbs
    (let [dk (get-date-key data)
          dc (clutch/get-document dk)]
      (clutch/put-document (merge dc {:_id dk} data)))))

(defn get-data [datestring] 
  (clutch/with-db dbs
    (let [dbdoc (clutch/get-document datestring)]
      (if dbdoc
        dbdoc
        (let
          [ms (-> (client/get (str matches-url "?" 
                                   (codec/form-encode 
                                     { :dateFrom datestring :dateTo datestring}))
                              headersmap)
                  :body
                  json/read-str)]
          (save-or-update-games ms))))))

(defn get-response []
  (client/get matches-url headersmap))

(defn get-json []
  (-> (get-response) :body json/read-str))

(defn get-matches-vector []
  (-> (get-json) (#(% "matches"))))

(def compnames (map (fn [c] (c "name")) comps))

(html/defsnippet singlematchsnippet "rikhwtemplates/main.html" [:tr]
  [{{hn "name"} "homeTeam" {an "name"} "awayTeam"}]
  [:#homeTeam] (html/content hn)
  [:#awayTeam] (html/content an))

(html/deftemplate matchesindex "rikhwtemplates/main.html"
  [matches]
  [:tbody] (html/content (map #(singlematchsnippet %) matches)))

(defn respond-with [mv]
  (ring.util.response/response (reduce str (matchesindex (mv)))))

(defn handler
  "give client matches for the day"
  [req]
  (ring.util.response/response (reduce str (matchesindex (get-matches-vector))))
  )  

(defn wrapped-handler []
  (wrap-params handler))

(defn -main []
  ;; run that server boi! port three stacks
  (jetty/run-jetty wrapped-handler {:port 3000}))
