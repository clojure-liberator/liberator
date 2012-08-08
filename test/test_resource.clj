(ns test-resource
  (:use clojure.test)
  (:use liberator.core))

(deftest test-handle-post
  (let [res (resource 
	   :method-allowed? [:post]
	   :can-post-to-missing? true
	   :post-is-create? true
           :post-redirect? true
	   :see-other "new-path")
	resp (res {:request-method :post :header {}})]
    (testing "post creates path"
      (is (= 303 (resp :status)))
      (is (= "new-path" (get-in resp [:headers "Location"]))))))

