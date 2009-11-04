;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns compojure-rest
  (:use compojure))

(declare handle-allowed handle-accept handle-authorized create-response)

(def -const-nil (constantly nil))

(def default-resource-functions
     { :valid-method (fn [req]
		       (let [functions (req :functions)]
			 (contains? functions (req :request-method))))
      :head -const-nil
      :etag -const-nil
      :last-modified -const-nil
      :expires -const-nil
      :allowed? (constantly true)
      :authorized? (constantly true)
      :content-type-provided #(some #{((% :headers) "accept")} ["text/html" "*/*"])
      })

;; returns a 2-vector [result new-request]
;; vector-or-f-or-result if a function may return result or  [result req']
;; vector-or-f-or-result may be result (single value)
;; vector-or-f-or-result may be 2-vector
(defn with-request [req vector-or-f-or-result]
  (let [vector-or-result (if (fn? vector-or-f-or-result)
			   (vector-or-f-or-result req)
			   vector-or-f-or-result)
	vector (if (sequential? vector-or-result)
		 vector-or-result 
		 [vector-or-result req])]
    (do
      (prn "with-request " vector)
      vector)))



(defn filter-nil-values [m]
  (into {} (filter (fn [[_ v]] (not (nil? v))) m)))

(def *http-date-format*
     (new java.text.SimpleDateFormat
	  "EEE, dd MMM yyyy HH:mm:ss Z"
	  java.util.Locale/US))

(defn http-date [int-or-date]
  (if-let [date (if (integer? int-or-date)
	       (new java.util.Date (+ int-or-date (java.lang.System/currentTimeMillis)))
	       int-or-date)]
    (.format *http-date-format* date)))

;; todo use cl-conneg instead of "contains"
(defn negotiate-content-type [string-or-fn req]
  (if (fn? string-or-fn)
    (string-or-fn req)
    (let [accept ((req :headers) "accept" "text/html")]
      (if (and (not (nil? accept)) (.contains accept string-or-fn))
	string-or-fn))))

(defn handle-request  [req]
  (let [functions (req :functions) 
	valid-method (functions :valid-method)
	[valid req] (with-request req valid-method)]
    (if-not valid 
      [501 "Not implemented"]
      (handle-accept req))))

(defn handle-accept [req]
  (let [c-t-p (-> req :functions :content-type-provided)
	[negotiated-type req] (with-request req (partial negotiate-content-type c-t-p))
	req (assoc req :negotiated-type negotiated-type)]
    (if negotiated-type
      (handle-authorized req)
      [415 "Unsupported media type"])))


(defn handle-authorized [req]
  (let [f-authorized? (-> req :functions :authorized?)
	[authorized req]
    	(with-request req f-authorized?)]
    (if authorized
      (handle-allowed req)
      [401 "Unauthorized"])))

(defn handle-allowed [req]
  (let [[allowed req] (with-request req (-> req :functions :allowed?))]
    (if allowed
      (create-response req)
      [403 "Forbidden"])))

(defn create-response [req]
  (let [functions (req :functions)
	expires (functions :expires)
	last-modified (functions :last-modified)
	gen-etag (functions :etag)
	method (req :request-method)
	handler (functions method)
	[etag req] (with-request req gen-etag)
	[body req] (with-request req handler) 
	[h-expires req] (with-request req expires)
	[h-last-modified req] (with-request req last-modified)
	resp-headers (merge (filter-nil-values 
			     {  "Content-Type" (req :negotiated-type)
				"Expires" (http-date h-expires) 
				"Last-Modified" (http-date h-last-modified)
				"ETag" etag
				})
			    (req :headers))]
    {
     :status 200	
     :headers resp-headers 
     :body body
     }))

(defn make-handler [functions]
  (fn [req]
    (let [functions-with-defaults (merge default-resource-functions functions)]
      (handle-request (assoc req :functions functions-with-defaults)))))
