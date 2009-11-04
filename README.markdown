Compojure-rest is a thin layer for building RESTful applications on top
of compojure. It is loosely modeled after webmachine. Every resource is
represented by a bunch of functions.

Compojure-rest is still in active development and must be considered an
incomplete ALPHA release

Sample Code
-----------

A small example web application as in test.clj

    (ns test
      (:use compojure)
      (:use compojure-rest))
    
    (defn hello-resource []
      (compojure-rest/make-handler {
          :get (fn [req] (str "Hello " ((req :route-params {}) :who "unknown foreigner")))
          :generate-etag (fn [req] ((req :route-params) :who))
          :expires (constantly 10000)		; expire in 10 sec
          :last-modified (constantly -10000)	; last modified 10 sec ago
          :authorized? (fn [req] (not (= "tiger" ((req :route-params {}) :who))))
          :allowed? (fn [req] (not (= "scott" ((req :route-params {}) :who))))
          }))
    
    
    (defroutes my-app
      (ANY "/hello/:who" (hello-resource))
      (ANY "/simple/" (compojure-rest/make-handler { :get (fn [req] "Simple") }))
      (ANY "*" (page-not-found)))
    
    
    (run-server {:port 8080}
      "/*" (servlet my-app))

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
