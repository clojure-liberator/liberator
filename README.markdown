Compojure-rest is a thin layer for building RESTful applications on top
of compojure. It is loosely modeled after webmachine. 
It provides a bunch of decorates which can be combined to provide a
sophisticated implementation of the HTTP RFC.

Compojure-rest is still in active development and must be considered an
incomplete ALPHA release

Sample Code
-----------

A small example web application as in test.clj

    (ns test
      (:use compojure)
      (:use compojure-rest))
    
    (defn hello-resource [request]
      ((-> (method-not-allowed)
           (wrap-generate-body (fn [r] (str "Hello " ((request :params) :who "stranger"))))
           (wrap-etag (comp :who :params))
           (wrap-expiry (constantly 10000))	
           (wrap-last-modified -1000)
           (wrap-exists (comp not #(some #{%} ["cat"]) :who :params))
           (wrap-auth (comp not #(some #{%} ["evil"]) :who :params))
           (wrap-allow (comp not #(some #{%} ["scott"]) :who :params)))
       request))
    
    (defroutes my-app
      (ANY "/hello/:who" hello-resource)
      (GET "/simple" (str "simple"))
      (GET "/echo/:foo" (fn [req] {:headers { "Content-Type" "text/plain" } :body (str (dissoc req :servlet-request))}))
      (GET "*" (page-not-found)))
    
    (defn main []
      (do
        (defserver test-server {:port 8080} "/*" (servlet my-app))
        (start test-server)))

You can also define a resource in a more RESTful way, like webmachine allows:

    (def product-resource
     (resource
      :content-types-provided [ "text/html", "text/plain"]
      :exists (fn [req] (if-let [id (-> req :route-params :id)]
			  (if (< id 10)
			    (assoc req ::product (str "P-" id)))))
      :generate-etag (fn [req] (str "X-" (req ::product)))
      :delete (fn [req] "deleted")
      :put    (fn [req] (str "PUT: "
			     ((req :route-params) :id) (req :body)))
      :get    {
	       "text/html" (fn [req] (str "<h1>" (req ::product) "</h1>"))
	       :json (fn [req] (str "JSON: " (req ::product)))
	       :xml  (fn [req] (str "XML:"   (req ::product)))}))

    

Dependencies
------------

For compojure-rest you'll need

* Leiningen build tool
* The [Compojure](http://groups.google.com/group/compojure) library
* clj-conneg for content negotiation. (Available from my github space)


License
-------

Compojure-rest is licensed under EPL 1.0 (see file epl-v10.html).
