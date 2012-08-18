(ns examples.server
  (:require
   [ring.adapter.jetty :as jetty])
  (:use
   [examples :only [assemble-routes]]
   [ring.middleware.multipart-params :only [wrap-multipart-params]]
   [ring.util.response :only [header]]
   [compojure.handler :only [api]]))

(defn create-handler []
  (fn [request]
    (
     (->
      (assemble-routes)
      api
      wrap-multipart-params)
     request)))

(def handler (create-handler))

(defn start [options]
  (jetty/run-jetty
   (fn [request]
     ((create-handler) request))
   (assoc options :join? false)))

(defn -main
  ([port]
     (start {:port (Integer/parseInt port)}))
  ([]
     (-main "8000")))


