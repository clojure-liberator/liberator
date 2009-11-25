;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns compojure-rest.resource
  (:use compojure)
  (:use compojure.http.response)
  (:use compojure-rest)
  (:use clojure.contrib.core)
  (:use clojure.contrib.trace)
  (:import clojure.lang.Fn)
  (:import java.util.Date)
  (:import java.util.Map)
  (:import java.lang.System)
  (:import java.util.Locale)
  (:import java.text.SimpleDateFormat))


(def *default-functions*
     {
      :service-available true
      :uri-too-long      false
      :known-method     (fn [req] (#(some #{(:request-method req)}
					  [:get :head :options
					   :put :post :delete :trace])))
      :method-allowed   (fn [req] (#(some #{(:request-method req)}
					  [:get :head])))
      :malformed        false
      :authorized       true
      :allowed          true
      :known-content-type   true
      :valid-content-header true
      :valid-entity-length  true
      :options              {}
      :content-types-provided "text/html"
      :languages-provided "*"
      :encodings-provided "*"
      :charsets-provided   "*"
      :get                ""
      :exists             true
      })

(defn simple-negotiate [provided accept-header]
  (let [provided-l (if (coll? provided) provided [provided]) ]
    (if (or (.contains accept-header "*/*")
	    (= accept-header "*"))
      (first provided-l)
      (if (some #{"*"} provided-l)
	accept-header
	(#(some #{accept-header} provided-l))))))

(defn wrap-options [handler generate-options-header]
  (fn [request]
    (if (= :options (request :request-method))
      { :headers (evaluate-generate generate-options-header request)}
      (handler request))))

(defn wrap-accept-header [handler provider-function negotiate-function header neg-key default]
  (fn [request]
    (let [accept ((request :headers {}) header default)
	  provided (evaluate-generate provider-function request)]
      (if-let [neg (negotiate-function provided accept)]
	(handler (assoc-in request [::rest neg-key] neg))
	{:statuc 406 :body (str header " " accept " cannot be provided. Available is " provided)}))
    ))

(defn wrap-accept [handler content-types-provided]
  (wrap-accept-header handler content-types-provided simple-negotiate
		      "Accept" :neg-content-type "*/*"))

(defn wrap-accept-language [handler provider-function]
  (wrap-accept-header handler provider-function simple-negotiate
		      "Accept-Language" :neg-lang "*"))

(defn wrap-accept-charset [handler provider-function]
  (wrap-accept-header handler provider-function simple-negotiate
		      "Accept-Charset" :neg-charset "*"))

(defn wrap-accept-encoding [handler provider-function]
  (wrap-accept-header handler provider-function simple-negotiate
		      "Accept-Encoding" :neg-encoding "*"))

(defn send-response [response]
  response)

(defn handle-missing-resource [request]
  {:status 404 :body "Missing resource not implemented."})

(defn handle-delete [m request]
  (send-response (m :delete)))

(defn accept-entity [m request]
  (let [content-type ((request :header) "Content-Type" "application/octet-stream")]
    (comment ;;  Todo: search (m :accept-content-type-handlers)
      )))

(defn assert-valid-redirect [resp]
  (if (and (= (resp :status) 303) (not (-> resp :header "Location")))
    { :status 500 :body "Response was redirect but no location." }
      resp))

(defn handle-post-and-redirect [m request]
  (assert-valid-redirect
   (if ((m :post-is-create) request)
     (let  [new-path ((m :create-path) request)
	    request (assoc request :path new-path)]  
       (accept-entity m request))
     ((m :process-post) request))))



(defn handle-post [m request]
  (if (evaluate-generate (m :allow-missing-post) request)
    (handle-post-and-redirect m request)
    404))

(defn handle-put [m request]
  (if ((m :is-conflict) request)
    409
    (accept-entity m request)))

(defmulti evaluate-body
  "Evaluate the generator with the current request. The type of the 
  generator determines how it is evaluated"
  (fn [generator request]
    (class generator)))

(defmethod evaluate-body Fn
  [update request]
  (update request))

(defmethod evaluate-body Map
  [content-type-map request]
  (let [content-type (-> request ::rest :neg-content-type)]
    (if-let [generator (content-type-map content-type)] 
     (generator request)
     {:status 500
      :body (str "No body generation function found for negotiated content type \""
		 content-type "\" request is " request)})))

(defmethod evaluate-body String
  [body request]
  body)

(defn check-multiple [m request]
(trace "c-m"  (if (m :multiple-choices?)
     300
     (if (= :get (request :request-method))
       (evaluate-body (m :get) request)
       ""))))


(defn handle-get-head [m request]
  (trace "handle-get-head" 
	 (let [resp (trace "ETag"
		     (if-let [etag (evaluate-generate (m :generate-etag) request)]
		       {:headers { "Etag" etag } } {}))
	       resp (trace "Last-M"
			   (if-let [lm (evaluate-generate (m :last-modified) request)]
			     (assoc-in resp [:headers "Last-Modified"] lm) resp))
	       resp (trace "Exp" (if-let [exp (evaluate-generate (m :expires) request)]
		       (assoc-in resp [:headers "Expires"] exp) resp))]
	   (update-response request resp (check-multiple m request)))))



(defn handle-delete-put-post [m]
  (fn [request]
    (condp = (request :request-method)
      :delete (handle-delete m request)
      :post   (handle-post m request)
      :put    (handle-put m request)
      (handle-get-head m request))))

(defn wrap-log [handler]
  (fn [request] 
    (do (prn (str "Calling " handler " with request " request))
	(let [resp (handler request)]
	  (prn (str "<- " handler " result " resp))))))


;; handlers must be a map of implementation methods
(defn resource [ & kvs]
  (fn [request]
    (let [m (merge *default-functions* (apply hash-map kvs))]
      ((-> 
	(handle-delete-put-post m)
	(wrap-if-modified-since (m :last-modified))
	(wrap-if-none-match (m :generate-etag))
	(wrap-if-unmodified-since (m :last-modified))
	(wrap-if-match (m :generate-etag))
	(wrap-predicate (m :exists) handle-missing-resource)
	(wrap-accept-encoding (m :encodings-provided))
	(wrap-accept-charset (m :charsets-provided))
	(wrap-accept-language (m :languages-provided))
	(wrap-accept (m :content-types-provided))
	(wrap-options (m :options))
	(wrap-predicate (m :valid-entity-length) 413)
	(wrap-predicate (m :known-content-type) 415)
	(wrap-predicate (m :valid-content-header) 501)
	(wrap-allow     (m :allowed))
	(wrap-auth      (m :authorized))
	(wrap-predicate #(comp not (evaluate-generate (m :malformed) %)) 400)
	(wrap-predicate (m :method-allowed) 405)
	(wrap-predicate #(comp not (evaluate-generate (m :uri-too-long) %)) 414)
	(wrap-predicate (m :known-method) 501)
	(wrap-predicate (m :service-available) 503))
       request))))


