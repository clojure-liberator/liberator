(ns test-util
  (:use
   liberator.util
   clojure.test)
  (:import
   (java.util Date)))


#_(deftest test-evaluate-generate
    (testing "with simple vaule"
      (is (= :v (evaluate-generate :v {}))))
    (testing "with constant function"
      (is (= :c (evaluate-generate (fn [request] :c) {}))))
    (testing "with function"
      (is (= :a (evaluate-generate (fn [request] (request :x)) { :x :a})))))

(deftest test-http-date
  (testing "with zero-date"
    (is (= "Thu, 01 Jan 1970 00:00:00 +0000" (http-date (new Date (long 0)) "UTC")))))


(deftest test-wrap-header
  (testing "with simple value"
    (is (= { :body "x" :headers { "header" "foo" }} 
	   ((wrap-header (fn [req] { :body "x"})
			 "header" "foo") {}))))
  (testing "with function"
    (is (= { :body "x" :headers { "header" "foo" }} 
	   ((wrap-header (fn [req] { :body "x"})
			 "header" (fn [req] "foo")) {})))))

