# Changelog

# New in 0.14.0

* The `defresource` macro no longer implicitly binds `request`.

* Values can be added to the context at the beginning of the execution
  flow using the :initialize-context action.
* If no handler is specified, the key :message is looked up from the
  context to create a default response.
* JSON body can be parsed into :request-entity by setting
  representation/parse-request-entity for :processable?
  parse-request-entity is a multimethod which can be extended for
  additional media types.

## Bugs fixed

* #76 Nullpointer with post options
* Allow decisions to override status in context

## Bugs fixed

* Support multimethods as decision functions.

# New in 0.13

* Optionally a value can be specified for ring-response
  together with a ring map. This value is coerced to a response
  like liberator does by default while the ring map makes it
  possible to override whatever part of the response.
* For status 201, 301, 303 and 307 the location header is added
  automatically. This used to be the case only for 201.

## Bugs fixed

* #169 Always call as-response, even for default handlers
* #206 avoid undesired deep merge of context

# New in 0.12.2

## Bugs fixed

* #162 This release actually contains the changes announced for 0.12.1
  Due to whatever reason the revision in clojars did not match
  what was tagged as 0.12.1 in the git repository.

# New in 0.12.1

## Bugs fixed

* Fix a regression and make default `:handle-exception` rethrow the
  exception. This matches the behaviour before 0.12.0
* Update the decision graph to include new paths after PATCH
  support was added.

# New in 0.12.0

* Support for PATCH method, thanks to Davig Park
* Add `:handle-exception` which is invoked when decision
  functions or handlers throw an exception.

# New in 0.11.1

## Bugs fixed

* #138 context update deeply merges values. Support workaround
  by enabling to evaluate a returned 0-ary function

# New in 0.11.0

* #97 Adds support for a default resource definition map parameter
  that simlpifies the reuse of resource definitions. This also
  adresses #95, however in a different way than it was proposed.
* #100 resources can specify :as-response to plug in custom
  implementations

## Changes

* Bumps version of hiccup to 1.0.3
* Bumps plugin versions to prepare compatibility with 1.6
  - lein-midje -> 3.1.3
  - lein-ring -> 0.8.10
  - ring-devel -> 1.2.1
  - ring-jetty-adapter -> 1.2.1
* Adds lein alias to run tests with different clojure versions

## Bugs fixed

# New in 0.10.0

## Bugs fixed

* Reenable suppport for keyword as a handler function
* #71 Add locations header to 201 created
* #65 Make sure svg path is highlighted
* #77 Multiple link header values as vector
* #49 OPTIONS should return 200 OK and "Allow" header
* #50 HTTP 405 response must include an Allow-Header
* #68 handle-options sends 201 created and not 200 or 204

# New in 0.9.0

* Improved documentation
* Add support for 422 unprocessable entity via processable?

## Changes

* Rename decision if-none-match to if-none-match?
* UTF-8 is now the default charset for Representations
* Adds web console for traces, include trace link header
* Add "ETag" and "Last-Modified" automatically
* Add "Vary" automatically
* Add declaration :available-media-types?
* Add support for HEAD request
* Rework redirecting handlers. Now supports pickup of redirect
  location from context key :location
* Extractor for graphivz dot file that reads core.clj
* Bump hiccup dependency to 1.0.2
* Add can-put-to-missing?
* Fix representation render-map-csv
* Make liberator build with lein 2.0.0RC1 (manage dependencies)
* Drop unnecessary methods from Representation
* Dispatch Representation on MapEquivalence and Sequential which
  increased robustness
* Fixes to HTML Table representation (missing tr)
* Render Clojure Representation using \*print-dup\*
* Support "application/edn" representation

## Bugs fixed

* #28 Head requests
* Do not re-use generated ETag and Last-Modified during request
  because they can have changed after post! et. al.
* Handlers for redirect status work now reliably
* Fix Postbox example using value, not function for post!

# New in 0.8.0

## Changes

* Include olympics example data with source

## Bugs fixes
* Handle line-break and whitespace in Accept headers
* Ignore case in character set negotiation
* #12 String representation sets character set
* #9 Missing media-type for "hello george" example
* #11
* #14 Use newer org.clojure:data.csv

# New in 0.7.0

Revision 0.7.0 has been accidently skipped
