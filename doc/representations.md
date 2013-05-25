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
* Clojure
* text/csv and text/tab-separated-values

You can add a custom media-type representations by extending the
multi-method ````liberator.representation/render-seq-generic````

### RingResponse

The function ````(liberator.representation/ring-response)```` will
create a response which is used unaltered as the ring response. This is
necessary because simple maps would be transformed to the above
media-types.

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