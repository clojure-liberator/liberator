(ns test-defresource
  (:require [midje.sweet :refer [facts fact]]
            [liberator.core :refer [defresource resource]]
            [ring.mock.request :refer [request header]]))

(defmulti with-multimethod* identity)

(defmethod with-multimethod* :default [_]
  "with-multimethod")

(defresource with-multimethod
  :handle-ok with-multimethod*)

(defmulti with-service-available?-multimethod*
  (comp :service-available? :request))

(defmethod with-service-available?-multimethod* :available [_] true)

(defmethod with-service-available?-multimethod* :not-available [_] false)

(defresource with-decisions-multimethod
  :service-available? with-service-available?-multimethod*
  :handle-ok (fn [_] "with-service-available?-multimethod"))

(defresource without-param
  :handle-ok (fn [_] (format "The text is %s" "test")))

(defresource parameter [txt]
  :handle-ok (fn [_] (format "The text is %s" txt))
  :available-media-types ["application/xml"])

(def standard-config
  {:available-media-types ["application/json"]})

(defresource with-options
  standard-config
  :handle-ok (fn [_] (format "The text is %s" "this")))

(defresource with-options-and-params [txt]
  standard-config
  :handle-ok (fn [_] (format "The text is %s" txt))
  :available-media-types ["application/xml"])  ;; this actually overrides the standard-config

(defresource with-options-only
  standard-config)

(defn parametrized-config
  [media-type]
  {:available-media-types [media-type]})

(defresource with-options-parametrized-config [media-type txt]
  (parametrized-config media-type)
  :handle-ok (fn [_] (format "The text is %s" txt)))

(defresource non-anamorphic-request [request]
  :handle-ok (str request))

(facts "about defresource"
       (fact "its simple form should behave as it always has"
             (without-param {:request-method :get})
             => {:headers {"Content-Type" "text/plain;charset=UTF-8"}, :body "The text is test", :status 200}
             ((parameter "a test") {:request-method :get})
             => {:headers {"Vary" "Accept", "Content-Type" "application/xml;charset=UTF-8"}, :body "The text is a test", :status 200})
       (fact "when provided a standard config, it should add this to the keyword list"
             (with-options {:request-method :get})
             => {:headers {"Vary" "Accept", "Content-Type" "application/json;charset=UTF-8"}, :body "The text is this", :status 200}
             ((with-options-and-params "something") {:request-method :get})
             => {:headers {"Vary" "Accept", "Content-Type" "application/xml;charset=UTF-8"}, :body "The text is something", :status 200})
       (fact "it should also work with a function providing the standard config"
             ((with-options-parametrized-config "application/json" "a poem") {:request-method :get})
             => {:headers {"Vary" "Accept", "Content-Type" "application/json;charset=UTF-8"}, :body "The text is a poem", :status 200})
       (fact "it should work with only a standard config"
             (with-options-only {:request-method :get})
             => {:headers {"Vary" "Accept", "Content-Type" "application/json;charset=UTF-8"}, :body "OK", :status 200})
       (fact "should allow multi methods as handlers"
             (with-multimethod {:request-method :get})
             => {:headers {"Content-Type" "text/plain;charset=UTF-8"}, :body "with-multimethod", :status 200})
       (fact "should allow multi methods as decisions"
             (with-decisions-multimethod {:request-method :get :service-available? :available})
             => {:headers {"Content-Type" "text/plain;charset=UTF-8"}, :body "with-service-available?-multimethod", :status 200})
       (fact "should allow multi methods as decisions alternate path"
             (with-decisions-multimethod {:request-method :get :service-available? :not-available})
             => {:headers {"Content-Type" "text/plain;charset=UTF-8"}, :body "Service not available.", :status 503})
       (fact "should allow 'request' to be used as a resource parameter name, this was a bug at a time."
             (:body ((non-anamorphic-request "test") {:request-method :get}))
             => "test"))


(def fn-with-options
  (resource
   standard-config
   :handle-ok (fn [_] (format "The text is %s" "this"))))

(def fn-with-options-only
  (resource
   standard-config))

(def fn-with-options-and-parametrized-config
  (resource
   (parametrized-config "application/json")
   :handle-ok (fn [_] (format "The text is %s" "this"))))

(facts "using resource function"
  (fact "when provided a standard config, it should add this to the keyword list"
    (fn-with-options {:request-method :get})
    => {:headers {"Vary" "Accept", "Content-Type" "application/json;charset=UTF-8"}, :body "The text is this", :status 200}
    (fn-with-options-and-parametrized-config {:request-method :get})
    => {:headers {"Vary" "Accept", "Content-Type" "application/json;charset=UTF-8"}, :body "The text is this", :status 200})
    (fn-with-options-only {:request-method :get})
    => {:headers {"Vary" "Accept", "Content-Type" "application/json;charset=UTF-8"}, :body "OK", :status 200})
