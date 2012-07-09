Compojure-rest is a thin layer for building RESTful applications on top
of compojure. It is loosely modeled after webmachine. 
It provides a bunch of decorates which can be combined to provide a
sophisticated implementation of the HTTP RFC.

# Defining resources

Resources are created with ```resource``` taking keyword arguments.

Here's the classic 'Hello World' example :-

    (resource :handle-ok "Hello World!")

# Ring compatibility

Resources are compatible with Ring and can be wrapped in Ring middleware.
When evaluated, a ```resource``` returns a function which takes a Ring request and returns a Ring response. This means that resources can be wrapped in standard Ring middleware.

# Routing web requests

Resources do not define routes but are compatible with routing libraries.

For example, to route a request with Compojure, wrap your resource in a ```routes``` wrapper :-

    (ns example
      (:use [compojure.core :only [routes]]))

    (routes 
      (ANY "/greeting" [] 
        (resource :handle-ok "Hello World!")))

To route a request with Moustache, declare your resource as the handler :-

    (ns example
        (:use [net.cgrand.moustache :only [app]]))

    (app 
      ["/greeting"] (resource :handle-ok "Hello World!"))

# Named resources

You may want to separate your routes from their target resources. There's a macro to provide a shorthand for this :-

    (defresource greeting :handle-ok "Hello World!")

# Resource maps

The arguments given to ```resource``` are given as keyword-value pairs. These pairs are known as _overrides_ and combined with a resource's default entries make up a map known as the _resource map_.

# Overrides

Overrides fall into 4 categories.

* Decisions (keywords ending in a ```?```)
* Handlers (keywords starting with ```handle-```)
* Actions (keywords ending in a ```!```)
* Declarations (keywords that don't fall into the categories above)

## Decisions

When handling a web request, numerous decisions are made which determine the response. You can influence the response by electing to make one or more of these decisions.

For example, the ```method-allowed?``` decision dictates where a given request method should be accepted :-

    (resource :handle-ok "Hello World!"
              :service-available? true
              :method-allowed? #(#{:get :put :post} (get-in % [:request :request-method])))

Decision values can be either constants evaluated at compile time or functions evaluated at runtime.

### Decision functions

When you decide to override a decision with a function, this function takes a single argument (called the _context_) which is a map containing any state that has been accumulated during the processing of the resource request.

At a minimum this map contains the following entries :-

* :request - the Ring request
* :resource - the resource map containing the values of all the possible overrides.

Depending on how far the processing of the request has got, the map may also contain results of any content negotiation keyed under ```:representation```.

The function must be return one of the following :-

* A boolean, determining the next step in the decision tree.
* A map to be merged with the context, indicating a true value.
* A vector pair containing a boolean as the first item and a map as the second item.

## Handlers

Handlers return a body response. They can be dynamic, in which case they
are functions taking a single argument called the _context_.

The _context_ is a map containing 5 entries :-

* :request - the Ring request.
* :resource - the resource map containing the values of all the possible
  overrides.
* :representation - a map reflecting the result of content negotiation
  between the user agent and server.
* :status - the negotiated HTTP response status code.
* :message - the message which normally accompanies the status code.

The result of a handler is a Ring response which is map containing the
status, header map and body keyed with ```:status```, ```:headers``` and
```:body``` respectively.

If you don't set one of the entries it will be set to the appropriate
default value.

You can also return anything that can be coerced into a Ring response
(by implementing ```compojure-rest.representation.Representation```
protocol). Out-of-the-box this includes String, File and InputStream
instances, plus the usual Clojure data types.

## Actions

Actions work the same way as handlers but cannot return
anything. Actions presume some side-effect will occur. They are called
when some underlying resource state is to be mutated during the POST,
PUT and DELETE methods of the HTTP protocol.

## Declarations

Declarations work the same way as decisions but return a value rather than a boolean. Resources indicate their capabilities via declarations. What each declaration should provide depends on the declaration type so you should check examples and documentation.

# Examples

Examples can be found in the examples/ dir.

# Reference: List of decisions

Decision points can be :-

* allowed? (decision)
* authorized? (decision)
* available-charsets (declaration)
* available-encodings (declaration)
* available-languages (declaration)
* available-media-types (declaration)
* can-post-to-missing? (decision)
* charset-available? (decision)
* conflict? (decision)
* delete! (action)
* encoding-available? (decision)
* etag (declaration)
* existed? (decision)
* exists? (decision)
* handle-created (handler)
* handle-gone (handler)
* handle-malformed (handler)
* handle-method-not-allowed (handler)
* handle-multiple-representations (handler)
* handle-multiple-representations (handler)
* handle-no-content (handler)
* handle-not-acceptable (handler)
* handle-not-found (handler)
* handle-not-found (handler)
* handle-not-implemented (handler)
* handle-not-modified (handler)
* handle-ok (handler)
* handle-precondition-failed (handler)
* handle-request-entity-too-large (handler)
* handle-service-not-available (handler)
* handle-unsupported-media-type (handler)
* handle-uri-too-long (handler)
* known-content-type? (decision)
* known-method? (decision)
* language-available? (decision)
* malformed? (decision)
* method-allowed? (decision)
* multiple-representations? (decision)
* new? (decision)
* post! (action)
* post-redirect? (decision)
* post-to-existing? (decision)
* put! (action)
* put-to-different-url? (decision)
* respond-with-entity? (decision)
* see-other (declaration)
* service-available? (decision)
* unauthorized (handler)
* unknown-method (handler)
* uri-too-long? (decision)
* valid-content-header? (decision)
* valid-entity-length? (decision)

available-* = option
.*\? = decision
.*\! = action
:else handler

# License

Compojure-rest is licensed under EPL 1.0 (see file epl-v10.html).
