---
title: Tutorial - Getting started
layout: tutorial
---
# Getting started

This part will give you a quick start into programming with liberator.

## What is liberator?

Liberator is a Clojure library that helps you expose your data as
resources while automatically complying with all the relevant
requirements of HTTP specification (RFC-2616). Your resources will
automatically gain useful HTTP features, such as caching and content
negotiation. Liberator was inspired by erlang's webmachine. By
following the constraints and requirements in RFC-2616 liberator will
enable you to create application according to a REST architecture.

## Liberator's place in the ecosystem

Liberator resources are ring handlers and fit exactly in the clojure
web development environment. Typicall you will use liberator together
with a routing library like compojure and a library to generate the
representation in HTML, like hiccup or liberary that generates json.

For more Information about adding the web server component, see the
[Ring documentation](https://github.com/ring-clojure/ring/wiki).

## Installation and getting started

Liberator is available from clojars. add liberator to your project.clj as

````[liberator "0.8.0"]````

<div class="alert alert-info">The latest release might be newer, but the tutorial works at least
with this version.</div>

For the tutorial we'll use jetty-ring-adapter and compojure. For a
fast setup, we'll use lein:

{% highlight bash session %}
lein new liberator-tutorial
{% endhighlight %}

{% highlight clojure %}
(defproject tutorial "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [liberator "0.8.0"]
                 [compojure "1.1.3"]
                 [ring/ring-jetty-adapter "1.1.0"]])  
{% endhighlight %}

Edit liberator_tutorial/core.clj where we define our first resource:

{% highlight clojure %}
(ns liberator-tutorial.core
  (:require [liberator.core :refer [resource defresource]]
            [ring.adapter.jetty :refer [run-jetty]]      
            [compojure.core :refer [defroutes ANY]]))

(defroutes app
  (ANY "/" [] (resource)))

(run-jetty #'app {{:port 3000}})
{% endhighlight %}

Load the namespace and jetty will be started on port 3000. However,
the result of pointing your browser are somewhat disappointing: 
"No acceptable resource available." To fix this we simply must
declare which kind of media type we are able to offer, e.g. 
"text/html". So change the definition as follows:

{% highlight clojure %}
(defroutes app
  (ANY "/foo" [] (resource :available-media-types ["text/html"])))
{% endhighlight %}

The result is promising but we likely want to return something more
dynamcis. For this we declare a handler function that will be invoked
for the http status code 200 "OK":

{% highlight clojure %}
(defroutes app
  (ANY "/foo" [] (resource :available-media-types ["text/html"]
                           :handle-ok (fn [ctx]
                                        (format "<html>It's %d milliseconds since the begin of the epoch."
                                                (System/currentTimeMillis))))))

{% endhighlight %}

That was easy, wasn't it? Now let's try a different HTTP method,
say "PUT":

{% highlight bash session %}
$ curl -v -X PUT http://localhost:3000/foo
HTTP/1.1 405 Method Not Allowed
Date: Fri, 19 Apr 2013 12:38:37 GMT
Content-Type: text/plain;charset=ISO-8859-1
Content-Length: 19
Server: Jetty(7.6.1.v20120215)

Method not allowed.
{% endhighlight %}

Wohoo! Exactly what I'd expect. But how did liberator determine that
it must send a 405 status? It uses a graph of decisions wich is
described in the next part: [Decision graph](decision-graph.html).
