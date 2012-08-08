(ns test-response
  (:use
   [clojure.test :only [deftest is are testing run-tests]]
   [ring.mock.request :only [request header]]
   [compojure.core :only [context ANY]]
   [liberator.core :only [defresource resource]]))

;; TODO: Ensure that we use compojure.response/Renderable underneath in any body function

(deftest response-tests
  (testing "Context parameter is passed to body generator" 
    (->
     (request :get "/users/10/display")
     ((context "/users/:id" [id]
               (ANY "/display" []
                    (resource
                     :handle-ok (fn [ctx]
                                  (is (= 200 (:status ctx)))
                                  (is (= "OK" (:message ctx)))))))))))




(run-tests)



;; TODO: Add tests for ETag.

