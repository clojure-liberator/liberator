(ns checkers
  "contains midje checkers to test ring responses"
  (:use midje.sweet
        [clojure.string :only (lower-case)]))

(defchecker ignore-case [expected]
  (fn [actual] (or (and (nil? actual) (nil? expected))
                  (= (lower-case actual) (lower-case expected)))))

(defchecker all [& checkers]
  (fn [actual] (every? #(% actual) checkers)))

(defchecker is-status [code]
  (contains {:status code}))

(defchecker body [expected]
  (contains {:body expected}))

(defchecker no-body []
  (fn [actual] (nil? (:body actual))))

(defchecker header-value [header expected]
  (contains {:headers (contains {header expected})}))

(defchecker content-type [expected]
  (header-value "Content-Type" expected))

(def OK (is-status 200))
(def CREATED (is-status 201))
(def ACCEPTED (is-status 202))
(def NO-CONTENT (all (is-status 204) (body nil?)))

(defn status-location [status location]
  (all (is-status status) 
       (header-value "Location" location)))

(defn MOVED-PERMANENTLY [location] (status-location 301 location))
(defn SEE-OTHER [location] (status-location 303 location))
(def  NOT-MODIFIED (is-status 304))
(defn MOVED-TEMPORARILY [location] (status-location 307 location))

(def NOT-FOUND (is-status 404))
(def GONE (is-status 410))
(def PRECONDITION-FAILED (is-status 412))

(def INTERNAL-SERVER-ERROR (is-status 500))
(def NOT-IMPLEMENTED (is-status 501))

