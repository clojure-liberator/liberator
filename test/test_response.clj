(ns test-response
  (:use
   [clojure.test :only [deftest is are testing run-tests]]
   [ring.mock.request :only [request header]]
   [compojure.core :only [context ANY]]
   [liberator.core :only [defresource resource]]))

;; TODO: Ensure that we use compojure.response/Renderable underneath in any body function

(deftest response-tests
  (testing "Context parameter is passed to body generator" 
    (is (= "User id is 10"
           (->
            (request :get "/users/10/display")
            ((context "/users/:id" [id]
                      (ANY "/display" []
                           (resource
                            :get {"text/plain" (format "User id is %s" id)}))))
            :body))))
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
         )))


(run-tests)



;; TODO: Add tests for ETag.

