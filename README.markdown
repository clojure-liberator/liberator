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
    

Dependencies
------------

For compojure-rest you'll need

* The [Clojure](http://clojure.org) programming language
* The [Clojure-Contrib](http://code.google.com/p/clojure-contrib/) library
* A Java servlet container like [Jetty](http://www.mortbay.org/jetty/)
* Apache Commons [FileUpload](http://commons.apache.org/fileupload),
  [IO](http://commons.apache.org/io) and
  [Codec](http://commons.apache.org/codec).
* The [Compojure](http://groups.google.com/group/compojure) library

A pom descriptor is available for your convenience "mvn package" should
drop you a fine jar file in target/compojure-rest-VERSION.jar

License
-------

Compojure-rest is licensed under EPL 1.0 (see file epl-v10.html).
