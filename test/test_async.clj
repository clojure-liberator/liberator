(ns test-async
  (:use clojure.test)
  (:use liberator.core)
  (:require [clojure.core.async :refer (<!! timeout)]
            [liberator.async :refer (go? <?)])
  (:import (javax.xml.ws ProtocolException)))

(deftest test-handle-async-post
  (let [res (resource 
       :async? true
       :exists? (fn [_] (go? (<? (timeout 5)) false))
	   :method-allowed? [:post]
	   :can-post-to-missing? true
	   :post-is-create? true
       :post-redirect? true
       :post! (fn [ctx] (go? (<? (timeout 5)) true))
	   :location "new-path")
    resp (<!! (:body (res {:request-method :post :header {}})))]
    (testing "post creates path"
      (is (= 303 (resp :status)))
      (is (= "new-path" (get-in resp [:headers "Location"]))))))

(deftest test-handle-exception
  (let [res (resource
       :async? true
       :exists?
       (fn [_]
         (go? (throw (IllegalArgumentException. "Something went wrong"))))
	   :method-allowed? [:post]
	   :can-post-to-missing? true
	   :post-is-create? true
       :post-redirect? true
	   :location "new-path")
    resp (<!! (:body (res {:request-method :post :header {}})))]
    (testing "exception thrown"
      (is (instance? Exception resp)))))

(deftest test-handle-protocol-exception
  (let [res (resource
       :async? true
       :exists?
       (fn [_]
         (go? (throw (ProtocolException. "ProtocolException"))))
	   :method-allowed? [:post]
	   :can-post-to-missing? true
	   :post-is-create? true
       :post-redirect? true
	   :location "new-path")
    resp (<!! (:body (res {:request-method :post :header {}})))]
    (testing "protocol exceptions return error response"
      (is (= 400 (resp :status))
          (= "ProtocolException" (resp :body))))))


