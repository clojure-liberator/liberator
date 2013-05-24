---
layout: tutorial
title: Welcome
---
# Quickstart
## Include in project.clj
{% highlight clojure %}
[liberator "0.9.0"]{% endhighlight %}

<div class="alert alert-warning">Liberator is still under heavy
development, however, the programming interface is settling down.
Compatibility with prior releases is a goal but it cannot always be
guaranteed. Compatibility notes will be added to the interface in 
the future.</div>


## Define your first resource 
````defresource```` defines a resource which, in the end, is a ring handler.
{% highlight clojure %}
(ns example
  (:use [liberator.core :only [defresource]]))

(defresource hello-world
  :available-media-types ["text/plain"]
  :handle-ok "Hello, world!")
{% endhighlight %}

## Liberating data?

Forget “big data”, there is so much useful data locked up in “big
applications”. Many of these applications, especially in large
organizations, are written in Java.

Clojure can run side-by-side with Java in the JVM, seamlessly
accessing the same internal Java APIs that your application does. That
makes Clojure a great choice for developing new facades onto your
application stack.
