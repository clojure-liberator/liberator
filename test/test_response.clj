(ns test-response
  (:use
   midje.sweet
   [ring.mock.request :only [request header]]
   [compojure.core :only [context ANY]]
   [liberator.core :only [defresource resource]]))

;; TODO: Ensure that we use compojure.response/Renderable underneath in any body function

(facts
 (->
            (request :get "/users/10/display")
            ((context "/users/:id" [id]
                      (ANY "/display" []
                           (resource
                            :handle-ok {"text/plain" (format "User id is %s" id)}))))
            :body)
 => "User id is 10")  


(comment
  (deftest response-tests
    
    (testing "Content negotiation"
      (are [accept content-type expected-type]
           (= expected-type
              (-> (request :get "/")
                  (header "Accept" accept)
                  ((resource :get {content-type "Some content"}))
                  (get-in [:headers "Content-Type"])))
           "text/html" "text/html" "text/html"
           "text/plain" "text/plain" "text/plain"
           "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" "text/html" "text/html"
           "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" "text/html" "text/html"
           ))))

;; TODO: Add tests for ETag.

