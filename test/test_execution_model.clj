(ns test-execution-model
  (:use
   midje.sweet
   [ring.mock.request :only [request header]]
   [liberator.core :only [defresource resource]]
   [liberator.representation :only [ring-response]]))


(facts "truethy return values"
  (fact (-> (request :get "/")
             ((resource :exists?y true)))
    => (contains {:status 200}))
  (fact (-> (request :get "/")
             ((resource :exists? 1)))
    => (contains {:status 200}))
  (fact "map merged with context"
    (-> (request :get "/")
        ((resource :exists? {:a 1}
                   :handle-ok #(ring-response %))))
    => (contains {:a 1}))
  (fact "vector and map merged with context"
    (-> (request :get "/")
        ((resource :exists? [true  {:a 1}]
                   :handle-ok #(ring-response %))))
    => (contains {:a 1 :status 200}))
  (fact "vector concated to context value"
    (-> (request :get "/")
        ((resource :service-available? {:a [1]}
                   :exists? {:a [2]}
                   :handle-ok #(ring-response %))))
    => (contains {:a [1 2] :status 200}))
  (fact "function returned as context is evaluated"
        (-> (request :get "/")
            ((resource :service-available? {:a [1]}
                       :exists? (fn [ctx] #(assoc ctx :a [2]))
                       :handle-ok #(ring-response %))))
        => (contains {:a [2] :status 200})))

(facts "falsey return values"
  (fact (-> (request :get "/")
             ((resource :exists? false)))
    => (contains {:status 404}))
  (fact (-> (request :get "/")
             ((resource :exists? nil)))
    => (contains {:status 404}))
  (fact "vector and map merged with context"
    (-> (request :get "/")
        ((resource :exists? [false {:a 1}]
                   :handle-not-found #(ring-response %))))
    => (contains {:a 1 :status 404})))

(facts "handler functions"
  (fact "keyword as handler"
    (-> (request :get "/")
        ((resource :exists? {:some-key "foo"}
                   :handle-ok :some-key)))
    => (contains {:status 200 :body "foo"})))
