(ns checkers
  "contains midje checkers to test ring responses"
  (:use midje.sweet))

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
(defn SEE-OTHER [location]
  (fn [actual]
    (fact actual => (is-status 303)) 
    (fact actual => (header-value "Location" location))))


(defchecker all [& checkers]
  (fn [actual] (every? #(% actual) checkers)))