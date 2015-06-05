---
title: Tutorial - Getting started
layout: tutorial
---
# Getting started

This part will give you a quick start into programming with liberator.

## Installation

Liberator is available from clojars. Add liberator to your project.clj as

````[liberator "0.10.0"]````

<div class="alert alert-info">The latest release might be newer, but the tutorial works at least
with this version.</div>

For the tutorial we'll use ring-core, ring-jetty-adapter and compojure. For a
fast setup, we'll use lein:

{% highlight bash session %}
lein new liberator-tutorial
{% endhighlight %}

Add dependencies to project.clj:

{% highlight clojure %}
(defproject liberator-tutorial "0.1.0-SNAPSHOT"
  :plugins [[lein-ring "0.8.11"]]
  :ring {:handler liberator-tutorial.core/handler}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [liberator "0.10.0"]
                 [compojure "1.3.4"]
                 [ring/ring-core "1.2.1"]])
{% endhighlight %}

Edit `src/liberator_tutorial/core.clj` where we define our first resource:

{% highlight clojure %}
(ns liberator-tutorial.core
  (:require [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes ANY]]))

(defroutes app
  (ANY "/" [] (resource)))

(def handler 
  (-> app 
      wrap-params))
{% endhighlight %}

Run with `lein ring server` and jetty will be started on port 3000. However,
the result of pointing your browser is somewhat disappointing: 
"No acceptable resource available". To get a more exciting result we simply must
declare which kind of media type we are able to offer, e.g. 
"text/html" and set a handler for the status ````200 OK````.
 So change the definition as follows:

{% highlight clojure %}
(defroutes app
  (ANY "/foo" [] (resource :available-media-types ["text/html"]
                           :handle-ok "<html>Hello, Internet.</html>")))
{% endhighlight %}

The result is promising but we likely want to return something more
dynamic. For this we declare a handler function that will be invoked
for the http status code 200 "OK". As you can see, liberator accepts
either values or functions that take the context as the only argument.

{% highlight clojure %}
(defroutes app
  (ANY "/foo" [] (resource :available-media-types ["text/html"]
                           :handle-ok (fn [ctx]
                                        (format "<html>It's %d milliseconds since the beginning of the epoch."
                                                (System/currentTimeMillis))))))
{% endhighlight %}

## There comes the confusion

Make sure that you actually provide a function and not a value:
{% highlight clojure %}
(def counter (ref 0))
;;...
(resource :handle-ok (format "The counter is %d" @counter))
;;...
{% endhighlight %}

This looks well but handle-ok will be set to a *value* which was computed
when the function ````resource```` was called, not when the request was
processed. The correct solution is:
{% highlight clojure %}
(def counter (ref 0))
;;...
(resource :handle-ok (fn [_] (format "The counter is %d" @counter)))
;;...
{% endhighlight %}

## Variations of resource definitions

Until now we used the function ````resource```` to create a ring
handler for the resources. It is natural that liberator also provides
a macro to bind the resource function to a var:

{% highlight clojure %}
(defresource example
  :handle-ok "This is the example")
{% endhighlight %}

Liberator also supports so-called parametrized resources. These are
handler factories that close over some bindings and match perfectly
with compojure's routing parameters:

{% highlight clojure %}
(defresource parameter [txt]
  :available-media-types ["text/plain"]
  :handle-ok (fn [_] (format "The text is %s" txt)))

(defroutes app
  (ANY "/bar/:txt" [txt] (parameter txt)))
{% endhighlight %}

## Using PUT to get more out of your resource 

Processing GET was easy, wasn't it? Now let's try a different HTTP
method, say "PUT":

{% highlight bash session %}
$ curl -v -X PUT http://localhost:3000/foo
HTTP/1.1 405 Method Not Allowed
Date: Fri, 19 Apr 2013 12:38:37 GMT
Content-Type: text/plain;charset=ISO-8859-1
Content-Length: 19
Server: Jetty(7.6.1.v20120215)

Method not allowed.
{% endhighlight %}

Woohoo! Exactly what I'd expect. But how did liberator determine that
it must send a 405 status? It uses a graph of decisions which is
described in the next part: [Decision graph](decision-graph.html).
