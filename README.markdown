# Liberator

Liberator is a Clojure library for building RESTful applications. 

### Similar projects

Liberator used to be known as compojure-rest. It got renamed in July 2012. 

Liberator is loosely modeled after webmachine and shares the same aims as Bishop.

## Warming up

### Dependencies

The examples in this document rely on you installing [leiningen 2](http://leiningen.org).

We'll also use ```curl``` for testing. If you don't have curl installed (ie. you're using Windows), there's some Clojure tests you can use instead.

### Running the examples

A set of examples is included.

If you want to see the examples in a browser, run

    lein examples
    
This will start a web server on port 8000 (but you can specify a alternative port with an argument, eg. ```lein examples 8001```). Alternatively you can run the web server with ```lein ring server```).

### Ensuring the tests pass

Liberator uses [Midje](https://github.com/marick/Midje/) for testing. You can run all the tests like this :-

    lein midje

## Getting started

Let's build our first REST service!

For the purposes of this example, I'll call this project ```servalan``` but (obviously) you can invent your own name.

    projects> lein new servalan
    projects> cd !$
    servalan> ls

Notice that a new ```project.clj``` has been created - edit this and add
the following entries in the ```dependencies``` vector.

```clojure
[compojure "1.0.2"]
[ring/ring-jetty-adapter "1.1.0"]
[liberator "0.8.0"]
```

Edit the file ```src/servalan/core.clj```, adding the code in bold :-

```clojure
(ns servalan.core
  (:use [liberator.core :only [defresource]]))

(defresource my-first-resource
  :available-media-types ["text/html" "text/plain"])

(defn -main
  "I don't do a whole lot."
  [& args]
  (println "Hello, World!"))
```

# Defining resources

Resources are created with ```resource``` taking keyword arguments.

Here's the classic 'Hello World' example :-

```clojure
(resource :handle-ok "Hello World!")
```

# Ring compatibility

Resources are compatible with Ring and can be wrapped in Ring middleware.
When evaluated, a ```resource``` returns a function which takes a Ring request and returns a Ring response. This means that resources can be wrapped in standard Ring middleware.

# Routing web requests

Resources do not define routes but are compatible with routing libraries.

For example, to route a request with Compojure, wrap your resource in a ```routes``` wrapper :-

```clojure
(ns example
  (:use [compojure.core :only [routes]]))

(routes 
  (ANY "/greeting" [] 
    (resource :handle-ok "Hello World!")))
```

To route a request with Moustache, declare your resource as the handler :-

```clojure
(ns example
    (:use [net.cgrand.moustache :only [app]]))

(app 
  ["/greeting"] (resource :handle-ok "Hello World!"))
```

# Named resources

You may want to separate your routes from their target resources. There's a macro to provide a shorthand for this :-

```clojure
(defresource greeting :handle-ok "Hello World!")
```

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

```clojure
(resource :handle-ok "Hello World!"
          :service-available? true
          :method-allowed? #(#{:get :put :post} (get-in % [:request :request-method])))
```

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
(by implementing ```liberator.representation.Representation```
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
* can-put-to-missing? (decision)
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

Liberator is licensed under EPL 1.0 (see file epl-v10.html).
