;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns liberator.core
  (:require [liberator.conneg :as conneg])
  (:use
   [liberator.util :only [parse-http-date http-date]]
   [liberator.representation :only [Representation as-response]]
   [clojure.tools.trace :only [trace]])
  (:import (javax.xml.ws ProtocolException)))

(defprotocol DateCoercions
  (as-date [_]))

(extend-protocol DateCoercions
  java.util.Date
  (as-date [this] this)
  Long
  (as-date [millis-since-epoch]
    (java.util.Date. millis-since-epoch)))

(defmulti coll-validator
  "Return a function that evaluaties if the give argument 
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

(defmacro with-console-logger [& body]
  `(binding [*-logger* console-logger]
     ~@body))

(declare if-none-match-exists?)

(defn map-values [f m]
  (apply hash-map (apply concat (map (fn [k] [k (f (m k))]) (keys m)))))

(defn make-function [x]
  (if (fn? x) x (constantly x)))

(defn request-method-in [& methods]
  #(some #{(:request-method (:request %))} methods))

(defn gen-etag [context]
  (or (context ::etag)
      (if-let [f ((:resource context) :etag)]
	(format "\"%s\"" (f context)))))

(defn gen-last-modified [context]
  (or (::last-modified context)
      (if-let [f (get-in context [:resource :last-modified])]
	(as-date (f context)))))

;; A more sophisticated update of the request than a simple merge
;; provides.  This allows decisions to return maps which modify the
;; original request in the way most probably intended rather than the
;; over-destructive default merge.
(defn combine [curr newval]
  (cond
   (and (map? curr) (map? newval)) (merge-with combine curr newval)
   (and (list? curr) (list? newval)) (concat curr newval)
   (and (vector? curr) (vector? newval)) (vec (concat curr newval))
   :otherwise newval))

(defn decide [name test then else {:keys [resource request] :as context}]
  (if (or (fn? test) (contains? resource name)) 
    (let [ftest (or (resource name) test)
	  ftest (make-function ftest)
	  fthen (make-function then)
	  felse (make-function else)
	  decision (log (str "Decision " name) (ftest context))
	  result (if (vector? decision) (first decision) decision)
	  context-update (if (vector? decision) (second decision) decision)
	  context (if (map? context-update)
                    (combine context context-update) context)]
      ((if result fthen felse) context))
    {:status 500 :body (str "No handler found for key \""  name "\"."
                            " Keys defined for resource are " (keys resource))}))

(defn defdecision*
  [name test then else]
  `(defn ~name [~'context]
     (decide ~(keyword name) ~test ~then ~else ~'context)))

(defmacro defdecision 
  ([name then else]
     (defdecision* name nil then else))
  ([name test then else]
     (defdecision* name test then else)))

(defmacro defaction [name next]
  `(defdecision ~name ~next ~next))

(defn set-header-maybe [res name value]
  (if (and value (not (empty? value)))
    (assoc res name (str value))
    res))

(defn build-vary-header [{:keys [media-type charset language encoding] :as represenation}]
  (->> [(when-not (empty? media-type) "Accept")
        (when-not (empty? charset) "Accept-Charset")
        (when-not (empty? language) "Accept-Language")
        (when-not (or (empty? encoding) (= "identity" encoding)) "Accept-Encoding")]
       (remove nil?)
       (interpose ", ")
       (apply str)))

(defn run-handler [name status message
                   {:keys [resource request representation] :as context}]
  (let [context (assoc context :status status :message message)]
    (if-let [handler (resource (keyword name))]
      (do
        (log "Handler" (keyword name))
        (->> 
         (merge

          ;; Status
          {:status status}

          ;; ETags
          (when-let [etag (gen-etag context)]
            {:headers {"ETag" etag}})

          ;; Last modified
          (when-let [last-modified (gen-last-modified context)]
            {:headers {"Last-Modified" (http-date last-modified)}})
       
          ;; Finally the result of the handler.  We allow the handler to
          ;; override the status and headers.
          ;;
          ;; The rules about who should take responsibility for encoding
          ;; the response are defined in the BodyResponse protocol.
          (let [handler-response (handler context)
                response (as-response handler-response context)]
            ;; We get an obscure 'cannot be cast to java.util.Map$Entry'
            ;; error if our BodyResponse function doesn't return a map,
            ;; so we check it now.
            (when-not (or (map? response) (nil? response))
              (throw (Exception. (format "%s as-response function did not return a map (or nil) for instance of %s"
                                         'Representation (type handler-response)))))
            response))
         ;; Content negotiations
         (merge-with
          merge
          {:headers
           (-> {} 
               (set-header-maybe "Content-Type"
                                 (str (:media-type representation)
                                      (when-let [charset (:charset representation)] (str ";charset=" charset))))
               (set-header-maybe "Content-Language" (:language representation))
               (set-header-maybe "Content-Encoding"
                                 (let [e (:encoding representation)]
                                   (if-not (= "identity" e) e)))
               (set-header-maybe "Vary" (build-vary-header representation)))})))
      
      ;; If there is no handler we just return the information we have so far.
      (do (log "Handler (default)" (keyword name))
          {:status status 
           :headers {"Content-Type" "text/plain"} 
           :body message}))))

(defmacro ^:private defhandler [name status message]
  `(defn ~name [context#]
     (run-handler '~name ~status ~message context#)))

(defn header-exists? [header context]
  (contains? (:headers (:request context)) header))

(defn if-match-star [context]
  (= "*" (get-in context [:request :headers "if-match"])))

(defn =method [method context]
  (= (get-in context [:request :request-method]) method))

(defmulti to-location type)

(defmethod to-location String [uri] {:headers {"Location" uri}})

(defmethod to-location clojure.lang.APersistentMap [this] this)

(defmethod to-location nil [this] this)

(defn- handle-moved [name status {:keys [resource] :as context}]
  (if-let [f (or (get resource name) (make-function (get context :location)))]
    (merge {:status status} (to-location (f context)))
    {:status 500
     :body (format "Internal Server error: no location specified for status %d. Provide %s" status name)}))

;; Provide :see-other which returns a location or override :handle-see-other
(defn handle-see-other [context]
  (handle-moved :see-other 303 context))

(defhandler handle-ok 200 "OK")

(defhandler handle-no-content 204 nil)

(defhandler handle-multiple-representations 310 nil) ; nil body because the body is reserved to reveal the actual representations available.

(defdecision multiple-representations? handle-multiple-representations handle-ok)

(defdecision respond-with-entity? multiple-representations? handle-no-content)

(defhandler handle-created 201 nil)

(defdecision new? handle-created respond-with-entity?)

(defdecision post-redirect? handle-see-other new?)

(defhandler handle-not-found 404 "Resource not found.")

(defhandler handle-gone 410 "Resouce is gone.")

(defaction post! post-redirect?)

(defdecision ^{:step :M7} can-post-to-missing? post! handle-not-found)

(defdecision ^{:step :L7} post-to-missing? (partial =method :post)
  can-post-to-missing? handle-not-found)

(defn handle-moved-permamently [context]
  (handle-moved :handle-moved-permanently 301 context))

(defn handle-moved-temporarily [context]
  (handle-moved :handle-moved-temporarily 307 context))

(defdecision ^{:step :N5} can-post-to-gone? post! handle-gone)

(defdecision post-to-gone? (partial =method :post) can-post-to-gone? handle-gone)

(defdecision moved-temporarily? handle-moved-temporarily post-to-gone?)

(defdecision moved-permanently? handle-moved-permamently moved-temporarily?)

(defdecision existed? moved-permanently? post-to-missing?)

(defhandler handle-conflict 409 "Conflict.")

(defaction put! new?)

(defdecision ^{:step [:O14 :P3]} conflict? handle-conflict put!)

(defhandler handle-not-implemented 501 "Not implemented.")

(defdecision can-put-to-missing? conflict? handle-not-implemented)

(defdecision put-to-different-url? handle-moved-permamently can-put-to-missing?)

(defdecision method-put? (partial =method :put) put-to-different-url? existed?)

(defhandler handle-precondition-failed 412 "Precondition failed.")

(defdecision if-match-star-exists-for-missing? 
  if-match-star
  handle-precondition-failed
  method-put?)

(defhandler handle-not-modified 304 nil)

(defdecision ^{:step :J18} if-none-match 
  #(#{ :head :get} (get-in % [:request :request-method]))
  handle-not-modified
  handle-precondition-failed)

(defdecision ^{:step :O16} put-to-existing? (partial =method :put)
  conflict? multiple-representations?)

(defdecision ^{:step :N16} post-to-existing? (partial =method :post) 
  post! put-to-existing?)

(defhandler handle-accepted 202 "Accepted")

(defdecision delete-enacted? respond-with-entity? handle-accepted)

(defaction delete! delete-enacted?)

(defdecision ^{:step :M16} method-delete?
  (partial =method :delete)
  delete!
  post-to-existing?)

(defn modified-since? [context]
  (let [last-modified (gen-last-modified context)]
    (decide :modified-since?
            (fn [context] (and last-modified
                               (.after last-modified
                                       (::if-modified-since-date context))))
            method-delete?
            handle-not-modified
            (assoc context ::last-modified last-modified))))

(defn if-modified-since-valid-date? [context]
  (let [date (parse-http-date (get-in context [:request :headers "if-modified-since"]))]
    (decide :if-modified-since-valid-date?
            (fn [_] date)
            modified-since?
            method-delete?
            (if date (assoc context ::if-modified-since-date date) context))))

(defdecision ^{:step :L13} if-modified-since-exists?
  (partial header-exists? "if-modified-since")
  if-modified-since-valid-date?
  method-delete?)

(defn ^{:step :K13} etag-matches-for-if-none? [context]
  (let [etag (gen-etag context)]
    (decide :etag-matches-for-if-none?
	    #(= (get-in % [:request :headers "if-none-match"]) etag)
	    if-none-match
	    if-modified-since-exists?
	    (assoc context ::etag etag))))

(defdecision ^{:step :I13} if-none-match-star? 
  #(= "*" (get-in % [:request :headers "if-none-match"]))
  if-none-match
  etag-matches-for-if-none?)

(defdecision ^{:step :I12} if-none-match-exists? (partial header-exists? "if-none-match")
  if-none-match-star? if-modified-since-exists?)

(defn ^{:step :H12} unmodified-since? [context]
  (let [last-modified (gen-last-modified context)]
    (decide :unmodified-since?
            (fn [context] (and last-modified
                               (.after last-modified
                                       (::if-unmodified-since-date context))))
            handle-precondition-failed
            if-none-match-exists?
            (assoc context ::last-modified last-modified))))

(defn ^{:step :H11} if-unmodified-since-valid-date? [context]
  (let [date (parse-http-date (get-in context [:request :headers  "if-unmodified-since"]))]
    (decide :if-unmodified-since-valid-date?
            (fn [context] date)
            unmodified-since?
            if-none-match-exists?
            (if date (assoc context ::if-unmodified-since-date date) context))))

(defdecision ^{:step :H10} if-unmodified-since-exists? (partial header-exists? "if-unmodified-since")
  if-unmodified-since-valid-date? if-none-match-exists?)

(defn ^{:step :G11} etag-matches-for-if-match? [context]
  (let [etag (gen-etag context)]
    (decide
     :etag-matches-for-if-match?
     #(= etag (get-in % [:request :headers "if-match"]))
     if-unmodified-since-exists?
     handle-precondition-failed
     (assoc context ::etag etag))))

(defdecision ^{:step :G9} if-match-star? 
  if-match-star if-unmodified-since-exists? etag-matches-for-if-match?)

(defdecision ^{:step :G8} if-match-exists? (partial header-exists? "if-match")
  if-match-star? if-unmodified-since-exists?)

(defdecision exists? if-match-exists? if-match-star-exists-for-missing?)

(defhandler handle-not-acceptable 406 "No acceptable resource available.")

(defdecision encoding-available? 
  (fn [ctx]
    (when-let [encoding (conneg/best-allowed-encoding
                         (get-in ctx [:request :headers "accept-encoding"])
                         ((get-in ctx [:resource :available-encodings]) ctx))]
      {:representation {:encoding encoding}}))

  exists? handle-not-acceptable)

(defmacro try-header [header & body]
  `(try ~@body
        (catch ProtocolException e#
          (throw (ProtocolException.
                  (format "Malformed %s header" ~header) e#)))))

(defdecision accept-encoding-exists? (partial header-exists? "accept-encoding")
  encoding-available? exists?)

(defdecision charset-available?
  #(try-header "Accept-Charset"
               (when-let [charset (conneg/best-allowed-charset
                                   (get-in % [:request :headers "accept-charset"])
                                   ((get-in context [:resource :available-charsets]) context))]
                 (if (= charset "*")
                   true
                   {:representation {:charset charset}})))
  accept-encoding-exists? handle-not-acceptable)

(defdecision accept-charset-exists? (partial header-exists? "accept-charset")
  charset-available? accept-encoding-exists?)


(defdecision language-available?
  #(try-header "Accept-Language"
               (when-let [lang (conneg/best-allowed-language
                                (get-in % [:request :headers "accept-language"]) 
                                ((get-in context [:resource :available-languages]) context))]
                 (if (= lang "*")
                   true
                   {:representation {:language lang}})))
  accept-charset-exists? handle-not-acceptable)

(defdecision accept-language-exists? (partial header-exists? "accept-language")
  language-available? accept-charset-exists?)

(defn negotiate-media-type [context]
  (try-header "Accept"
              (when-let [type (conneg/best-allowed-content-type 
                               (get-in context [:request :headers "accept"]) 
                               ((get-in context [:resource :available-media-types] "text/html") context))]
                {:representation {:media-type (conneg/stringify type)}})))

(defdecision media-type-available? negotiate-media-type
  accept-language-exists? handle-not-acceptable)

(defn accept-exists? [context]
  (decide :accept-exists?
          #(if (header-exists? "accept" %)
             true
             ;; "If no Accept header field is present, then it is assumed that the
             ;; client accepts all media types" [p100]
             (if-let [type (liberator.conneg/best-allowed-content-type 
                            "*/*"
                            ((get-in context [:resource :available-media-types]) context))]
               [false {:representation {:media-type (liberator.conneg/stringify type)}}]
               false))
          media-type-available?
          accept-language-exists?
          context))

(defn generate-options-header [{:keys [resource request]}]
  {:headers ((:generate-options-header resource) request)})

(defdecision is-options? #(= :options (:request-method (:request %))) generate-options-header accept-exists?)

(defhandler handle-request-entity-too-large 413 "Request entity too large.")
(defdecision valid-entity-length? is-options? handle-request-entity-too-large)

(defhandler handle-unsupported-media-type 415 "Unsupported media type.")
(defdecision known-content-type? valid-entity-length? handle-unsupported-media-type)

(defdecision valid-content-header? known-content-type? handle-not-implemented)

(defhandler handle-forbidden 403 "Forbidden.")
(defdecision allowed? valid-content-header? handle-forbidden)

(defhandler handle-unauthorized 401 "Not authorized.")
(defdecision authorized? allowed? handle-unauthorized)

(defhandler handle-malformed 400 "Bad request.")
(defdecision malformed? handle-malformed authorized?)

(defhandler handle-method-not-allowed 405 "Method not allowed.")
(defdecision method-allowed? coll-validator malformed? handle-method-not-allowed)

(defhandler handle-uri-too-long 414 "Request URI too long.")
(defdecision uri-too-long? handle-uri-too-long method-allowed?)

(defhandler handle-unknown-method 501 "Unknown method.")
(defdecision known-method? uri-too-long? handle-unknown-method)

(defhandler handle-service-not-available 503 "Service not available.")
(defdecision service-available? known-method? handle-service-not-available)

(def default-functions 
     {
      ;; Decisions
      :service-available?        true
      :known-method?             (request-method-in :get :head :options
						   :put :post :delete :trace)
      :uri-too-long?             false
      :method-allowed?           (request-method-in :get :head)
      :malformed?                false
;      :encoding-available?       true
;      :charset-available?        true
      :authorized?               true
      :allowed?                  true
      :valid-content-header?     true
      :known-content-type?       true
      :valid-entity-length?      true
      :exists?                   true
      :existed?                  false
      :respond-with-entity?      false
      :new?                      true
      :post-redirect?            false
      :put-to-different-url?     false
      :multiple-representations? false
      :conflict?                 false
      :can-post-to-missing?      true
      :can-put-to-missing?       true
      :moved-permanently?        false
      :moved-temporarily?        false
      :delete-enacted?           true

      ;; Handlers
      :handle-ok                 "OK"

      ;; Imperatives. Doesn't matter about decision outcome, both
      ;; outcomes follow the same route.
      :post!                     true
      :put!                      true
      :delete!                   true

      ;; Directives
      :available-media-types     []

      ;; "If no Content-Language is specified, the default is that the
      ;; content is intended for all language audiences. This might mean
      ;; that the sender does not consider it to be specific to any
      ;; natural language, or that the sender does not know for which
      ;; language it is intended."
      :available-languages       ["*"]
      :available-charsets        ["UTF-8"]
      :available-encodings       ["identity"]})

;; resources are a map of implementation methods
(defn run-resource [request kvs]
  (try
    (service-available? {:request request
                         :resource
                         (map-values make-function (merge default-functions kvs))
                         :representation {}})
    
    (catch ProtocolException e         ; this indicates a client error
      {:status 400
       :headers {"Content-Type" "text/plain"}
       :body (.getMessage e)
       ::throwable e}))) ; ::throwable gets picked up by an error renderer

(defn resource [& kvs]
  (fn [request] (run-resource request (apply hash-map kvs))))

(defmacro defresource [name & kvs]
  (if (vector? (first kvs))
    (let [args (first kvs)
          kvs (rest kvs)]
      `(defn ~name [~@args]
         (fn [request#]
           (run-resource request# ~(apply hash-map kvs)))))
    `(defn ~name [request#] 
       (run-resource request# ~(apply hash-map kvs)))))

(defn wrap-trace-as-response-header [handler]
  (fn [request]
    (binding [*-log* (ref [])
              *-logger* var-logger]
      (let [resp (handler request)]
        (when resp
          (assoc-in resp
                    [:headers "X-Liberator-Trace"] (make-trace-headers @*-log*)))))))

(defn get-trace []
  (make-trace-headers @*-log*))
