---
layout: default
title: Actions
---

# Actions

For non-idempotent methods, i.e. ````PUT````, ````DELETE```` and
````POST```` processing the request typically changes the state on the
server side. After the state was changed either an updated
representation of the changed entity is returned to the client, or one
of the status codes that redirect the client to a different locations
is returned.

Liberator provides so called action functions that you can declare
with your resource definitions which are called at well suited points
during request processing.

actions  | description
---------|-----------------------------
:post!   | called for POST requests
:put!    | called for PUT requests
:delete! | called for DELETE requests

## When are actions called?

The action functions are called after content negotiation, existence
checks and conditional request processing -- just before deciding on
actual the response type: including an entity, redirection etc.

You can spot the actions on the [decision graph](decisions.html) as the
round nodes.
