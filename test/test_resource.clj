;; Originally this was compojure_rest/t_resource.clj in src/

(ns test-resource
  (:use clojure.test)
  (:use compojure-rest.resource))

(deftest test-handle-post
  (let [res (resource 
	   :method-allowed [:post]
	   :allow-missing-post true
	   :post-is-create true
	   :create-path "new-path" 
	   )
	resp (trace "resp" (res {:request-method :post :header {}}))]
    (testing "post creates path"
      (is (= 301 (resp :status)))
      (is (= "new-path" ((resp :header) "Location"))))))

