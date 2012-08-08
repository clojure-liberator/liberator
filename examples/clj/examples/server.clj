(ns examples.server
  (:require
   [ring.adapter.jetty :as jetty])
  (:use
   [examples :only [assemble-routes]]
   [ring.middleware.multipart-params :only [wrap-multipart-params]]
   [liberator.representation :only [wrap-convert-suffix-to-accept-header]]
   [ring.util.response :only [header]]
   [compojure.handler :only [api]]))

(defn create-handler []
  (fn [request]
    (
     (->
      (assemble-routes)
      api
      wrap-multipart-params
      (wrap-convert-suffix-to-accept-header
       {".html" "text/html"
        ".csv" "text/csv"
        ".tsv" "text/tab-separated-values"
        ".txt" "text/plain"
        ".xhtml" "application/xhtml+xml"
        ".xml" "application/xml"
        ".json" "application/json"
        ".clj" "application/clojure"})
      ) request)))

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


