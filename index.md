---
layout: tutorial
title: Welcome
---
# What is liberator?

Liberator is a [Clojure](http://clojure.com/) library that helps you expose your data as
resources while automatically complying with all the relevant
requirements of the [HTTP specification (RFC-2616)](http://www.ietf.org/rfc/rfc2616.txt).
Your resources will automatically gain useful HTTP features, such as caching and content
negotiation. Liberator was inspired by [Erlang's Webmachine](https://github.com/basho/webmachine).
By following the constraints and requirements in RFC-2616, liberator will
enable you to create application according to a
[REST architecture](http://en.wikipedia.org/wiki/Representational_state_transfer).

# Liberator's place in the Clojure ecosystem

Liberator resources are ring handlers and fit nicely into
[Ring](https://github.com/ring-clojure/ring), the defacto Clojure
web development environment. Typically you will use liberator together
with a routing library like [compojure](https://github.com/weavejester/compojure)
and a library to generate the resources' representations. The representations may
be in HTML, generated with a library like [hiccup](https://github.com/weavejester/hiccup),
or they could be represented in something like JSON or XML by using the appropriate libraries.

For more Information about adding the web server component required for liberator, see the
[Ring documentation](https://github.com/ring-clojure/ring/wiki).

# Quickstart

Add the liberator dependency to your project.clj.

{% highlight clojure %}
[liberator "0.10.0"]{% endhighlight %}

<div class="alert alert-warning">Liberator is still under heavy
development, however, the programming interface is settling down.
Compatibility with prior releases is a goal but it cannot always be
guaranteed. Compatibility notes will be added to the interface in 
the future.</div>

Define your first resource using ````defresource````.

{% highlight clojure %}
(ns example
  (:use [liberator.core :only [defresource]]))

(defresource hello-world
  :available-media-types ["text/plain"]
  :handle-ok "Hello, world!")
{% endhighlight %}

## Liberating data?

Forget “big data”, there is a lot of useful data locked up in “big
applications”. Many of these applications, especially in large
organizations, are written in Java.

Clojure can run side-by-side with Java in the JVM, seamlessly
accessing the same internal Java APIs that your Java application does. That
makes Clojure a great choice for developing new facades onto your
application stack and liberating your data in an HTTP compliant manner.
