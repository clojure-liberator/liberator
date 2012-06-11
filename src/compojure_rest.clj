;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns compojure-rest
  (:use compojure-rest.util))

(defn wrap-header [handler header generate-header]
  (fn [request]
    (let [value (apply-if-function generate-header request)
	  response (handler request)]
      (assoc-in response [:headers header] (str value))))) 

(defn wrap-if-match [handler gen-etag]
  (fn [request]
    (let [if-match (get-in request :headers (get  "if-match"))]
      (if (or (= if-match "*") (nil? if-match))
	(handler request)
	(let [etag (gen-etag request)]
	  (if (= if-match etag)
	    (handler (assoc-in request [::rest :etag] etag))
	    {:status 412 :body "precondition failed"}))))))

(defn wrap-if-none-match [handler gen-etag]
  (fn [request]
    (let [if-none-match (get-in request :headers (get  "if-none-match"))]
      (if (or (nil? if-none-match)
	      (and (not (= if-none-match "*"))
		   (not (= if-none-match (gen-etag request)))))
	(handler request)
	(if (some #{(request :request-method)} [:get :head])
	  {:status 304 }
	  {:status 412 :body "precondition failed"})))))

(defn wrap-generate-etag [handler generate-etag]
  (fn [request]
    (let [etag (or (get-in request ::rest :etag) (generate-etag request))]
      ((wrap-header handler "Etag" etag) request))))

(defn wrap-expiry [handler generate-expires]
  (wrap-header handler "Expires"
	       #(http-date (apply-if-function generate-expires %))))

(defn wrap-last-modified [handler generate-last-modified]
  (wrap-header handler "Last-Modified"
	       #(http-date (apply-if-function generate-last-modified %))))

(defn wrap-if-unmodified-since [handler generate-last-modified]
  (fn [request]
    (if-let [if-unmodified-since (get-in request :headers (get "if-unmodified-since"))]
      {:status 412 :body "if-unmodified-since not supported"}
      (handler request)
      )))

(defn wrap-if-modified-since [handler generate-last-modified]
  (fn [request]
    (if-let [if-modified-since (get-in request :headers (get "if-modified-since"))]
      {:status 412 :body "if-modified-since not supported"}
      (handler request))))


(defn wrap-predicate [handler pred else]
  (fn [request]
    (if-let [r (apply-if-function pred request)]
      (let [r2 (if (map? r) r request)]
       (handler r2))
      (apply-if-function else request))))

(defn wrap-exists [handler exists-function]
  (wrap-predicate handler exists-function (constantly { :status 404 :body "not found"})))

(defn wrap-auth [handler auth-function]
  (fn [request]
    (if (apply-if-function auth-function request)
      (handler request)
      {:status 401 :body "unauthorized"})))

(defn wrap-allow [handler allow-function]
  (fn [request]
    (if (apply-if-function allow-function request)
      (handler request)
      {:status 403 :body "forbidden"})))

(defn wrap-generate-body [handler generate-body-function-or-val]
  (fn [request]
    (if (= (:method request) :get)
      (if (fn? generate-body-function-or-val)
        (generate-body-function-or-val request)
        generate-body-function-or-val)
      (handler request))))

(defn wrap-etag [handler generate-etag]
  (-> handler
      (wrap-if-match generate-etag)
      (wrap-if-none-match generate-etag)
      (wrap-generate-etag generate-etag)))

(defn send-method-not-allowed []
  (fn [request] 
    {:status 405 :body "method not allowed"}))

(defn wrap-service-available [handler service-available-function]
  (wrap-predicate service-available-function
		  { :status 503 :body "service unavailable"}))
