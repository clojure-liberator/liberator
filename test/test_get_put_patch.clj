(ns test-get-put-patch
  (:use liberator.core
        midje.sweet
        checkers
        [ring.mock.request :only [request header]]))

;; tests for a resource where you can put something and get
;; it back later. Will use the content-type of the PUT body
;; Generates last-modified header for conditional requests.

(defn thing-resource [things]
  (resource
   :allowed-methods [:delete :get :head :options :patch :put]
   ;; early lookup
   :service-available? (fn [ctx]
                         {::r (get @things (get-in ctx [:request :uri]))})
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
                    {:content (:entity %)
                     :media-type "text/plain"
                     :last-modified (java.util.Date.)}))
   :patch-content-types ["application/json"]
   :put! #(dosync
           (alter things assoc-in
                  [(get-in % [:request :uri])]
                  {:content (:entity %)
                   :media-type (get-in % [:request :headers "content-type"]
                                       "application/octet-stream")
                   :last-modified (java.util.Date.)}))
   ;; ...store a nil value to marke the resource as gone
   :delete! #(dosync (alter things assoc (get-in % [:request :uri]) nil))
   :last-modified #(get-in % [::r :last-modified])))

(let [things (ref nil)
      thing-resource (thing-resource things)]
  (facts
    (fact "resource does not exist yet"
      (let [resp (thing-resource (request :get "/r1"))]
        (fact "get => 404" resp => NOT-FOUND)))
    (fact "create resource with put"
      (let [resp (thing-resource (-> (request :put "/r1")
                                     (assoc :body "r1")
                                     (header "content-type" "text/plain")))]
        (fact "returns correct status code" resp => CREATED)))
    (fact "get created resource"
      (let [resp (thing-resource (-> (request :get "/r1")))]
        (fact "returns correct status code" resp => OK)
        (fact "get body is what was put before"
          resp => (body "r1"))
        (fact "content type is set correctly"
          resp => (content-type "text/plain;charset=UTF-8"))
        (fact "last-modified header is set"
          (nil? (get (:headers resp) "Last-Modified")) => false)))
    (let [resp (thing-resource (-> (request :options "/r1")))]
      (fact "allowed patch content types"
        (get (:headers resp) "Accept-Patch") => "application/json")
      (fact "expected options response - Allow header"
        (get (:headers resp) "Allow") => "DELETE, GET, HEAD, OPTIONS, PATCH, PUT")
      (fact "get => 200" resp => OK)
      (fact "last-modified header is set"
        (nil? (get (:headers resp) "Last-Modified")) => false))
    (let [resp (thing-resource (-> (request :patch "/r1")
                                   (assoc :body "{\"some\":\"json\"}")
                                   (header "content-type" "application/json")))]
      (fact "patch => 204" resp => NO-CONTENT))
    (let [resp (thing-resource (-> (request :get "/r1")))]
      (fact "get => 200" resp => OK)
      (fact "get body is what was patched in"
        resp => (body "some=json"))
      (fact "content type is set correctly"
        resp => (content-type "text/plain;charset=UTF-8")) ;
      (fact "last-modified header is set"
        (nil? (get (:headers resp) "Last-Modified")) => false))
    (let [resp (thing-resource (-> (request :delete "/r1")))]
      (fact "delete" resp => NO-CONTENT))
    (let [resp (thing-resource (request :get "/r1"))]
      (fact "get => gone" resp => GONE))))
