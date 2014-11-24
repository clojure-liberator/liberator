(ns test-override-as-response
  (:use
   midje.sweet
   [ring.mock.request :only [request header]]
   [liberator.core :only [defresource resource]]
   [liberator.representation :as rep]))


(facts "as-response can be overriden"
  (fact "custom as-reponse's ring response is not coerced into content-type"
        ((resource :available-media-types ["application/json"]
                   :handle-ok (fn [_] "some string")
                   :as-response (fn [d ctx] {:status 666 :body d}))

         (request :get "/"))
        => (contains {:body "some string"
                      :headers (contains  {"Content-Type" "application/json"})
                      :status 666}))

  (fact "necessary headers are added"
        ((resource :available-media-types ["application/json"]
                   :handle-ok (fn [_] "some string")
                   :as-response (fn [d ctx] {:body d}))
         (request :get "/"))
        => (contains {:headers (contains {"Content-Type" "application/json"
                                          "Vary" "Accept"})
                      :status 200
                      :body "some string"}))

  (fact "custom as-reponse can call default as-response"
        ((resource :available-media-types ["text/plain"]
                   :handle-ok (fn [_] "some text")
                   :as-response (fn [d ctx] (assoc-in (rep/as-response d ctx)
                                                     [:headers "X-FOO"] "BAR")))
         (request :get "/"))
        => (contains {:body "some text"
                      :headers (contains {"X-FOO" "BAR"})
                      :status 200}))

  (fact "custom as-response works with default handlers"
        ((resource :available-media-types ["text/plain"]
                   :as-response (fn [d ctx] {:foo :bar}))
         (-> (request :get "/")
             (header "Accept" "foo/bar")))
        => (contains {:foo :bar })))
