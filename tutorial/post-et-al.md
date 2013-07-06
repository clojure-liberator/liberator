---
layout: tutorial
title: Tutorial - Handling POST, et al.
---
# Handling POST, et al.

There's more than GET and HEAD requests. To be a useful web library
you better support POST, PUT and DELETE as well, and so does
liberator. While there are even more HTTP methods than those, liberator
has no out-of-the-box support for them and treat them like GET requests.
In any case you need to declare them as known and allowed.

## Enabling the methods

The allowed methods are determined by ````:method-allowed?````. The
default implementation for this decision uses the resource key
````:allowed-methods```` to obtain a list of methods and checks if it
matches the request method. When adding more methods to your resource,
make sure that the method is declared as known in
````:known-methods````. By default, liberator knows the methods from
RFC2616: GET, HEAD, PUT, POST, DELETE, OPTIONS, TRACE.

## POST

Post requests share a lot of the decision flow with GET requests. The
main difference is that you can also reach the handlers
````:created````, ````:no-content```` and ````:see-other````. Because
post is a non-idempotent method, you can provide a function for the
key ````:post!```` which is optimal for changing the state on the
server. The negotiation of the status code is done afterwards. For the
details please refer to the decision graph.

An idiomatic way to support post is the following:

{% highlight clojure %}
  (ANY "/postbox" []
       (resource
        :allowed-methods [:post :get]
        :available-media-types ["text/html"]
        :handle-ok (fn [ctx]
                     (format  (str "<html>Post text/plain to this resource.<br>\n"
                                   "There are %d posts at the moment.")
                              (count @posts)))
        :post! (fn [ctx]
                 (dosync 
                  (let [body (slurp (get-in ctx [:request :body]))
                        id   (count (alter posts conj body))]
                    {::id id})))
        ;; actually http requires absolute urls for redirect but let's
        ;; keep things simple.
        :post-redirect? (fn [ctx] {:location (format "/postbox/%s" (::id ctx))})))
{% endhighlight %}

We can extend this example to support conditional request. Thus a
client can make sure that the POST is enacted only if no other request
was made since it checked the resource:

{% highlight clojure %}
    (ANY "/cond-postbox" []
       (resource
        :allowed-methods [:post :get]
        :available-media-types ["text/html"]
        :handle-ok (fn [ctx]
                     (format  (str "<html>Post text/plain to this resource.<br>\n"
                                   "There are %d posts at the moment.")
                              (count @posts)))
        :post! (fn [ctx]
                 (dosync 
                  (let [body (slurp (get-in ctx [:request :body]))
                        id   (count (alter posts conj body))]
                    {::id id})))
        ;; actually http requires absolute urls for redirect but let's
        ;; keep things simple.
        :post-redirect? (fn [ctx] {:location (format "/postbox/%s" (::id ctx))})
        :etag (fn [_] (str (count @posts)))))
{% endhighlight %}

We also make a little resource to retrieve the posted content again:

{% highlight clojure %}
    (ANY "/postbox/:x" [x]
       (resource
        :allowed-methods [:get]
        :available-media-types ["text/html"]
        :exists? (fn [ctx] (if-let [d (get @posts (dec (Integer/parseInt x)))] {::data d}))
        :handle-ok ::data))
{% endhighlight %}

A quick test with curl shows that we cannot post to a stale resource:

{% highlight bash session %}
$ curl -i http://localhost:3000/cond-postbox
HTTP/1.1 200 OK
Date: Thu, 25 Apr 2013 15:52:45 GMT
Vary: Accept
ETag: "4"
Content-Type: text/html;charset=UTF-8
Content-Length: 76
Server: Jetty(7.6.1.v20120215)

<html>Post text/plain to this resource.<br>
There are 4 posts at the moment.

$ curl -XPOST -d test -H 'Content-Type: text/plain' -H 'If-Match: "4"' -i http://localhost:3000/cond-postbox
HTTP/1.1 303 See Other
Date: Thu, 25 Apr 2013 15:53:52 GMT
Vary: Accept
Location: /postbox/5
ETag: "5"
Content-Type: text/html;charset=ISO-8859-1
Content-Length: 0
Server: Jetty(7.6.1.v20120215)

$ curl -XPOST -d test -H 'Content-Type: text/plain' -H 'If-Match: "4"' -i http://localhost:3000/cond-postbox
HTTP/1.1 412 Precondition Failed
Date: Thu, 25 Apr 2013 15:54:04 GMT
ETag: "5"
Content-Type: text/plain;charset=ISO-8859-1
Content-Length: 20
Server: Jetty(7.6.1.v20120215)

Precondition failed.
{% endhighlight %}

# PUT request

The necessary steps to implement handling of PUT are mostly those for
POST. A key difference is that ````:can-put-to-missing?```` can lead
to ````:conflict?```` which can send you to ````:handle-conflict````.
This is not possible for POST requests. On the other hand PUT to a
nonexistent resource does not allow a response that sends you to a
different location. The necessary flow can be seen as always on the
[decision graph](decision-graph.html).
