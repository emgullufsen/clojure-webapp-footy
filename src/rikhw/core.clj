(ns rikhw.core
  (:require [ring.util.response]))

(defn foo
  "I don't do a whole lot."
  [req]
  (ring.util.response/response "Hey (created with ring.util.response)")
  ;;{ :status 200 :headers {"content-type" "text/html"} :body "Hey"}
  ;;(ring.util.response/response "Hey man whatup...")
  )  
