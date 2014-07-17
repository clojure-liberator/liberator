(ns test-get-put-patch
  (:use liberator.core
        midje.sweet
        checkers
        [ring.mock.request :only [request header]]))

;; tests for a resource where you can put something and get
;; it back later. Will use the content-type of the PUT body
;; Generates last-modified header for conditional requests.

(def things (ref nil))

(def thing-resource
  (resource
   :allowed-methods [:delete :get :head :options :patch :put]
   ;; early lookup   
   :service-available? (fn [ctx] {::r (get @things (get-in ctx [:request :uri]))})
   ;:method-allowed? (request-method-in )
   ;; lookup media types of the requested resource
   :available-media-types #(if-let [m (get-in % [::r :media-type])] [m])
   ;; the resource exists if a value is stored in @things at the uri
   ;; store the looked up value at key ::r in the context
   :exists? ::r
   ;; ...it existed if the stored value is nil (and not some random
   ;; Objeced we use as a setinel)
   :existed? #(nil? (get @things (get-in % [:request :uri]) (Object.)))
   ;; use the previously stored value at ::r
   :handle-ok #(get-in % [::r :content])
   ;; update the representation
   :patch! #(dosync
           (alter things assoc-in
                  [(get-in % [:request :uri])]
                  {:content (get-in % [:request :body])
                   :media-type "text/plain"
                   :last-modified (java.util.Date.)}))
   :patch-content-types ["application/example"]
   :put! #(dosync
           (alter things assoc-in
                  [(get-in % [:request :uri])]
                  {:content (get-in % [:request :body])
                   :media-type (get-in % [:request :headers "content-type"]
                                       "application/octet-stream")
                   :last-modified (java.util.Date.)}))
   ;; ...store a nil value to marke the resource as gone
   :delete! #(dosync (alter things assoc (get-in % [:request :uri]) nil))
   :last-modified #(get-in % [::r :last-modified])))

(facts
 (let [resp (thing-resource (request :get "/r1"))]
   (fact "get => 404" resp => NOT-FOUND))
 (let [resp (thing-resource (-> (request :put "/r1")
                   (assoc :body "r1")
                   (header "content-type" "text/plain")))]
   (fact "put => 202" resp => CREATED))
 (let [resp (thing-resource (-> (request :get "/r1")))]
   (fact "get => 200" resp => OK)
   (fact "get body is what was put before"
         resp => (body "r1"))
   (fact "content type is set correctly"
         resp => (content-type "text/plain;charset=UTF-8"))
   (fact "last-modified header is set"
         (nil? (get (:headers resp) "Last-Modified")) => false))
 (let [resp (thing-resource (-> (request :options "/r1")))]
   (fact "allowed patch content types"
         (get (:headers resp) "Accept-Patch") => "application/example")
   (fact "expected options response - Allow header"
         (get (:headers resp) "Allow") => "DELETE, GET, HEAD, OPTIONS, PATCH, PUT")
   (fact "get => 200" resp => OK)
   (fact "last-modified header is set"
         (nil? (get (:headers resp) "Last-Modified")) => false))
 (let [resp (thing-resource (-> (request :patch "/r1")
                   (assoc :body "Some patch implementation.")
                   (header "content-type" "application/example")))]
   (fact "put => 204" resp => NO-CONTENT))
 (let [resp (thing-resource (-> (request :get "/r1")))]
   (fact "get => 200" resp => OK)
   (fact "get body is what was patched in"
         resp => (body "Some patch implementation."))
   (fact "content type is set correctly"
         resp => (content-type "text/plain;charset=UTF-8"))
   (fact "last-modified header is set"
         (nil? (get (:headers resp) "Last-Modified")) => false))
 (let [resp (thing-resource (-> (request :delete "/r1")))]
   (fact "delete" resp => NO-CONTENT))
 (let [resp (thing-resource (request :get "/r1"))]
   (fact "get => gone" resp => GONE)))
