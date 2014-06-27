---
layout: default
title: Asynchronous Resources
---
# Asynchronous Resources <span class="label label-info">since 0.12.0</span>

The [core.async](https://github.com/clojure/core.async) library provides an
excellent set of facilities for asynchronous (strictly speaking: concurrent
sequential) programming which, when paired with event-driven / non-blocking
web servers such as [Aleph](https://github.com/ztellman/aleph),
[HTTP Kit](http://http-kit.org) or [Netty](http://netty.io/)
allows us to create web services with extremely desirable scaling
characteristics and performance under load.
Liberator integrates with core.async in order to allow resource functions
(decisions, actions, handlers and directives) to execute, near seamlessly, in a
non-blocking fashion; simply return true from the new ````:async?```` decision
to enable async support, then return a channel from any resource function to
switch liberator into non-blocking mode. The returned channel must eventually
yield the appropriate result depending on the resource function type, or an
````Exception````. The resource execution will return a skeleton response
object containing only a ````:body```` key whose value will be a channel onto
which the eventual response will be placed (rather than yielding the response
directly).

Note that there is no requirement to return a channel from *every* resource
function; use them only where processing may block on IO, and return "normal"
clojure values from pure functions.

## Simple Example

The following, contrived, example shows a simple non-blocking resource (but
see the section called [Channel Caveats](#channel_caveats) before using the
example directly):

{% highlight clojure %}
(require '[clojure.core.async :refer (go <!)])

(defresource user-resource
  [userId]
  :async? true
  :handle-not-found
  (fn [_] (str "User id: " userId " not found"))
  :exists?
  (fn [ctx]
    (go
      (when-let [user (<! (get-user-from-db-async userId))]
        {:user user})))
  :handle-ok
  (fn [{:keys [user] :as ctx}]
    (str "User id: " userId ", user name: " (:name user))))
{% endhighlight %}

## Web Server Integration

Integrating asynchronous responses may vary depending on the web server being
used, but will most likely involve writing some
[Ring middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware).
An example piece of middleware for [HTTP Kit](http://http-kit.org) is shown below:

{% highlight clojure %}
(require '[clojure.core.async :refer [take!]]
         '[liberator.async :refer [async-response]]
         '[org.httpkit.server :refer [close send! with-channel]])

(defn wrap-async
  [f]
  (fn [request]
    (let [raw-response (f request)]
      ;; Only switch to async when neccessary; allows us to support sync
      ;; and async handling.
      (if-let [response-channel (async-response raw-response)]
        (with-channel request out-channel
          (take! response-channel
                 (fn [response]
                   (send! out-channel response)
                   (close out-channel))
                 false))
        raw-response))))
{% endhighlight %}

## Channel Caveats

Liberator does not (currently) perform any form of timeout handling
(````alt!```` etc.) on the channels returned by resource functions.
It is important, therefore, to ensure that all channels returned from
such functions *will* eventually yield a value, or else liberator
handling of the request will deadlock.
There are two main areas that need to be considered when implementing resource
functions which support this: *third party channels* and *exception handling*.

*Third party channels* are any channel that may not ever return a response;
for example if you are using a non blocking HTTP client library such as
HTTP Kit (and have bridged it to core.async channels), then you should
ensure either that your requests will always deliver *some* sort of response
to the channel, or else use ````alt!```` in combination with a ````timeout````
inside your resource function and respond accordingly.

*Exception handling* is a concern because core.async ````go```` blocks run
inside their own executor pool, and any exceptions thrown from inside a
block will not propagate out of it (as they would for normal code).
Liberator allows for this, while maintaining reasonable semantics, by
checking if the value returned from a channel is an ````Exception````;
if it is then the value is rethrown at the point where it is read
(as per traditional resource execution). Clients need only ensure that
any exceptions thrown from their own code are placed onto the response
channel rather than discarded by the executor. The simplest way to achieve
this is with a ````go?```` macro wrapper around core.async ````go```` which
catches any thrown exceptions and places them onto the return channel.
Liberator provides this macro, along with several others, in the
````liberator.async```` namespace (see below).

## Async Utilities

Liberator provides several macros in the ````liberator.async```` namespace
designed to make integrating and working with non-blocking resources as
straightforward and error-free as possible.

The ````go?```` macro executes its body inside a core.async ````go```` block,
but catches any exceptions which would otherwise propagate outside the block,
and places them onto the result channel. It is often used in conjunction
with ````<?````, a wrapper around core.async ````<!```` which performs the
channel read, checks if the resulting value is an ````Exception````, and if
so throws it. ````channel?```` can be used to test if a value is a core.async
channel, and is intended for use in integration code.
Rounding out the integration functions are ````async-response````, which
checks if a response map contains a ````:body```` which is a channel, and if
so returns it
(see the [web server integration example](#web_server_integration))
and ````async-middleware```` which can be used to create async aware
middleware functions from a pair of request and response handling functions,
such as those found in
[ring-core](https://github.com/ring-clojure/ring/tree/master/ring-core).

