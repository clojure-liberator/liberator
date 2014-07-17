(ns test-resource-definition
  (:use liberator.core
        midje.sweet))

;; test cases for different resource definitions

(defn dump-representation [parameter] #(get-in % [:representation parameter] "-"))

(fact "default media-type negotiation uses :available-media-types"
  (let [r (resource :available-media-types ["text/html"]
                    :handle-ok (dump-representation :media-type))]
      (r {:request-method :get :headers {"accept" "text/html"}})
      => (contains {:body "text/html"})))

(fact "custom media-type negotiation with :media-type-available?"
  (let [r (resource :media-type-available?
                    (fn [ctx]
                      {:representation {:media-type "text/html"}})
                    :handle-ok (dump-representation :media-type))]
    (r {:request-method :get :headers {"accept" "text/html"}})
    => (contains {:body "text/html"})))

(fact "custom media-type negotiation with :media-type-available?"
  (let [r (resource :media-type-available?
                    (fn [ctx]
                      {:representation {:media-type "text/html"}})
                    :handle-ok (dump-representation :media-type))]
    (r {:request-method :get :headers {"accept" "text/html"}})
    => (contains {:body "text/html"})))

(fact "default language negotiation uses :available-languages"
  (let [r (resource :available-languages ["en" "de" "fr"]
                    :handle-ok (dump-representation :language))]
    (r {:request-method :get :headers {"accept-language" "fr"}})
    => (contains {:body "fr"})))