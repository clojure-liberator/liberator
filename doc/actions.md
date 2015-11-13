---
layout: default
title: Actions
---

# Initializing the context

<span class="label label-info">Not released as of 0.13</span>
````:initialize-context```` is the first action performed when a request
is being handled --- its purpose is to allow additional values to be
inserted into the context (in addition to the standard
````:representation````, ````:request```` and ````:resource```` keys --- see
the [execution model](execution-model.html) documentation) before the
request is processed further.  Note that this action should not, in
general, modify the state of the server --- it is intended solely to
provide a convenient way to thread additional values through the
execution flow.

# Actions

The processing of ````PUT````, ````DELETE````, ````POST```` and
````PATCH```` methods typically results in a changed state on the 
server-side. After the state was changed either an updated 
representation of the changed entity is returned to the client, or one
of the status codes that redirect the client to a different locations
is returned.

Liberator provides so called action functions that you can declare
with your resource definitions which are called at well suited points
during request processing.

actions             | description
--------------------|-----------------------------
:initialize-context | first function to be called. Can be used to initialize the context <span class="label label-info">since 0.14.0</span>
:post!              | called for POST requests
:put!               | called for PUT requests
:delete!            | called for DELETE requests
:patch!             | called for PATCH requests

## When are actions called?

The action `:initialize-context` is the first callback called for
a resource. It's a convenient place to initialize the resource for
every request and seed the context with data.

The action functions are called after content negotiation, existence
checks and conditional request processing -- just before deciding on
actual the response type: including an entity, redirection etc.

You can spot the actions on the [decision graph](decisions.html) as the
round nodes.
