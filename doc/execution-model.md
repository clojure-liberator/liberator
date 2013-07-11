---
layout: default
title: Execution model
---
# Execution model

The execution flow is driven by the outcome of the decision
function. This is how liberator determines the final status code and
which handler must be used. To propagate information like the
negotiated media-type or whether a resource exists, a
*context* map is passed along.

## Context

The context initially contains the following keys

* ````:representation```` - a map containing the negotiated representation
   parameters such as ````:media-type````, ````:charset````,
   ````:language```` and ````:encoding````. This map will be filled by
   the decision functions like ````:media-type-available?````
*  ````:request```` - the original ring request
*  ````:resource```` - the map that defines the resource, including decision
   function etc.

The context is updated by the outcome of decision and action functions.
When the outcome is a map it is deeply merged with the context. "Deeply merged"
means that map values in the context are merged with the outcome's
value. Sequential values are appended with the outcome's sequence.

Updating the context with a single new key is easy: just return a map
with the single key:

{% highlight clojure %}
(defresource foo 
  :exists? (fn [ctx] (if-let [x (lookup-entity)] {:entity x}))
  :handle-ok :entity)
{% endhighlight %}

If the resource exists, the context will be merged with the map 
````{:entity x}````. The handler function is the keyword under which
the entity was stored in the context and makes use of the fact that
a keyword is a function that can "lookup itself" from a map.

## Decision functions

Every decision in a resource is implemented by a decision function.
Its outcome is interpreted as a boolean value. If the value is a
vector then the first element is used as the boolean decision and the
second element is used to update the context.

decision function result     | boolean value | context update
-----------------------------|---------------|---------------
````true````                 | true          | _no-update_
````false````                | false         | _no-update_
````{:foo :bar}````          | true          | ````{:foo :bar}````
````[true, {:foo :bar}]````  | true          | ````{:foo :bar}````
````[false, {:foo :bar}]```` | false         | ````{:foo :bar}````

## Action functions

The keys ````:post!````, ````:put!```` and ````:delete!```` provide
points that are suited well to enact side effects. While they are
evaluated like decision functions, the boolean value has no effect and
the next decision step is constant. The context update works exactly
like for decision functions.

## Handler functions and representations

Handlers are the final step of processing a request. Like decision and
action functions they must accept the context as a single argument.
Handlers are expected to return a representation for the resource.

Handlers must return a [Representation](representations.html).
