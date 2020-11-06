(ns rikhw.core-test
  (:require [clojure.test :refer :all]
            [rikhw.core :refer :all]))

(def team-map {:homeTeam {:id 7} :awayTeam {:id 89}})

(deftest get-home-id-check
    (is (= (get-home-id team-map) 7)))
