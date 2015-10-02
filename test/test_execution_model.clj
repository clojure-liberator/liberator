(ns test-execution-model
  (:use
   midje.sweet
   [ring.mock.request :only [request header]]
   [liberator.core :only [defresource resource]]
   [liberator.representation :only [ring-response]]))


(facts "truethy return values"
  (fact (-> (request :get "/")
             ((resource :exists? true)))
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
  (fact "handler is a function"
    (-> (request :get "/")
        ((resource :exists? false
                   :handle-not-found (fn [ctx] "not found"))))
    => (contains {:status 404 :body "not found"}))
  (fact "keyword as handler"
        (-> (request :get "/")
            ((resource :exists? {:some-key "foo"}
                       :handle-ok :some-key)))
        => (contains {:status 200 :body "foo"}))
  (fact "default handler uses message key"
        (-> (request :get "/")
            ((resource :exists? [false {:message "absent"}])))
        => (contains {:status 404 :body "absent"}))
  (fact "decisions can override status"
        (-> (request :get "/")
            ((resource :exists? [false {:status 444 :message "user defined status code"}])))
        => (contains {:status 444 :body "user defined status code"})))

(facts "context merge leaves nested objects intact (see #206)"
  (fact "using etag and if-match"
    (-> (request :put "/")
        (header "if-match" "\"1\"")
        ((resource :allowed-methods [:put]
                   :available-media-types ["application/edn"]
                   :malformed? [false {:my-entity {:deeply [:nested :object]}}]
                   :handle-created :my-entity
                   :etag "1")))
    => (contains {:status 201, :body "{:deeply [:nested :object]}"}))
  (fact "using if-unmodified-since"
    (-> (request :put "/")
        (header "if-unmodified-since" "Tue, 15 Nov 1994 12:45:26 GMT")
        ((resource :allowed-methods [:put]
                   :available-media-types ["application/edn"]
                   :malformed? [false {:my-entity {:deeply [:nested :object]}}]
                   :handle-created :my-entity
                   :last-modified (java.util.Date. 0))))
    => (contains {:status 201, :body "{:deeply [:nested :object]}"})))
