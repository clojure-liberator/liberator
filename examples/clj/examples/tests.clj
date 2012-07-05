(ns examples.tests
  (:use
   examples
   midje.sweet
   [hiccup.page :only [html5]]
   [compojure-rest.resource :only [resource wrap-trace-as-response-header request-method-in]]
   [ring.mock.request :only [request header]]))

(def friends (ref []))

(dosync
 (alter friends concat [{:name "Alice Wonderland"}
                        {:name "Bobby Charlton"}
                        {:name "Charlie Brown"}])
 )

;; Here are some examples of how to write RESTful resources.
;; The Midje testing framework is used which allows us to make factual declarations.
;; Learn more about Midge here: https://github.com/marick/Midje

;; The resource function always returns a Ring handler, which we then call with a request. We use the ring.mock.request library to create requests.

;; This is the traditional 'Hello World' example.

(facts
 (hello-world
  (-> (request :get "/")))
 => {:status 200 :headers {} :body "Hello World!"})

;; Simple GET of friends in text/plain

((resource :ok {"text/plain" friends})
 (-> (request :get "/")
     (header "Accept" "text/plain")))

;; Let's build up the concepts gradually

;; Creating resources with POST
(facts
 (let [rmap {:method-allowed? (request-method-in :post)
             ;; TODO: Do something on each test to check the created function is called.
             :created (fn [rmap request status] {:body "Thanks!"})
             :exists? true}
       make-resource (fn [rmap] (fn [request] ((apply resource (reduce concat rmap)) request)))]

   ((make-resource rmap)                ; The default resource
    (request :post "/"))                ; upon this request
   => {:status 201, :body "Thanks!"}    ; produces a 201

   ((make-resource                      ; The default resource
     (assoc rmap                        ; with some redirect options
       :post-redirect? true
       :see-other "/new.txt"))
    (request :post "/"))                              ; upon this request
   => {:status 303, :headers {"Location" "/new.txt"}} ; produces a 303 redirect

   ;; Typically we POST to resources that exist, but HTTP also allows us to POST to resources that don't.

   (-> ((make-resource
         (assoc rmap
           :exists? false                ; if the resource does not exist
           :can-post-to-missing? false)) ; and we can't post to it
        (request :post "/"))             ; then upon this request
       (dissoc :headers :body))          ; (ignoring response headers and body)
   => {:status 404}                      ; produces a 404

   
   ((make-resource
     (assoc rmap                    ; on the other hand,
       :exists? false               ; if the resource does not exist
       :can-post-to-missing? true)) ; but we can't post to missing resources
    (request :post "/"))         ; (ignoring response headers and body)
   => {:status 201 :body "Thanks!"}
   ))

;; TODO Now, how about PUT a resource

;; TODO Show how to override various things (like authorization)

;; TODO Define a fancy 404 page (use ordinary Ring middleware)

;; TODO Do HEAD

;; TODO Do OPTIONS

;; TODO Etags

;; TODO Start a Ring server, show some CRUD operations.
;; Integrate this with the euroclojure demo

;; reading from db (see billy's example)

;; to_json, to_html

