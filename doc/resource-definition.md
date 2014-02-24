---
layout: default
title: Resource definition
---
# Resource definition

A liberator resource is a ring handler and adheres to the ring
contract. However, as a developer you will not work with the ring interface,
but instead provide functions for decision points, actions and handlers.

## resource and defresource

A resource is created using the functions ````resource```` which
returns a function that is a ring handler. You can also use the macro
````defresource```` which binds also binds the function to a var.

{% highlight clojure %}
(defresource foo :handle-ok "This is ok")

;; this is the same as

(def foo (resource :handle-ok "This is ok")
{% endhighlight %}

The resource takes a sequence of keys and values. The values are
functions that accept a single parameter, the ````context````, or
values that will be threated like a constant function.

Liberator uses three types of functions:
[*decisions*](decisions.html), [*actions*](actions.html) and
[*handlers*](handlers.html).

## Base implementation

You can optionally pass a map as the first argument to a resource
definition. The values in the map are used when no other value is
specified for a key:

{% highlight clojure %}
(def resource-defaults
  :handle-not-found (fn [_] "Ops.")
  :authorized? (fn [_] ...))

(defresource foo resource-defaults :handle-ok "This is ok")

;; this is the same as

(defresource foo resource-defaults
  :handle-ok "This is ok"
  :handle-not-found (fn [_] "Ops.")
  :authorized? (fn [_] ...))
{% endhighlight %}

## Parametrized resources

Routing libraries like compojure support the extraction and binding of
request parameters from the URL. This frees the developer from
extracting the parameters from the request in the resource functions
and typically enhances the code legibility.

With liberator you can use ````resource```` and simply close over the
parameter, however, liberator simplifies this with parametrized resources.

{% highlight clojure %}
(defresource parametrized [x]
  :handle-ok (fn [_] (format "This is x: %s" x)))

(defroutes params
  ;; these are equivalent:
  (ANY "/a/:x" [x] (fn [req] (resource :handle-ok (fn [_] (format "This is x: %s" x)))))
  (ANY "/b/:x" [x] (parametrized x)))
{% endhighlight %}



