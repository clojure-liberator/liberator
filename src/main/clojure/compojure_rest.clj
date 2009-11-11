;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns compojure-rest
  (:use compojure)
  (:use clojure.contrib.core)
  (:import java.util.Date)
  (:import java.lang.System)
  (:import java.util.Locale)
  (:import java.text.SimpleDateFormat))


(defn evaluate-generate [function-or-value request]
  (if (fn? function-or-value)
    (function-or-value request)
    function-or-value))

(def *http-date-format*
     (new SimpleDateFormat
          "EEE, dd MMM yyyy HH:mm:ss Z"
          Locale/US))

(defn http-date [int-or-date]
  (if-let [date (if (integer? int-or-date)
		  (new Date (+ int-or-date (System/currentTimeMillis)))
		  int-or-date)]
    (.format *http-date-format* date)))

(defn wrap-header [handler header generate-header]
  (fn [request]
    (let [value (evaluate-generate generate-header request)
	  response (handler request)]
      (assoc-in response [:headers header] (str value))))) 

(defn wrap-if-match [handler gen-etag]
  (fn [request]
    (let [if-match (-?> request :headers (get  "if-match"))]
      (if (or (= if-match "*") (nil? if-match))
	(handler request)
	(let [etag (gen-etag request)]
	  (if (= if-match etag)
	    (handler (assoc-in request [::rest :etag] etag))
	    {:status 412 :body "precondition failed"}))))))

(defn wrap-if-none-match [handler gen-etag]
  (fn [request]
    (let [if-none-match (-?> request :headers (get  "if-none-match"))]
      (if (or (nil? if-none-match)
	      (and (not (= if-none-match "*"))
		   (not (= if-none-match (gen-etag request)))))
	(handler request)
	(if (some #{(request :request-method)} [:get :head])
	  {:status 304 }
	  {:status 412 :body "precondition failed"})))))

(defn wrap-generate-etag [handler generate-etag]
  (fn [request]
    (let [etag (or (-?> request ::rest :etag) (generate-etag request))]
      ((wrap-header handler "Etag" etag) request))))

(defn wrap-expiry [handler generate-expires]
  (wrap-header handler "Expires"
	       #(http-date (evaluate-generate generate-expires %))))

(defn wrap-last-modified [handler generate-last-modified]
  (wrap-header handler "Last-Modified"
	       #(http-date (evaluate-generate generate-last-modified %))))

(defn wrap-exists [handler exists-function]
  (fn [request]
    (if (exists-function request)
      (handler request)
      {:status 404 :body "not found"})))

(defn wrap-auth [handler auth-function]
  (fn [request]
    (if (auth-function request)
      (handler request)
      {:status 401 :body "unauthorized"})))

(defn wrap-allow [handler allow-function]
  (fn [request]
    (if (allow-function request)
      (handler request)
      {:status 403 :body "forbidden"})))

(defn wrap-generate-body [handler generate-body-function-or-val]
  (fn [request]
    (if (match-method :get request)
      (compojure.http.response/create-response
       request
       (if (fn? generate-body-function-or-val)
	 (generate-body-function-or-val request)
	 generate-body-function-or-val))
      (handler request))))

(defn wrap-etag [handler generate-etag]
  (-> handler
      (wrap-if-match generate-etag)
      (wrap-if-none-match generate-etag)
      (wrap-generate-etag generate-etag)))

(defn method-not-allowed []
  (fn [request] 
    (compojure.http.response/create-response
     request {:status 405 :body "method not allowed"})))
