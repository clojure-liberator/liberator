(ns test-examples
  (:require examples)
  (:use
   [ring.mock.request :only [request header]]
   [compojure.core :only [ANY]]
   [liberator.core  :only [resource]]
   [midje.sweet :only [facts truthy against-background contains future-facts defchecker]]))

(defchecker is-status [code]
  (contains {:status code}))

(defchecker has-body [expected]
  (contains {:body expected}))

(defchecker has-header-value [header expected]
  (fn [actual]
    (= (get-in actual [:headers header]) expected)))

(defchecker has-content-type [expected]
  (has-header-value "Content-Type" expected))

(def OK (is-status 200))

(facts "about a simple GET"
  (let [handler (ANY "/hello" [] examples/hello-world)
        response (handler (request :get "/hello"))]
    response => OK
    response => (has-body "Hello World!")
    response => (has-content-type "text/plain")
    ))

(comment
  (future-facts "about post"
                (let [handler (ANY "/orders")])
                ))