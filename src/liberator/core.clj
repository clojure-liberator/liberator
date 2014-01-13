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
   [liberator.util :only [parse-http-date http-date as-date make-function]]
   [liberator.representation :only [Representation as-response ring-response]]
   [clojure.string :only [join upper-case]]
   [clojure.tools.trace :only [trace]])
  (:import (javax.xml.ws ProtocolException)))

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

(defn console-logger [category values] 
  #(apply println "LOG " category " " values))

(def ^:dynamic *loggers* nil) 

(defmacro with-logger [logger & body]
  `(binding [*loggers* (conj (or *loggers* []) ~logger)]
     ~@body))

(defmacro with-console-logger [& body]
  `(with-logger console-logger
     ~@body))

(defn atom-logger [atom]
  (fn [& args]
    (swap! atom conj args)))

(defn log! [category & values]
  (doseq [l *loggers*]
    (l category values)))

(declare if-none-match-exists?)

(defn map-values [f m]
  (apply hash-map (apply concat (map (fn [k] [k (f (m k))]) (keys m)))))

(defn request-method-in [& methods]
  #(some #{(:request-method (:request %))} methods))

(defn gen-etag [context]
  (if-let [f (get-in context [:resource :etag])]
    (if-let [etag-val (f context)]
      (format "\"%s\"" etag-val))))

(defn gen-last-modified [context]
  (if-let [f (get-in context [:resource :last-modified])]
    (if-let [lm-val (f context)]
      (as-date lm-val))))

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
	  decision (ftest context)
	  result (if (vector? decision) (first decision) decision)
	  context-update (if (vector? decision) (second decision) decision)
	  context (if (map? context-update)
                    (combine context context-update) context)]
      (log! :decision name decision)
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


(defn set-header-maybe [headers name value]
  (if-not (empty? value)
    (assoc headers name value)
    headers))

(defn build-vary-header [{:keys [media-type charset language encoding] :as represenation}]
  (->> [(when-not (empty? media-type) "Accept")
        (when-not (empty? charset) "Accept-Charset")
        (when-not (empty? language) "Accept-Language")
        (when-not (or (empty? encoding) (= "identity" encoding)) "Accept-Encoding")]
       (remove nil?)
       (interpose ", ")
       (apply str)))

(defn build-allow-header [resource]
  (join ", " (map (comp upper-case name) ((:allowed-methods resource)))))

(defn build-options-headers [resource]
  (merge {"Allow" (build-allow-header resource)}
         (if (some #{:patch} ((:allowed-methods resource)))
           {"Accept-Patch" (join "," ((:patch-content-types resource)))}
           {})))

(defn run-handler [name status message
                   {:keys [resource request representation] :as context}]
  (let [context
        (merge {:status status :message message} context)
        response 
        (merge-with
         combine

         ;; Status
         {:status status}

         ;; ETags
         (when-let [etag (gen-etag context)]
           {:headers {"ETag" etag}})
         
         ;; Last modified
         (when-let [last-modified (gen-last-modified context)]
           {:headers {"Last-Modified" (http-date last-modified)}})

         ;; 201 created required a location header to be send
         (when (= 201 status)
           (if-let [f (or (get context :location)
                          (get resource :location))]
             {:headers {"Location" (str ((make-function f) context))}}))
     
         (if-let [handler (get resource (keyword name))]
           (do
             (log! :handler (keyword name))
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
                   (set-header-maybe "Vary" (build-vary-header representation)))}
              ;; Finally the result of the handler.  We allow the handler to
              ;; override the status and headers.
              (let [handler-response (handler context)
                    ring-response ((:as-response resource) handler-response context)]
                ring-response)))

           ;; If there is no handler we just return the information we
           ;; have so far.
           (let [message (get context :message)]
             (do (log! :handler (keyword name) "(default implementation)")
                 {:status status 
                  :headers {"Content-Type" "text/plain"} 
                  :body (if (fn? message) (message context) message)}))))]
    (cond
     (or (= :options (:request-method request)) (= 405 (:status response)))
     (merge-with merge
                 {:headers (build-options-headers resource)}
                 response)
      
      (= :head (:request-method request))
        (dissoc response :body)
      :else response)))

(defmacro ^:private defhandler [name status message]
  `(defn ~name [context#]
     (run-handler '~name ~status ~message context#)))

(defn header-exists? [header context]
  (get-in context [:request :headers header]))

(defn if-match-star [context]
  (= "*" (get-in context [:request :headers "if-match"])))

(defn =method [method context]
  (= (get-in context [:request :request-method]) method))

(defmulti to-location type)

(defmethod to-location String [uri] (ring-response {:headers {"Location" uri}}))

(defmethod to-location clojure.lang.APersistentMap [this] this)

(defmethod to-location java.net.URL [url] (to-location (.toString url)))

(defmethod to-location nil [this] this)

(defn- handle-moved [{resource :resource :as context}]
  (if-let [f (or (get context :location)
                 (get resource :location))]
    (to-location ((make-function f) context))
    {:status 500
     :body (format "Internal Server error: no location specified for status %s" (:status context))}))

;; Provide :see-other which returns a location or override :handle-see-other
(defhandler handle-see-other 303 nil)

(defhandler handle-ok 200 "OK")

(defhandler handle-no-content 204 nil)

(defhandler handle-multiple-representations 300 nil) ; nil body because the body is reserved to reveal the actual representations available.

(defdecision multiple-representations? handle-multiple-representations handle-ok)

(defdecision respond-with-entity? multiple-representations? handle-no-content)

(defhandler handle-created 201 nil)

(defdecision new? handle-created respond-with-entity?)

(defdecision post-redirect? handle-see-other new?)

(defhandler handle-not-found 404 "Resource not found.")

(defhandler handle-gone 410 "Resource is gone.")

(defaction post! post-redirect?)

(defdecision can-post-to-missing? post! handle-not-found)

(defdecision post-to-missing? (partial =method :post)
  can-post-to-missing? handle-not-found)

(defhandler handle-moved-permanently 301 nil)

(defhandler handle-moved-temporarily 307 nil)

(defdecision can-post-to-gone? post! handle-gone)



(defdecision post-to-gone? (partial =method :post) can-post-to-gone? handle-gone)

(defdecision moved-temporarily? handle-moved-temporarily post-to-gone?)

(defdecision moved-permanently? handle-moved-permanently moved-temporarily?)

(defdecision existed? moved-permanently? post-to-missing?)

(defhandler handle-conflict 409 "Conflict.")

(defaction patch! respond-with-entity?)

(defaction put! new?)

(defdecision conflict? handle-conflict put!)

(defhandler handle-not-implemented 501 "Not implemented.")

(defdecision can-put-to-missing? conflict? handle-not-implemented)

(defdecision put-to-different-url? handle-moved-permanently can-put-to-missing?)

(defdecision method-put? (partial =method :put) put-to-different-url? existed?)

(defhandler handle-precondition-failed 412 "Precondition failed.")

(defdecision if-match-star-exists-for-missing? 
  if-match-star
  handle-precondition-failed
  method-put?)

(defhandler handle-not-modified 304 nil)

(defdecision if-none-match? 
  #(#{ :head :get} (get-in % [:request :request-method]))
  handle-not-modified
  handle-precondition-failed)

(defdecision put-to-existing? (partial =method :put)
  conflict? multiple-representations?)

(defdecision post-to-existing? (partial =method :post) 
  post! put-to-existing?)

(defhandler handle-accepted 202 "Accepted")

(defdecision delete-enacted? respond-with-entity? handle-accepted)

(defaction delete! delete-enacted?)

(defdecision method-patch? (partial =method :patch) patch! post-to-existing?)

(defdecision method-delete?
  (partial =method :delete)
  delete!
  method-patch?)

(defdecision modified-since?
  (fn [context]
    (let [last-modified (gen-last-modified context)]
      [(and last-modified (.after last-modified (::if-modified-since-date context)))
       {::last-modified last-modified}]))
  method-delete?
  handle-not-modified)

(defdecision if-modified-since-valid-date?
  (fn [context] 
    (if-let [date (parse-http-date (get-in context [:request :headers "if-modified-since"]))]
      {::if-modified-since-date date}))
  modified-since?
  method-delete?)

(defdecision if-modified-since-exists?
  (partial header-exists? "if-modified-since")
  if-modified-since-valid-date?
  method-delete?)

(defdecision etag-matches-for-if-none?
  (fn [context]
    (let [etag (gen-etag context)]
      [(= (get-in context [:request :headers "if-none-match"]) etag)
       {::etag etag}]))
  if-none-match?
  if-modified-since-exists?)

(defdecision if-none-match-star? 
  #(= "*" (get-in % [:request :headers "if-none-match"]))
  if-none-match?
  etag-matches-for-if-none?)

(defdecision if-none-match-exists? (partial header-exists? "if-none-match")
  if-none-match-star? if-modified-since-exists?)

(defdecision unmodified-since?
  (fn [context]
    (let [last-modified (gen-last-modified context)]
      [(and last-modified
            (.after last-modified
                    (::if-unmodified-since-date context)))
       {::last-modified last-modified}]))
  handle-precondition-failed
  if-none-match-exists?)

(defdecision  if-unmodified-since-valid-date?
  (fn [context]   
    (if-let [date (parse-http-date (get-in context [:request :headers  "if-unmodified-since"]))]
      (assoc context ::if-unmodified-since-date date) context))
  unmodified-since?
  if-none-match-exists?)

(defdecision if-unmodified-since-exists? (partial header-exists? "if-unmodified-since")
  if-unmodified-since-valid-date? if-none-match-exists?)

(defdecision etag-matches-for-if-match?
  (fn [context]
    (let [etag (gen-etag context)]
      [(= etag (get-in context [:request :headers "if-match"]))
       (assoc context ::etag etag)]))
  if-unmodified-since-exists?
  handle-precondition-failed)

(defdecision if-match-star? 
  if-match-star if-unmodified-since-exists? etag-matches-for-if-match?)

(defdecision if-match-exists? (partial header-exists? "if-match")
  if-match-star? if-unmodified-since-exists?)

(defdecision exists? if-match-exists? if-match-star-exists-for-missing?)

(defhandler handle-unprocessable-entity 422 "Unprocessable entity.")
(defdecision processable? exists? handle-unprocessable-entity)

(defhandler handle-not-acceptable 406 "No acceptable resource available.")

(defdecision encoding-available? 
  (fn [ctx]
    (when-let [encoding (conneg/best-allowed-encoding
                         (get-in ctx [:request :headers "accept-encoding"])
                         ((get-in ctx [:resource :available-encodings]) ctx))]
      {:representation {:encoding encoding}}))

  processable? handle-not-acceptable)

(defmacro try-header [header & body]
  `(try ~@body
        (catch ProtocolException e#
          (throw (ProtocolException.
                  (format "Malformed %s header" ~header) e#)))))

(defdecision accept-encoding-exists? (partial header-exists? "accept-encoding")
  encoding-available? processable?)

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
                               ((get-in context [:resource :available-media-types] (constantly "text/html")) context))]
                {:representation {:media-type (conneg/stringify type)}})))

(defdecision media-type-available? negotiate-media-type
  accept-language-exists? handle-not-acceptable)

(defdecision accept-exists?
  #(if (header-exists? "accept" %)
     true
     ;; "If no Accept header field is present, then it is assumed that the
     ;; client accepts all media types" [p100]
     ;; in this case we do content-type negotiaion using */* as the accept
     ;; specification
     (if-let [type (liberator.conneg/best-allowed-content-type 
                    "*/*"
                    ((get-in context [:resource :available-media-types]) context))]
       [false {:representation {:media-type (liberator.conneg/stringify type)}}]
       false))
  media-type-available?
  accept-language-exists?)

(defhandler handle-options 200 nil)

(defdecision is-options? #(= :options (:request-method (:request %))) handle-options accept-exists?)

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

(defn test-request-method [valid-methods-key]
  (fn [{{m :request-method} :request
       {vm valid-methods-key} :resource
       :as ctx}]
    (some #{m} (vm ctx))))

(def default-functions 
  {
   ;; Decisions
   :service-available?        true

   :known-methods             [:get :head :options :put :post :delete :trace :patch]
   :known-method?             (test-request-method :known-methods)

   :uri-too-long?             false

   :allowed-methods           [:get :head]
   :method-allowed?           (test-request-method :allowed-methods)

   :malformed?                false
   ;;      :encoding-available?       true
   ;;      :charset-available?        true
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
   :processable?              true

   ;; Handlers
   :handle-ok                 "OK"
   :handle-see-other          handle-moved
   :handle-moved-temporarily  handle-moved
   :handle-moved-permanently  handle-moved
   

   ;; Imperatives. Doesn't matter about decision outcome, both
   ;; outcomes follow the same route.
   :post!                     true
   :put!                      true
   :delete!                   true
   :patch!                    true

   ;; To support RFC5789 Patch, this is used for OPTIONS Accept-Patch
   ;; header
   :patch-content-types [] ;(:patch-content-types resource)

   ;; The default function used extract a ring response from a handler's response
   :as-response               as-response

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


(defn get-options
  [kvs]
  (if (map? (first kvs))
    (merge (first kvs) (apply hash-map (rest kvs)))
    (apply hash-map kvs)))

(defn resource [& kvs]
  (fn [request]
    (run-resource request (get-options kvs))))

(defmacro defresource [name & kvs]
  (if (vector? (first kvs))
    (let [args (first kvs)
          kvs (rest kvs)]
      `(defn ~name [~@args]
         (resource ~@kvs)))
    `(def ~name 
       (resource ~@kvs))))

(defn by-method
  "returns a handler function that uses the request method to
   lookup a function from the map and delegates to it.

   Example:

   (by-method {:get \"This is the entity\"
               :delete \"Entity was deleted successfully.\"})"
  [map]
  (fn [ctx] ((make-function (get map (get-in ctx [:request :request-method]) ctx)) ctx)))

