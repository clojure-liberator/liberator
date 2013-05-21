---
layout: default
title: Debugging
---
# Debugging the execution flow

Finding out why liberator comes to a certain result can be tedious.
despite sprinking trace statemens over your code, liberator can also
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
debugging with curl or other console tools

### Trace user interface

Sometimes, the full response header is not easily available or you
want to "look back later". Liberator provides a web resource at
````/x-liberator/requests```` where a list of recent requests can be
found. The trace for a single request can be selected there. 

For the trace-ui to be accessible it is mandatory that the wrapped
handler is invoked for trace ui resource. The simplest way to make
this is to add wrap-trace to the stack of ring middleware outside the
routing.

### Linking to a request's trace

In any case a link header will be returned with rel=x-liberator-trace.
It points to the request on the trace ui. The current id can also be
retreived programatically using ````(current-trace-url)````. 

### Runtime access to the current trace id

The current trace id is bound to ````liberator.dev/*current-id*````.
You can generate a link to the ui console for the current request
and embed a HTML-snippet of a hovering link.

function            | description
--------------------|------------
````current-trace-url````   | generates a URL pointing to the trace ui for the current request
````include-trace-panel```` | generates an HTML snippet with a link to thetrace ui
````css-url````             | generates a URL to CSS for the above HTML-snippet

## Debugging and testing in the REPL
