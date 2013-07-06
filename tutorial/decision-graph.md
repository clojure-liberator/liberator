---
title: Decision Graph
layout: tutorial
---
# Decision Graph

When handling a resource liberator executes a set of decisions to
determine the correct response status code and to provide the handler
function with the necessary information to generate an appropriate
response.

Decisions are made according to a flowchart which determines the order
in which decisions and places where the path splits into two.

The flowchart always ends at a handler (at the bottom of the chart)
which determines the HTTP status code in the response.

On deciding how to implement the behaviour you have designed for
your resource you will often have this diagram in front of you to
decide which functions to replace:

The Liberator flowchart - mousewheel to zoom, click-and-drag to
pan. (if you don't see anything below, please [open it]({{site.url}}assets/img/decision-graph.svg))

<span style="border: 1px solid #333; width: 90%; height: 40em; display:block;">
<object data="{{site.url}}assets/img/decision-graph.svg" width="100%" height="100%">
<img src="{{site.url}}assets/img/decision-graph.svg"
        type="image/svg+xml">
</object>
</span>

As you can see there are a lot of decisions to be made until your can
send a response.

For every decision in the graph you can supply a function in your
resource definition which will be called to obtain a boolean value.
You can e.g. access a request parameter to decide if a certain
resource exists. We wrap the handler in a ring middleware to gain easy
access to the parameters:

{% highlight clojure %}
(defroutes app
  (ANY "/secret" []
       (resource :available-media-types ["text/html"]
                 :exists? (fn [ctx]
                            (= "tiger" (get-in ctx [:request :params "word"])))
                 :handle-ok "You found the secret word!"
                 :handle-not-found "Uh, that's the wrong word. Guess again!")))
{% endhighlight %}

In this example we've given a function that checks if the request
parameter "word" equals the secret word. If it returns true, then
liberator finally reaches ````handle-ok```` else it will call
 ```handle-not-found```.

Every decision function takes a single parameter, the context, which
is a map. The ring request map is available at the key
````:request````, we use it here to access the request parameter.

We can also manipulate the context in a decision function. This is
useful when we want to pass along a value to a handler:

{% highlight clojure %}
(ANY "/choice" []
       (resource :available-media-types ["text/html"]
                 :exists? (fn [ctx]
                            (if-let [choice
                                     (get {"1" "stone" "2" "paper" "3" "scissors"}
                                          (get-in ctx [:request :params "choice"]))]
                              {:choice choice}))
                 :handle-ok (fn [ctx]
                              (format "<html>Your choice: &quot;%s&quot;."
                                        (get ctx :choice)))
                 :handle-not-found (fn [ctx]
                                     (format "<html>There is no value for the option &quot;%s&quot;"
                                             (get ctx :choice "")))))
{% endhighlight %}

In this example we check if there is an entry in a map for the
parameter "choice" and if we found one, we return a map:
````{:choice choice}````. Liberator merges a map that is returned from
a decision function with the current context. This way we can easily
store the value for the choice and retrieve it later in the handle-ok
function: ````(get ctx :choice)````.

<div class="alert alert-info">Storing data in the context is an
idiomatic pattern in liberator. The exists?-decision is a good location
to lookup an entity from a database. By doing so you can avoid
repeated costly lookups in later decision functions or in the handler.
</div>

Continue with [content negotiation](conneg.html).
