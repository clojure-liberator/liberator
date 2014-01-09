(ns test-defresource
  (:require [midje.sweet :refer [facts fact]]
            [liberator.core :refer [defresource]]))

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

(defn parametrized-config
  [media-type]
  {:available-media-types [media-type]})

(defresource with-options-parametrized-config [media-type txt]
  (parametrized-config media-type)
  :handle-ok (fn [_] (format "The text is %s" txt)))

(facts "about defresource"
       (fact "its simple form should behave as it always has"
             ((without-param) {:request-method :get})
             => {:headers {"Content-Type" "text/plain;charset=UTF-8"}, :body "The text is test", :status 200}
             ((parameter "a test") {:request-method :get})
             => {:headers {"Vary" "Accept", "Content-Type" "application/xml;charset=UTF-8"}, :body "The text is a test", :status 200})
       (fact "when provided a standard config, it should add this to the keyword list"
             ((with-options) {:request-method :get})
             => {:headers {"Vary" "Accept", "Content-Type" "application/json;charset=UTF-8"}, :body "The text is this", :status 200}
             ((with-options-and-params "something") {:request-method :get})
             => {:headers {"Vary" "Accept", "Content-Type" "application/xml;charset=UTF-8"}, :body "The text is something", :status 200})
       (fact "it should also work with a function providing the standard config"
             ((with-options-parametrized-config "application/json" "a poem") {:request-method :get})
             => {:headers {"Vary" "Accept", "Content-Type" "application/json;charset=UTF-8"}, :body "The text is a poem", :status 200}))

