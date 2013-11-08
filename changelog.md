---
layout: default
title: Changelog
---
# Changelog

# New in 0.10.0

## Bugs fixed

* Reenable suppport for keyword as a handler function
* \#71 Add locations header to 201 created
* \#65 Make sure svg path is highlighted
* \#77 Multiple link header values as vector
* \#49 OPTIONS should return 200 OK and "Allow" header
* \#50 HTTP 405 response must include an Allow-Header
* \#68 handle-options sends 201 created and not 200 or 204

## New in 0.9.0

### Changes

* UTF-8 is now the default character set for Representations
* New web console for traces, include trace link header
* Add "ETag" and "Last-Modified" automatically
* Add "Vary" automatically
* Add declaration :available-media-types?
* Add support for HEAD request
* Extractor for graphviz dot file that reads core.clj
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

### Bugs fixed

* \#28 Head requests
* Do not re-use generated ETag and Last-Modified during request
  because they can have changed after post! et. al.
* Handlers for redirect status work now reliably
* Fix Postbox example using value, not function for post!

## New in 0.8.0

### Changes

* Include olympics example data in source tree

### Bugs fixes
* Handle line-break and whitespace in Accept headers
* Ignore case in character set negotiation
* \#12 String representation sets character set
* \#9 Missing media-type for "hello george" example
* \#11
* \#14 Use newer org.clojure:data.csv

## New in 0.7.0

Revision 0.7.0 has been accidentally skipped
