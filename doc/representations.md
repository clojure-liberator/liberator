---
layout: default
title: Representations
---
# Representations

Handlers return a _representation_ of the resource at a given URL. The
returned value must extend the protocol
````liberator.representation/Representation````. This protocol defines
a single method ````as-response```` which must return a standard ring
response map. The representation must consider the media-type, charset
and other negotiated values in the context's representation map.
Liberator does not re-encode the returned response any further to match
character set or encoding.

The most common types for response bodies are supported by liberator
out-of-the-box:

* String
* File
* InputStream
* Maps
* Sequences
* RingResponse

These types can be represented as plain text, HTML, CSV, JSON, Clojure
and so on. The idea is to have a fast flash-to-bang when exposing data
as resources.

### String

String responses are encoded into a bytestream according to the
charset.

### File

File responses are returned as body unchanged and are expected to
match the negotiated parameters

### Inputstream

Inpustreams are returned as body unchanged and are expected to match
the negotiated parameters

### Maps

Liberator can build different representations for maps:

* text/plain
* text/csv and text/tab-separated-values
* JSON
* Clojure
* HTML-Table

You can add a custom media-type representations by extending the
multi-method ````liberator.representation/render-map-generic````

### Sequences

As with maps, there are a couple of representations for sequences:

* text/plain
* HTML
* JSON
* Clojure/EDN <span class="label label-info">since 0.9.0</span>
* text/csv and text/tab-separated-values

You can add a custom media-type representations by extending the
multi-method ````liberator.representation/render-seq-generic````

### RingResponse

With ````(liberator.representation/ring-response)```` you can 
create a response which is used unaltered as the ring response, or
which alters the representation:

#### Returning a ring response map

{% highlight clojure %}
(defresource x
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx] (ring-response
                          {:status 666 :body "\"this is json\""})))
;; will return the outcome of `handle-ok` as the response
{% endhighlight %}

#### Altering a generated response <span class="label label-info">since 0.13</span>

`ring-response` can also make use of liberator's response generation. This
is especially useful to set individual headers in the response.

{% highlight clojure %}
(defresource x
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx] (ring-response
                         {:some "json"}
                         {:headers {"X-Foo-Header" "This is liberator"}})))
;; This creates the same response as without ring-response but additionally
;; sets the header "X-Foo-Header"
{% endhighlight %}

### More media types

Additional media types can be generated in different ways. You can
extend the map and sequence rendering as described above, you can return a
string, or return a ring response.

For example, say you want to return "application/css". Your handler can
generate the representation most easily like this:

{% highlight clojure %}
(defresource x 
  :available-media-types ["application/css"]
  :handle-ok (fn [_] "body { font-size: 16px; }")
{% endhighlight %}

### Custom implementation at :as-response <span class="label label-info">since 0.11.0</span>

You can also specify a function at the key ```:as-response.``` The
function will be used instead of ````liberator.representation/as-response````.

A typical use is to decorate the ring response map with custom headers:

{% highlight clojure %}
(defresource x 
  :as-response (fn [d ctx]
                 (-> (as-response d ctx) ;; default implementation
                     (assoc-in [:headers "X-FOO"] "Bar")))
  :handle-ok (fn [_] "test"))
{% endhighlight %}

A function at as-response is required to return a ring response map. Liberator
will set the status code and various headers (e.g. "Vary") if necessary.
