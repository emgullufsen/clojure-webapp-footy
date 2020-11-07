(ns rikhw.core-test
  (:require [clojure.test :refer :all]
            [rikhw.core :refer :all]
            [com.ashafa.clutch :as clutch]))

(def team-map {:homeTeam {:id 7} :awayTeam {:id 89}})

;; needs-update? = true
(def test-match-zero {:utcDate "2020-10-08T20:30:00Z" :lastUpdated "2020-10-07T22:00:00Z" :status "FINISHED"})

;; needs-update? = false
(def test-match-one {:utcDate "2020-10-07T18:30:00Z" :lastUpdated "2020-10-07T22:00:00Z" :status "FINISHED"})

;; needs-update? = true
(def test-match-two {:utcDate "2020-10-07T20:30:00Z" :lastUpdated "2020-10-07T22:00:00Z" :status "IN_PROGRESS"})

(deftest ^:with-db assemble-data-check
  (let [data-to-check (assemble-page-data "2020-11-07")]
    (is (not (nil? data-to-check)))))

(deftest ^:with-db contact-db-check
  (let [dbdocky (clutch/with-db (get-db-string-testeric2) (clutch/get-document "2020-11-07"))]
    (is (not (nil? (dbdocky :matches))))))

(deftest needs-update?-check
  (is (= [true false true] (map needs-update? [test-match-zero test-match-one test-match-two]))))

(deftest get-home-id-check
    (is (= (get-home-id team-map) 7)))
