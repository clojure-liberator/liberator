(ns test-resource
  (:use clojure.test)
  (:use liberator.core))

(def url "http://clojure-liberator.github.io")

(deftest test-handle-post
  (doseq [location [url
                    (java.net.URL. url)
                    (java.net.URI. url)]]
    (let [res (resource
               :method-allowed? [:post]
               :can-post-to-missing? true
               :post-is-create? true
               :post-redirect? true
               :location location)
          resp (res {:request-method :post :header {}})]
      (testing "post creates path"
        (is (= 303 (resp :status)))
        (is (= url (get-in resp [:headers "Location"])))))))
