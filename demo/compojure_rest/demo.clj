(ns compojure-rest.demo
  (:use compojure.core compojure-rest.resource)
  (:require [clout.core :as clout]
            [compojure.route :as route]
            compojure.handler
            [hiccup.core :as hiccup]))

;; How do we merge routes?

(def users (atom []))
(def users-update-count (atom 0))

(comment
  (add-watch users :update-counter
             (fn [k users old new] (swap! users-update-count inc)))

  (deref users-update-count))

(defroutes main-routes
  (context "/users/:id" [id]
           (ANY "/foo" [] (resource
                           :get {"text/html"
                                 (format "<body>User is %s</body" id)}
                           )))
  (route/not-found "<h1>Page not found</h1>"))

(defn build-handler [routes]
  (-> routes
      compojure.handler/api)
  )

(defn root-handler [request]
  (let [handler (build-handler main-routes)]
    (handler request))
  )

(defn create-root-handler []
  (fn [request] (root-handler request)))

