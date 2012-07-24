(ns test-examples
  (:require examples)
  (:use
   [liberator.representation :only [->when]]
   [ring.mock.request :only [request header]]
   [compojure.core :only [ANY]]
   [liberator.core  :only [resource]]
   [midje.sweet :only [fact facts truthy against-background contains future-facts defchecker tabular]]))

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
  (let [handler (ANY "/" [] examples/hello-world)
        response (handler (request :get "/"))]
    response => OK
    response => (has-body "Hello World!")
    response => (has-content-type "text/plain")
    ))

(facts "about language negotiation"
  (tabular
   (fact "about hello-george example"
     (let [handler (ANY "/" [] examples/hello-george)]
       (handler (-> (request :get "/")
                    (->when ?lang (header "Accept-Language" ?lang))))) => ?expected)
   ?lang     ?expected
   nil       OK
   nil       (has-body "Hello!")
   "en"      OK
   "en"      (has-body "Hello George!")
   "bg"      OK
   "bg"      (has-body "Zdravej, Georgi")))

(comment
  (future-facts "about post"
                (let [handler (ANY "/orders")])
                ))