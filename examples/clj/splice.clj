(ns splice
  (:require [liberator.dev :as dev])
  (:use [liberator.core :only [defresource defhandler defdecision request-method-in post! put! handle-conflict =method]]
        [compojure.core :only [ANY routes]]))

; simulated domain model
(def todo-list (atom []))

(defn todo-by-name [name]
  (first (filter #(= name (get % :name)) @todo-list)))

(defprotocol Validatable
  (valid? [_ _]))

(defrecord ToDo [name checked]
  Validatable
  (valid? [this save-context]
    (let [valid (if (= :create save-context)
                  (not (or (or (empty? name) (= name "invalid")) ; has to have a valid name
                           (not (nil? (todo-by-name name))) ; name must not be present
                           (nil? checked)))
                  ; update
                  (not (or (empty? name)         ; has to have a name
                           (nil? (todo-by-name name))    ; name must be present
                           )))]
      valid)))

(defn request->ToDo
  [request]
  (if-let [params (or (:params request) (:form-params request))]
    (->ToDo (:name params) (or (:checked params) false))
    (->ToDo nil nil)))

; new handlers and decisions

(defhandler handle-unprocessable-entity 422 "Entity is invalid")

(defdecision post-entity-valid? post! handle-unprocessable-entity)

(defdecision put-entity-valid?  put! handle-unprocessable-entity)

; resource that splices is new flows

(defresource todo
  :method-allowed? (request-method-in :get :post :put)
  :available-media-types ["text/html"]
  :new? (fn [ctx] (=method :post ctx))
  :post-to-existing? (fn [ctx]
                       (if (=method :post ctx)
                         post-entity-valid?
                         false))
  :post-entity-valid? (fn [ctx]
                        (let [entity (request->ToDo (get ctx :request))]
                          [(.valid? entity :create) {:entity entity}]))
  :post! (fn [ctx]
           (swap! todo-list conj (:entity ctx)))
  :handle-created "Created"

  :conflict? (constantly put-entity-valid?)
  :put-entity-valid? (fn [ctx]
                       (let [entity (request->ToDo (get ctx :request))]
                         [(.valid? entity :update) {:entity entity}]))
  ; fake put here
  :put! (constantly true)
  :handle-no-content "Updated")

(defn assemble-routes []
  (->
   (routes
    (ANY "/todo" [] todo))
   (dev/wrap-trace :ui :header)))
