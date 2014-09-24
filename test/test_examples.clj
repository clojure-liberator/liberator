(ns test-examples
  (:require examples)
  (:use
   [liberator.representation :only [->when]]
   [ring.mock.request :only [request header]]
   [compojure.core :only [ANY]]
   [liberator.core  :only [resource with-console-logger]]
   [midje.sweet :only [fact facts truthy against-background contains future-fact future-facts defchecker tabular before]]
   [checkers]))

(facts "about a simple GET"
  (let [handler (ANY "/" [] examples/hello-world)
        response (handler (request :get "/"))]
    response => OK
    response => (body "Hello World!")
    response => (content-type "text/plain;charset=UTF-8")
    ))

(facts "about language negotiation"
  (let [handler (ANY "/" [] examples/hello-george)]
    
    (tabular
     (fact "about hello-george example"
       (handler (-> (request :get "/")
                    (->when ?lang (header "Accept-Language" ?lang)))) => ?expected)
     ?lang     ?expected
     nil       OK
     nil       (body "Hello!")
     "en"      OK
     "en"      (body "Hello George!")
     "bg"      OK
     "bg"      (body "Zdravej, Georgi"))

    (future-facts
     (tabular
      (fact "about hello-george example"
        (handler (-> (request :get "/")
                     (->when ?lang (header "Accept-Language" ?lang)))) => ?expected)
      ?lang     ?expected
      "en-gb"   OK
      "en-gb"   (body "Hello George!")
      "en"      OK
      "en"      (body "Hello George!")
      "*"       (body "(check rfc-2616)")))))

(facts "about POST"
  (let [handler (ANY "/" [] examples/postbox)
        response (handler (request :post "/"))]
    response => CREATED
    response => (body (contains "Your submission was accepted."))
    @examples/postbox-counter => 1)
  (against-background (before :facts (reset! examples/postbox-counter 0))))

;; TODO: Make defresource take arguments which produce resources

(facts "about content negotiation"
  (tabular
   (facts "about chameleon example"
     (let [handler (ANY "/" [] (examples/chameleon ?available))
           req (-> (request :get "/")
                   (->when ?accept (header "Accept" ?accept)))
           response (handler req)]
       (fact "has expected status"
         response => (contains {:status ?status}))
       (fact "has expected content-type"
         response => (content-type ?content-type))))

   ?accept              ?available         ?status    ?content-type
   "text/html"          ["text/html"]      200        "text/html;charset=UTF-8"
   "text/plain"         ["text/html"]      406        "text/plain;charset=UTF-8"))

