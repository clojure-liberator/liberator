---
layout: default
title: Handlers
---
# Handlers

For every http status code there is a handler function defined in
liberator. All have sensible defaults and will return a simple english
error message or an empty response, whatever is appropriate.

Handler key                     | status code | redirect?
--------------------------------|-------------|----------
handle-ok                       | 200         | |
handle-created                  | 201         | |
handle-options                  | 201         | |
handle-accepted                 | 202         | |
handle-no-content               | 204         | |
handle-moved-permanently        | 301         | yes
handle-see-other                | 303         | yes
handle-not-modified             | 304         | |
handle-moved-temporarily        | 307         | yes
handle-multiple-representations | 310         | |
handle-malformed                | 400         | |
handle-unauthorized             | 401         | |
handle-forbidden                | 403         | |
handle-not-found                | 404         | |
handle-method-not-allowed       | 405         | |
handle-not-acceptable           | 406         | |
handle-conflict                 | 409         | |
handle-gone                     | 410         | |
handle-precondition-failed      | 412         | |
handle-request-entity-too-large | 413         | |
handle-uri-too-long             | 414         | |
handle-unsupported-media-type   | 415         | |
handle-unprocessable-entity <span class="label label-info">since 0.9.0</span>     | 422         | |
handle-not-implemented          | 501         | |
handle-unknown-method           | 501         | |
handle-service-not-available    | 503         | |

## Redirecting

The handlers that are marked as redirecting will set a "Location"
header that is generated from the following mechanism:

### Lookup in context <span class="label label-info">since 0.9.0</span>

Liberator uses the lookup key ````:location```` in the context. You
can set this key from a decision function that decides on the
redirection, e.g. ````:post-redirect?````. The value can either be a
constant value or a function that will be called with the current
context.

Example:

{% highlight clojure %}
(defresource postbox
  :allowed-methods [:post]
  :post-redirect? (fn [ctx] {:location "http://example.com/look-here/}))
{% endhighlight %}

You could also use a constant value for :post-redirect like the
[execution model](execution-model.html) for liberator describes.
   
### Lookup in resource

If no location was found in the context, then Liberator will lookup
the key ````:location```` in the resource definition. Note that this
value is used for all redirecting handlers if :location was not set
the context.

Example:

{% highlight clojure %}
(defresource postbox
  :allowed-methods [:post]
  :post-redirect? true 
  :location (fn [ctx] "http://example.com/look-here/"))
{% endhighlight %}
