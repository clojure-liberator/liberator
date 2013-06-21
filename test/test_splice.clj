(ns test-splice
  (:require [splice :refer [todo-list todo ->ToDo]]
            [ring.mock.request :as mock :refer [request header]]
            [compojure.core :refer [ANY]]
            [compojure.handler :refer [api]]
            [midje.sweet :refer [fact facts truthy against-background contains future-fact future-facts defchecker tabular before]]
            [checkers :refer :all]))

(facts "about POST"
  (tabular
   (facts "with empty list"
     (with-state-changes [(before :facts (reset! todo-list []))]
       (let [handler (api (ANY "/todo" [] todo))
             req (-> (request :post "/todo")
                     (mock/body ?body))
             response (handler req)]
         (fact "has expected status"
           response => (contains {:status ?status}))
         (fact "has expected content"
           response => (body ?result)))))
   ?body                           ?status            ?result
   {:name "Do it" :checked false}  201                "Created"
   {:name "It Do"}                 201                "Created"
   {:name "invalid"}               422                "Entity is invalid"
   {}                              422                "Entity is invalid")

  (tabular
   (with-state-changes [(before :facts (reset! todo-list [(->ToDo "Buy Milk" false)]))]
     (facts "with an existing ToDo"
       (let [handler (api (ANY "/todo" [] todo))
             req (-> (request :post "/todo")
                     (mock/body ?body))
             response (handler req)]
         (fact "has expected status"
           response => (contains {:status ?status}))
         (fact "has expected content"
           response => (body ?result)))))
   ?body                          ?status            ?result
   {:name "Buy Milk"}             422                "Entity is invalid"
   {:name "Buy Soda"}             201                "Created"))

(facts "about PUT"
  (tabular
   (with-state-changes [(before :facts (reset! todo-list [(->ToDo "Buy Milk" false)]))]
     (facts "with an existing ToDo"
       (let [handler (api (ANY "/todo" [] todo))
             req (-> (request :put "/todo")
                     (mock/body ?body))
             response (handler req)]
         (fact "has expected status"
           response => (contains {:status ?status}))
         (fact "has expected content"
           response => (body ?result)))))
   ?body                            ?status            ?result
   {:name "Buy Milk" :checked true} 204                "Updated"
   {:name "Buy Soda"}               422                "Entity is invalid"))
