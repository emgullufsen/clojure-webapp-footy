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
            [clj-time.local :as l]
            [clj-time.format :as tf]
            [clojure.string :as stringy]))

(def db-base "http://admin:gullie06@localhost:5984/")
(def dbs (str db-base "testeric2"))
(def db-teams (str db-base "teams"))
(def base-url    "https://api.football-data.org/v2/")
(def matches-url (str base-url "matches"))
(def comps-url   (str base-url "competitions"))
(def headersmap  {:headers {"X-AUTH-TOKEN" "1a65e8acccdb47949431186d2d4ea406"}}) 

(def comps 
  ((json/read-str ((client/get comps-url headersmap) :body)) "competitions"))

(defn get-date-key [gamesdata] ((gamesdata :filters) :dateFrom))

(defn get-home-id [m] (-> m :homeTeam :id))
(defn get-away-id [m] (-> m :awayTeam :id))

(defn save-or-update-games [data]
  (clutch/with-db dbs
    (let [dk (get-date-key data)
          dc (clutch/get-document dk)]
      (clutch/put-document (merge dc {:_id dk} data)))))

(defn hit-api [datestring] 
  (let [resp (client/get (str matches-url "?" (codec/form-encode { :dateFrom datestring :dateTo datestring})) headersmap)
        bod  (resp :body)]
      (do
        (println "hitting api (hit-api)")
        (json/read-str bod :key-fn keyword)
        )))

(defn gen-hit [url]
  (let [resp (client/get url headersmap)
        bod  (resp :body)]
      (do
        (println (str "hitting api (gen-hit)" url))
        (json/read-str bod :key-fn keyword))))

(defn teams-url [id] (str base-url "teams/" id))

(defn get-team [id]
  (clutch/with-db db-teams
    (let [team-doc (clutch/get-document id)
          tu       (teams-url id)]
      (if team-doc
        (do
          ;;(println "*from db")
          team-doc)
        (let [teamdat (gen-hit tu) 
              tid     (str (teamdat :id))]
            (do
              ;;(println "*new")
              (clutch/put-document (merge {:_id tid} teamdat))))))))

(defn add-teams-to-matches [matches]
  (map (fn [m] (merge m {:htizzle (get-team (get-home-id m)) :atizzle (get-team (get-away-id m))})) matches))

(defn needs-update? [m]
  "checks if a game needs to update score from API"
  (let [[utcDate lastUpdated] (map #(tf/parse (tf/formatters :date-time-no-ms) %) [(m :utcDate) (m :lastUpdated)])
        localtime (l/local-now)
        status (m :status)]
    (if (t/after? localtime utcDate) 
      (if (t/before? lastUpdated utcDate)
        true
        (if (not= status "FINISHED")
          true
          false))
      false)))

(defn get-data [datestring] 
  (clutch/with-db dbs
    (let [dbdoc (clutch/get-document datestring)]
      (if dbdoc
        (let [matches (dbdoc :matches)
              nu      (some needs-update? matches)]
          (if nu
            (save-or-update-games (hit-api datestring))
            dbdoc))
        (let
          [ms (hit-api datestring)]
            (save-or-update-games ms))))))

(defn get-response []
  (client/get matches-url headersmap))

(defn get-json []
  (-> (get-response) :body json/read-str))

(defn get-matches-vector []
  (-> (get-json) (#(% "matches"))))

(def compnames (map (fn [c] (c "name")) comps))

(html/defsnippet singleplayersnippet "rikhwtemplates/main.html" [:li]
  [{namey :name}]
  [:span] (html/content namey))

(html/defsnippet singlematchsnippet "rikhwtemplates/main.html" [:tr]
  [{{hn :name} :homeTeam {an :name} :awayTeam {homeCrestUrl :crestUrl squadHome :squad homeColors :clubColors} :htizzle {awayCrestUrl :crestUrl squadAway :squad awayColors :clubColors} :atizzle}]
  [:.homeTeam] (let [colors (stringy/split homeColors #" ")
                     color1 (get colors 0)
                     color2 (get colors 2)]
                  (html/set-attr :style (str "background-color: " color1 "; " "border-color: " color2 ";")))
  [:.awayTeam] (let [colors (stringy/split awayColors #" ")
                     color1 (get colors 0)
                     color2 (get colors 2)]
                  (html/set-attr :style (str "background-color: " color1 "; " "border-color: " color2 ";")))
  [:.homeTeamImage] (html/set-attr :src homeCrestUrl)
  [:.awayTeamImage] (html/set-attr :src awayCrestUrl)
  [:.homeTeamCaption] (html/content hn)
  [:.awayTeamCaption] (html/content an)
  [:.homeTeamPlayers] (html/content (map #(singleplayersnippet %) squadHome))
  [:.awayTeamPlayers] (html/content (map #(singleplayersnippet %) squadAway)))

(defn add-day [s]
  "accepts [string] as arg in YYYY-MM-DD format and returns next days string"
  (l/format-local-time (t/plus (tf/parse (tf/formatters :year-month-day) s) (t/days 1)) :year-month-day))

(defn subtract-day [s]
  "accepts [string] as arg in YYYY-MM-DD format and returns previous days string"
  (l/format-local-time (t/minus (tf/parse (tf/formatters :year-month-day) s) (t/days 1)) :year-month-day))

(html/deftemplate matchesindex "rikhwtemplates/main.html"
  [matches day]
  [:tbody] (html/content (map #(singlematchsnippet %) matches))
  [:#yesterday] (html/set-attr :href (str "?" (codec/form-encode {:gameDate (subtract-day day)})))
  [:#tomorrow] (html/set-attr :href (str "?" (codec/form-encode {:gameDate (add-day day)}))))

(defn respond-with [mv gd]
  (ring.util.response/response (reduce str (matchesindex mv gd))))

(defn handler
  "give client matches for the day"
  [req]
  (let [gameDate  (get-in req [:params "gameDate"])
        localtime (l/format-local-time (l/local-now) :year-month-day)]
    (if gameDate
      (do 
        ;;(println "gameDate found in query string")
        (respond-with (-> ((get-data gameDate) :matches) add-teams-to-matches) gameDate))
      (do
        ;;(println "no query param found")
        ;;(print (req :query-params))
        (respond-with (-> ((get-data localtime) :matches) add-teams-to-matches) localtime)))))

(def wrapped-handler
  (-> handler wrap-params))

(defn -main []
  ;; run that server boi! port three stacks
  (jetty/run-jetty wrapped-handler {:port 3000}))
