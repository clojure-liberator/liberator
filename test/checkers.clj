(ns checkers
  "contains midje checkers to test ring responses"
  (:use midje.sweet))

(defchecker all [& checkers]
  (fn [actual] (every? #(% actual) checkers)))

(defchecker is-status [code]
  (contains {:status code}))

(defchecker body [expected]
  (contains {:body expected}))

(defchecker header-value [header expected]
  (fn [actual]
    (= (get-in actual [:headers header]) expected)))

(defchecker content-type [expected]
  (header-value "Content-Type" expected))

(def OK (is-status 200))
(def CREATED (is-status 201))
(def ACCEPTED (is-status 202))

(defn status-location [status location]
  (all (is-status status) 
       (header-value "Location" location)))

(defn SEE-OTHER [location] (status-location 303 location))
(defn MOVED-TEMPORARILY [location] (status-location 307 location))
(defn MOVED-PERMANENTLY [location] (status-location 301 location))

(def NOT-FOUND (is-status 404))
(def GONE (is-status 410))