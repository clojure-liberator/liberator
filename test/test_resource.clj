(ns test-resource
  (:use clojure.test
        [clojure.tools.trace :only (trace)]
        [ring.mock.request :only [request header]]
        compojure-rest.resource))

(deftest test-simplest-get-ok
  (testing "simplest case"
    (let [res (resource)
          response (res (request :get "/"))]
      (is (= "OK" (:body (trace "R1" response)))))))

(deftest test-handle-post
  (let [res (resource 
	   :method-allowed? [:post]
	   :can-post-to-missing? true
	   :post-is-create? true
           :post-redirect? true
	   :see-other "new-path")
	resp (trace "resp" (res {:request-method :post :header {}}))]
    (testing "post creates path"
      (is (= 303 (resp :status)))
      (is (= "new-path" (get-in resp [:headers "Location"]))))))

