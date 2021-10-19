(ns rikhw.core
  (:require [ring.util.response]
            [ring.util.codec :as codec]
            [ring.adapter.jetty :as jetty]
            [clojure.data.json  :as json]
            [clj-http.client    :as client]
            [net.cgrand.enlive-html :as html]
            [com.ashafa.clutch :as clutch]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [clj-time.core :as t]
            [clj-time.local :as local]
            [clj-time.format :as tf]
            [clojure.string :as stringy]
            [clojure.edn :as cedn]
	    [clojure.java.io :as io]
            [environ.core :as environ]
            )
  (:gen-class))

(defn get-db-base []
  (environ/env :couch-db-string))

(defn get-db-base-plus [db] (str (get-db-base) db))
(defn get-db-string-teams [] (get-db-base-plus "teams"))
(defn get-db-string-testeric2 [] (get-db-base-plus "testeric2"))

(def base-url    "https://api.football-data.org/v2/")
(def matches-url (str base-url "matches"))
;;:throw-exceptions false 
(defn get-headersmap [] {:throw-exceptions false :headers {"X-AUTH-TOKEN" (environ/env :football-data-api-xauth)}})

(defn get-date-key [gamesdata] ((gamesdata :filters) :dateFrom))

(defn get-home-id [m] (-> m :homeTeam :id))
(defn get-away-id [m] (-> m :awayTeam :id))

(defn e-encode [s] (codec/form-encode {:dateFrom s :dateTo s}))

(defn get-stuff-from-api [urlstring headersobj] 
  (let [resp (client/get urlstring headersobj)
        status (resp :status)
        statuspeek (not (= status 200))]
    (if statuspeek
      nil
      (json/read-str (resp :body) :key-fn keyword))))

(defn get-matches-from-api [datestring headersobj]
  (let [url-plus-qs (str matches-url "?" (e-encode datestring))]
    (get-stuff-from-api url-plus-qs headersobj)))

(defn teams-url [id] (str base-url "teams/" id))

(defn get-team [id db-teams headers]
  (clutch/with-db db-teams
    (let [team-doc (clutch/get-document id)
          tu       (teams-url id)]
      (if team-doc
          team-doc
          (let [teamdat (get-stuff-from-api tu headers)]
            (if (nil? teamdat)
              nil
              (let [tid (str (teamdat :id))]
                (clutch/put-document (assoc teamdat :_id tid)))))))))        

(defn add-teams-to-matches [matches db-teams headers]
  (map (fn [m]
         (let [hid (get-home-id m)
               aid (get-away-id m)
               ht (get-team hid db-teams headers)
               at (get-team aid db-teams headers)]
           (assoc m :htizzle ht :atizzle at))) 
       matches))

(defn needs-update? [m]
  "checks if a game needs to update score from API"
  (let [[utcDate lastUpdated] (map #(tf/parse (tf/formatters :date-time-no-ms) %) [(m :utcDate) (m :lastUpdated)])
        localtime (local/local-now)
        status (m :status)]
    (if (t/after? localtime utcDate) 
      (if (t/before? lastUpdated utcDate)
        true
        (if (not= status "FINISHED")
          true
          false))
      false)))

(defn get-data [datestring dbs headers] 
  (clutch/with-db dbs
    (let [dbdoc (clutch/get-document datestring)]
      (if dbdoc
        (let [matches (dbdoc :matches)
              nu      (some needs-update? matches)]
          (if nu
            (let [ud (get-matches-from-api datestring headers)]
              (if (nil? ud) 
                dbdoc 
                (clutch/put-document (assoc ud :_id (get-date-key ud) :_rev (dbdoc :_rev)))))
            dbdoc))
        (let
          [ms (get-matches-from-api datestring headers)]
          (if (nil? ms)
            nil
            (clutch/put-document (assoc ms :_id (get-date-key ms)))))))))

(defn assemble-page-data [datestring]
  (let [matches-db-string (get-db-string-testeric2)
        teams-db-string (get-db-string-teams)
        headers (get-headersmap)
        dat ((get-data datestring matches-db-string headers) :matches)]
    (add-teams-to-matches dat teams-db-string headers)))

(html/defsnippet singleplayersnippet "rikhwtemplates/main.html" [:li]
  [{namey :name}]
  [:span] (html/content namey))

(defmulti get-colors identity)

(defmethod get-colors nil [string]
  ["White" "Black"])

(defmethod get-colors :default [instring]
  (let [splitz (stringy/split instring #" ")]
    (if (> (count splitz) 2)
      [(get splitz 0) (get splitz 2)]
      ["White" "Black"])))

(defmulti get-style-string (fn [[c1 c2]] (= c2 "White")))

(defmethod get-style-string true [[c1 c2]]
  (str "background-color: " c1 "; " "border-style: double;"))

(defmethod get-style-string :default [[c1 c2]]
  (str "background-color: " c1 "; " "border-color: " c2 ";"))

(html/defsnippet singlematchsnippet "rikhwtemplates/main.html" [:tr]
  [{scoreobj :score {hn :name} :homeTeam {an :name} :awayTeam {homeCrestUrl :crestUrl squadHome :squad homeColors :clubColors} :htizzle {awayCrestUrl :crestUrl squadAway :squad awayColors :clubColors} :atizzle}]
  [:.scoreSpot] (html/content (str (-> scoreobj :fullTime :homeTeam) " - " (-> scoreobj :fullTime :awayTeam)))
  [:.homeTeam] (html/set-attr :style (-> homeColors get-colors get-style-string))
  [:.awayTeam] (html/set-attr :style (-> awayColors get-colors get-style-string))
  [:.homeTeamImage] (html/set-attr :src homeCrestUrl)
  [:.awayTeamImage] (html/set-attr :src awayCrestUrl)
  [:.homeTeamCaption] (html/content hn)
  [:.awayTeamCaption] (html/content an)
  [:.homeTeamPlayers] (html/content (map #(singleplayersnippet %) squadHome))
  [:.awayTeamPlayers] (html/content (map #(singleplayersnippet %) squadAway)))

(def formatter (tf/formatters :year-month-day))
(def oneday (t/days 1))

(defn add-day [s]
  "accepts [string] as arg in YYYY-MM-DD format and returns next days string"
  (local/format-local-time (t/plus (tf/parse formatter s) oneday) :year-month-day))

(defn subtract-day [s]
  "accepts [string] as arg in YYYY-MM-DD format and returns previous days string"
  (local/format-local-time (t/minus (tf/parse formatter s) oneday) :year-month-day))

(html/deftemplate matchesindex "rikhwtemplates/main.html"
  [matches day]
  [:tbody] (html/content (map #(singlematchsnippet %) matches))
  [:#yesterday] (html/set-attr :href (str "?" (codec/form-encode {:gameDate (subtract-day day)})))
  [:#tomorrow] (html/set-attr :href (str "?" (codec/form-encode {:gameDate (add-day day)}))))

(defn respond-with [mv gd]
  (-> (ring.util.response/response (reduce str (matchesindex mv gd)))) (ring.util.response/header "Content-Type" "text/html; charset=utf-8"))

(defn handler
  "give client matches for the day"
  [req]
  (let [gameDate  (get-in req [:params "gameDate"])
        localtime (local/format-local-time (local/local-now) :year-month-day)
        useDate (if gameDate gameDate localtime)
        useDataM (assemble-page-data useDate)]
    (respond-with useDataM useDate)))

(def wrapped-handler
  (-> handler wrap-params))

(defn -main []
  ;; run that server boi! port three stacks
  (jetty/run-jetty wrapped-handler {:port 3000}))
