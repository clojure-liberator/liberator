;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns compojure-rest.resource
  (:use clojure.tools.trace
        compojure-rest.conneg
        compojure-rest.util)
  (:require compojure.response))

(defmulti coll-validator "Return a function that evaluaties of the give argument 
             a) is contained in a collection 
             b) equals an argument
             c) when applied to a function evaluates as true" 
  (fn [x] (cond
	   (coll? x) :col
	   (fn? x) :fn)))

(defmethod coll-validator :col [xs]
  (fn [x] (some #{x} xs)))
(defmethod coll-validator :fn [f]
  f)
(defmethod coll-validator :default [x]
  (partial = x))

;; todos
;; make authorized handler returning single value (not map) as identifier of a principal

(def console-logger #(println (str "LOG " %)))
(def no-logger (constantly nil))

(declare ^:dynamic *-log*)

(def ^:dynamic *-logger* no-logger)

(def var-logger (fn [msg] (dosync (alter *-log* #(conj % msg)))))

(defn log [name value]
  (do
    (*-logger* (str name ": " (pr-str value)))
    value))

(defn make-trace-headers [log]
  log)

(declare if-none-match-exists?)

(defn map-values [f m]
  (apply hash-map (apply concat (map (fn [k] [k (f (m k))]) (keys m)))))

(defn make-function [x]
  (if (fn? x) x (constantly x)))

(defn request-method-in [& methods]
  #(some #{(:request-method %)} methods))

(defn -gen-etag [rmap request]
  (or (request ::etag)
      (if-let [gen-etag (rmap :etag)]
	(gen-etag request))))

;; Comment by Malcolm Sparks :-
;; 
;; I have removed the capability of using keywords for content-types.
;; I believe this feature allowed the following:
;;
;; {:to_html (fn [x] ...)
;;  "text/html" :to_html
;;  "application/xml" :to_html}
;;
;; However, I will try to re-implement this functionality at a later date

;; TODO: This is not called anywhere but we should be careful to preserve the ETag functionality prior to its removal.
(defn -make-body-response [generator rmap request status]
  (let [body (generator request)	
	type (request ::negotiated-content-type)
	response {:status status
                  :headers {"Content-Type" type } 
                  :body body }]
    (if-let [etag (-gen-etag rmap request)]
      (merge response {:headers {"ETag" etag}})
      response)))

(defn make-body-response [rmap request status]
  (let [
        ct-map ((rmap (:request-method request)) request)
        candidates (keys ct-map)
        content-type (log "Make body response for content type" 
                          (or (request ::negotiated-content-type)
                              (some #{"text/html" "text/plain"} candidates)
                              (first candidates)))
        generator (get ct-map content-type)
        request (assoc request ::negotiated-content-type content-type)]
    
    (if (log "Generator is " generator)
      (update-in (merge {:status 200} (compojure.response/render generator request)) [:headers] assoc "Content-Type" content-type)
      [500 (str "Could not resolve body generator for " content-type ".")]
      )
    ))
  
(defn make-handler-function [x]
  (if (number? x) (fn [rmap request] (make-body-response rmap request x))
      (make-function x)))

(defn decide [name test then else rmap request]
  (if (or (fn? test) (contains? rmap name)) 
    (let [ftest (if (fn? test) test (rmap name))	  
	  ftest (make-function ftest)
	  fthen (make-handler-function then)
	  felse (make-handler-function else)
	  r-and-u (log (str  "Decision " name) (ftest request))
	  result (if (vector? r-and-u) (first r-and-u) r-and-u)
	  request-update (if (vector? r-and-u) (second r-and-u) r-and-u)
	  request (if (map? request-update) (merge request request-update ) request)]
      ((if result fthen felse) rmap request))
    { :status 500 :body (str "No handler found for key " name ". Key defined for resource " 
			     (keys rmap))}))

(defn -defdecision [name test then else]
  (let [key (keyword name)]
    `(defn ~name [~'rmap ~'request]
       (decide ~key ~test ~then ~else ~'rmap ~'request))))

(defmacro defdecision 
  ([name then else] (-defdecision name nil then else))
  ([name test then else] (-defdecision name test then else)))

(defn -defhandler [name status message]
  `(defn ~name [~'rmap ~'request]
     (if-let [~'handler (~'rmap ~(keyword name))]
       (merge 
        (~'handler ~'rmap ~'request ~status) 
        {:status ~status})
       {:status ~status 
        :headers { "Content-Type" "text/plain" } 
        :body ~message })))

(defmacro defhandler [name status message]
  (-defhandler name status message))

(defn header-exists? [header request]
  (contains? (:headers request) header))

(defn -if-match-star [request]
  (= "*" ((request :headers) "if-match")))

(defn =method [method request]
  (= (request :request-method) method))

(defn handle-see-other [rmap request]
  ;; handle body
  (if-let [fother (rmap :see-other)]
    {:status 303 :headers { "Location "(fother rmap request)}}
    {:status 500 
     :body "Internal Server error: no location specified for status 303."}))

(defn handle-ok [rmap request]
  (make-body-response rmap request 200))

(defhandler no-content 204 nil)

(defn handle-multiple-representations [rmap request]
  (make-body-response rmap request 310))

(defdecision multiple-representations? handle-multiple-representations handle-ok)

(defdecision respond-with-entity? multiple-representations? no-content)

(defhandler created 201 nil)

(defdecision new? created respond-with-entity?)

(defdecision post-redirect? handle-see-other new?)

(defhandler not-found 404 "Resource not found.")

(defhandler gone 410 "Resouce is gone.")

(defdecision can-post-to-missing? post-redirect? not-found)

(defdecision post-to-missing? (partial =method :post)
  can-post-to-missing? not-found)

(defn handle-moved-permamently [rmap request]
  ;; handle :redirect return body
  { :status 301 :headers { "Location" ((rmap :moved-permamently) request)}})

(defn handle-moved-temporarily [rmap request]
  ;; handle :redirect return body
  { :status 307 :headers { "Location" ((rmap :moved-temporarily) request)}})

(defdecision can-post-to-gone? post-redirect? gone)

(defdecision post-to-gone? (partial =method :post) can-post-to-gone? gone)

(defdecision moved-temporarily? handle-moved-temporarily post-to-gone?)

(defdecision moved-permanently? handle-moved-permamently moved-temporarily?)

(defdecision existed? moved-permanently? post-to-missing?)

(defdecision can-put-to-missing? respond-with-entity? not-found)

(defhandler conflict 409 "Conflict.")

(defdecision conflict? conflict respond-with-entity?)

(defdecision put-to-different-url? handle-moved-permamently conflict?)

(defdecision method-put? (partial =method :put) put-to-different-url? existed?)

(defhandler precondition-failed 412 "Precondition failed.")

(defdecision if-match-star-exists-for-missing? 
  -if-match-star
  precondition-failed
  method-put?)

(defhandler not-modified 304 nil)

(defdecision handle-if-none-match 
  #(#{ :head :get} (% :request-method))
  not-modified
  precondition-failed)

(defdecision put-to-existing? (partial =method :put)
  conflict? multiple-representations?)

(defdecision post-to-existing? (partial =method :post) 
  post-redirect? put-to-existing?)

(defmulti make-date class)
(defmethod make-date java.util.Date [date] date)
(defmethod make-date java.lang.Long [millis-since-epoch] (java.util.Date millis-since-epoch))

(defhandler handle-accepted 202 "Accepted")

(defdecision delete-enacted? respond-with-entity? handle-accepted)

(defdecision method-delete? (partial =method :delete)
  delete-enacted? post-to-existing?)

(defn modified-since? [rmap request]
  (if-let [last-modified (rmap :last-modified)]
    (if (.after (make-date (rmap :last-modified)) (request :if-modified-since-date))
	precondition-failed
	if-none-match-exists?)))


(defn -if-modified-since-valid-date? [rmap request]
  (if-let [date (parse-http-date (request :headers))]
    (modified-since? rmap (assoc request :if-modified-since-date date))
    method-delete?))

(defdecision if-modified-since-exists? (partial header-exists? "if-modified-since")
  -if-modified-since-valid-date? method-delete?)

(defn etag-matches-for-if-none? [rmap request]
  (let [etag (-gen-etag rmap request)]
    (decide :etag-matches-for-if-none?
	    #(= ((% :headers) "if-none-match") etag)
	    handle-if-none-match
	    if-modified-since-exists?
	    rmap
	    (assoc request ::etag etag))))

(defdecision if-none-match-star? 
  #(= "*" ((%1 :headers) "if-none-match"))
  handle-if-none-match etag-matches-for-if-none?)

(defdecision if-none-match-exists? (partial header-exists? "if-none-match")
  if-none-match-star? if-modified-since-exists?)

(defn unmodified-since? [rmap request]
  (if-let [last-modified (rmap :last-modified)]
    (if (.after (make-date (rmap :last-modified)) (request :if-unmodified-since-date))
	method-delete?
	not-modified)))


(defn -if-unmodified-since-valid-date? [rmap request]
  (if-let [date (parse-http-date ((request :headers) "if-unmodified-since"))]
    (unmodified-since? rmap (assoc request ::if-unmodified-since-date date))
    (if-none-match-exists? rmap request)))

(defdecision if-unmodified-since-exists? (partial header-exists? "if-unmodified-since")
  -if-unmodified-since-valid-date? if-none-match-exists?)

(defn etag-matches-for-if-match? [rmap request]
  (let [etag (-gen-etag rmap request)]
    (decide
     :etag-matches-for-if-match?
     #(= ((% :headers) "if-match") etag)
     if-unmodified-since-exists?
     precondition-failed
     rmap
     (assoc request ::etag etag))))

(defdecision if-match-star? 
  -if-match-star if-unmodified-since-exists? etag-matches-for-if-match?)

(defdecision if-match-exists? (partial header-exists? "if-match")
  if-match-star? if-unmodified-since-exists?)

(defdecision exists? if-match-exists? if-match-star-exists-for-missing?)

(defhandler not-acceptable 406 "No acceptable resource available.")

(defdecision encoding-available?  exists? not-acceptable)

(defdecision accept-encoding-exists? (partial header-exists? "accept-encoding")
  encoding-available? exists?)

(defdecision charset-available? accept-encoding-exists? not-acceptable)

(defdecision accept-charset-exists? (partial header-exists? "accept-charset")
  charset-available? accept-encoding-exists?)

(defdecision language-available? accept-charset-exists? not-acceptable)

(defdecision accept-language-exists? (partial header-exists? "accept-language")
  language-available? accept-charset-exists?)

(defn media-type-available? [rmap request]
  (decide :media-type-available? 
	  #(when-let [type (compojure-rest.conneg/best-allowed-content-type 
			    (get-in % [:headers "accept"]) 
			    (keys ((rmap (:request-method %)))))]
	     {::negotiated-content-type (str (first type) "/" (nth type 1))})
	  accept-language-exists?
	  not-acceptable
	  rmap 
	  request))

(defdecision accept-exists? (partial header-exists? "accept") 
  media-type-available? accept-language-exists?)

(defn generate-options-header [request m]
  {:headers ((m :generate-options-header) request)})

(defdecision is-options? #(= :options (:request-method %)) generate-options-header accept-exists?)

(comment (defn handle-options [m request]
   ((if (= :options (request :request-method))
      generate-options-header 
      negotiate-content-type) m request)))

(defhandler request-entity-too-large 413 "Request entity too large.")
(defdecision valid-entity-length? is-options? request-entity-too-large)

(defhandler unsupported-media-type 415 "Unsupported media type.")
(defdecision known-content-type? valid-entity-length? unsupported-media-type)

(defhandler not-implemented 501 "Not implemented.")
(defdecision valid-content-header? known-content-type? not-implemented)

(defhandler forbidden 403 "Forbidden.")
(defdecision allowed? valid-content-header? forbidden)

(defhandler unauthorized 401 "Not authorized.")
(defdecision authorized? allowed? unauthorized)

(defhandler malformed 400 "Bad request.")
(defdecision malformed? malformed authorized?)

(defhandler method-not-allowed 405 "Method not allowed.")
(defdecision method-allowed? coll-validator malformed? method-not-allowed)

(defhandler uri-too-long 414 "Request URI too long.")
(defdecision uri-too-long? uri-too-long method-allowed?)

(defhandler unknown-method 501 "Unknown method.")
(defdecision known-method? uri-too-long? unknown-method)

(defhandler service-not-available 503 "Service not available.")
(defdecision service-available? known-method? service-not-available)

(def default-functions 
     {
      :service-available?        true
      :known-method?            (request-method-in :get :head :options
						   :put :post :delete :trace)
      :uri-too-long?             false
      :method-allowed?           (request-method-in :get :head)
      :malformed?                false
      :encoding-available?       true
      :charset-available?        true
      :authorized?               true
      :allowed?                  true
      :valid-content-header?     true
      :known-content-type?       true
      :valid-entity-length?      true
      :exists?                   true
      :existed?                  false
      :respond-with-entity?      false
      :new?                      true
      :post-to-existing?         false
      :post-redirect?            false
      :put-to-different-url?     false
      :multiple-representations? false
      :conflict?                 false
      :can-put-to-missing?       false
      :can-post-to-missing       true
      :language-available?       true
     })


;; resources are a map of implementation methods

(defn -resource [request kvs]
      (let [m (merge default-functions kvs)
	  m (map-values make-function m)]
        (service-available? m request)))

(defn resource [& kvs]
  (fn [request] (-resource request (apply hash-map kvs))))

(defmacro defresource [name & kvs]
  `(defn ~name [~'request] 
     (-resource ~'request ~(apply hash-map kvs))))

(defn wrap-trace-as-response-header [handler]
  (fn [request]
    (binding [*-log* (ref [])
              *-logger* var-logger]
      (merge 
       (handler request)
       {:headers { "X-Compojure-Rest-Trace" (make-trace-headers @*-log*)}}))))