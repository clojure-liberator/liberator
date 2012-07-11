(ns liberator.demo.server
  (:use liberator.demo)
  (:require
   swank.swank
   [ring.adapter.jetty :as jetty]))

(swank.swank/start-server :host "localhost" :port 4005)

(comment
  (jetty/run-jetty (create-root-handler)
                   {:port 8000}))

