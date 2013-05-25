---
layout: default
title: Debugging
---
# Debugging the execution flow

Finding out why liberator comes to a certain result can be tedious.
Instead of sprinkling trace statements over your code, liberator can simply
trace the request execution.

## Tracing with liberator.dev/wrap-trace

To enable request tracing wrap the request handler in
````liberator.dev/wrap-trace````. 

{% highlight clojure %}
(defroutes app
  (ANY "/some-resource" [] some-resource)
  (ANY "/other-uri" [] some-ring-handler)

(def handler
  (-> app
      (wrap-trace :header :ui)))
{% endhighlight %}

````wrap-trace```` accepts to optional arguments

argument | description
---------|------------
:header  | Enabled response header "X-Liberator-Trace"
:ui      | Add link rel to UI

### Tracing header

If enabled, liberator adds for every decision and handler invocation a
response header "X-Liberator-Trace". This is especially helpful when
debugging with curl or other console tools.

### Trace user interface

Sometimes, the full response header is not easily available or you
want to "look back" later. Liberator provides a web resource at
````/x-liberator/requests```` where a list of recent requests can be
found. The trace for a single request can be selected there. 

For the trace-ui to be accessible it is mandatory that the wrapped
handler is invoked for trace ui resource. The simplest way to make
this is to add wrap-trace to the stack of ring middleware outside the
routing.

### Linking to a request's trace

In any case a link header will be returned with rel=x-liberator-trace.
It points to the request on the trace ui. The current id can also be
retrieved programmatically using ````(current-trace-url)````.

### Runtime access to the current trace id

The current trace id is bound to ````liberator.dev/*current-id*````.
You can generate a link to the ui console for the current request
and embed a HTML-snippet of a hovering link.

function                    | description
----------------------------|------------
````current-trace-url````   | generates a URL pointing to the trace ui for the current request
````include-trace-panel```` | generates an HTML snippet with a link to the trace ui
````css-url````             | generates a URL to CSS for the above
HTML-snippet

### Add trace information in your code

The function ````liberator.core/log!```` adds entries to liberators
trace log. This can have some advantages over printing to the console,
because the logged statement is available at the correct position in
the trace.

{% highlight clojure %}
(defresource you-trace-me
   :available-media-types ["text/plain"]
   :handle-ok (fn [_]
                (log! :trace "It's now" (System/currentTimeMillis))
                "This is ok!"))
{% endhighlight %}

The entry will show up in the trace and is marked as :trace. You can
use any keyword you want, liberator uses :decision and :handler for
it's own purposes.

## Debugging and testing in the REPL

In clojure, debugging mostly occurs at the REPL, and Liberator makes
this especially easy because every resource is a ring handler function
and can be tested as such:

{% highlight clojure %}
    (use '[ring.mock.request :only [request header]])
    (use 'liberator.core)
    (use 'liberator.dev)

(defresource test-resource
      :available-media-types ["application/json"]
      :handle-ok {:message "I am a constant value"}
      ;; let etag change every 10s
      :etag (str (int (/ (System/currentTimeMillis) 10000))))
{% endhighlight %}

In the REPL we can test the resource:

{% highlight clojure %}
    (test-resource (request :get "/"))
    ;; => {:body "{\"message\":\"I am a constant value\"}", :headers {"Content-Type" "application/json;charset=UTF-8", "Vary" "Accept", "ETag" "\"136920539\""}, :status 200}

(test-resource (-> (request :get "/") (header "if-none-match"
    "\136920539\"")
    ;; => {:body nil, :headers {"Content-Type" "text/plain", "ETag""\"136920539\""}, :status 304}

;; wait 10s
    (test-resource (-> (request :get "/") (header "if-none-match"
    "\136920539\"")
    ;; => {:body "{\"message\":\"I am a constant value\"}", :headers {"Content-Type" "application/json;charset=UTF-8", "Vary" "Accept", "ETag" "\"136920540\""}, :status 200}

{% endhighlight %}

Let's test content negotiation and add some tracing:

{% highlight clojure %}
    ((wrap-trace test-resource :header) (-> (request :get "/") (header "accept" "text/plain")))
    ;; => {:body "No acceptable resource available.", :headers
    {"X-Liberator-Trace" (":decision (:service-available? true)"
    ":decision (:known-method? :get)" ":decision (:uri-too-long? false)"
    ":decision (:method-allowed? :get)" ":decision (:malformed? false)"
    ":decision (:authorized? true)" ":decision (:allowed? true)"
    ":decision (:valid-content-header? true)" ":decision
    (:known-content-type? true)" ":decision (:valid-entity-length? true)"
    ":decision (:is-options? false)" ":decision (:accept-exists? true)"
    ":decision (:media-type-available? nil)" ":handler
    (:handle-not-acceptable \"(default implementation)\")"),
    "X-Liberator-Trace-Id" "xa5cv", "Content-Type" "text/plain", "ETag"
    "\"136920564\""}, :status 406}

    ;; as you can see, no available media type was found, so let's try again
    ((wrap-trace test-resource :header) (-> (request :get "/") (header "accept" "application/json")))
    => {:body "{\"message\":\"I am a constant value\"}", :headers
    {"X-Liberator-Trace" (":decision (:service-available? true)"
    ":decision (:known-method? :get)" ":decision (:uri-too-long? false)"
    ":decision (:method-allowed? :get)" ":decision (:malformed? false)"
    ":decision (:authorized? true)" ":decision (:allowed? true)"
    ":decision (:valid-content-header? true)" ":decision
    (:known-content-type? true)" ":decision (:valid-entity-length? true)"
    ":decision (:is-options? false)" ":decision (:accept-exists? true)"
    ":decision (:media-type-available? {:representation {:media-type
    \"application/json\"}})" ":decision (:accept-language-exists? nil)"
    ":decision (:accept-charset-exists? nil)" ":decision
    (:accept-encoding-exists? nil)" ":decision (:exists? true)" ":decision
    (:if-match-exists? nil)" ":decision (:if-unmodified-since-exists?
    nil)" ":decision (:if-none-match-exists? nil)" ":decision
    (:if-modified-since-exists? nil)" ":decision (:method-delete? false)"
    ":decision (:post-to-existing? false)" ":decision (:put-to-existing?
    false)" ":decision (:multiple-representations? false)" ":handler
    (:handle-ok)"), "X-Liberator-Trace-Id" "wzakh", "Content-Type"
    "application/json;charset=UTF-8", "Vary" "Accept", "ETag"
    "\"136920569\""}, :status 200}

    ;; you can still access the log later:
    (pprint (liberator.dev/log-by-id "wzakh"))
    ["wzakh"
     [#inst "2013-05-22T06:54:54.955-00:00"
      {:headers {"accept" "application/json", "host" "localhost"},
       :uri "/",
       :request-method :get}
      [(:decision (:service-available? true))
       (:decision (:known-method? :get))
       (:decision (:uri-too-long? false))
       (:decision (:method-allowed? :get))
       (:decision (:malformed? false))
       (:decision (:authorized? true))
       (:decision (:allowed? true))
       (:decision (:valid-content-header? true))
       (:decision (:known-content-type? true))
       (:decision (:valid-entity-length? true))
       (:decision (:is-options? false))
       (:decision (:accept-exists? true))
       (:decision
        (:media-type-available?
         {:representation {:media-type "application/json"}}))
       (:decision (:accept-language-exists? nil))
       (:decision (:accept-charset-exists? nil))
       (:decision (:accept-encoding-exists? nil))
       (:decision (:exists? true))
       (:decision (:if-match-exists? nil))
       (:decision (:if-unmodified-since-exists? nil))
       (:decision (:if-none-match-exists? nil))
       (:decision
     (:if-modified-since-exists? nil))
       (:decision (:method-delete? false))
       (:decision (:post-to-existing? false))
       (:decision (:put-to-existing? false))
       (:decision (:multiple-representations? false))
       (:handler (:handle-ok))]]]
    ;; => nil
{% endhighlight %}

If you happen to run the application and have wrapped it in wrap-trace
then the request from the repl are also available at the trace ui.


