(ns test-get-put
  (:use liberator.core
        midje.sweet
        checkers
        [ring.mock.request :only [request header]]))

;; tests for a resource where you can put something and get
;; it back later. Will use the content-type of the PUT body
;; Generates last-modified header for conditional requests.


(defn thing-resource [things]
  (resource
   ;; early lookup
   :service-available? (fn [ctx] {::r (get @things (get-in ctx [:request :uri]))})
   :method-allowed? (request-method-in :get :put :delete)
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
   ;; known request content types
   :known-content-types ["application/edn"]
   ;; update the representation
   :put! #(dosync
           (alter things assoc-in
                  [(get-in % [:request :uri])]
                  {:content (get % :entity)
                   :media-type (get-in % [:request :headers "content-type"]
                                       "application/octet-stream")
                   :last-modified (java.util.Date.)}))
   ;; ...store a nil value to marke the resource as gone
   :delete! #(dosync (alter things assoc (get-in % [:request :uri]) nil))
   :last-modified #(get-in % [::r :last-modified])))

(let [things (ref nil)
      thing-resource (thing-resource things)]
  (facts
    (fact "entity does not exists, yet"
      (let [resp (thing-resource (request :get "/r1"))]
        (fact "get => 404" resp => NOT-FOUND)))
    (fact "create entity with put"
      (let [resp (thing-resource (-> (request :put "/r1")
                                     (assoc :body "r1")
                                     (header "content-type" "text/plain")))]
        (fact "put => 202" resp => CREATED)))
    (fact "get newly created entity"
      (let [resp (thing-resource (-> (request :get "/r1")))]
        (fact "get => 200" resp => OK)
        (fact "get body is what was put before"
          resp => (body "r1"))
        (fact "content type is set correcty"
          resp => (content-type "text/plain;charset=UTF-8"))
        (future-fact "last-modified header is set")))
    (fact "delete entity"
      (let [resp (thing-resource (-> (request :delete "/r1")))]
        (fact "delete" resp => NO-CONTENT)))
    (fact "entity is gone"
      (let [resp (thing-resource (request :get "/r1"))]
        (fact "get => gone" resp => GONE)))))
