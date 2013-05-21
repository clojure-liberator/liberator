---
layout: default
title: Changelog
---
# New in 0.9.0

## Changes

* UTF-8 is now the default charset for Representations
* Adds web console for traces, include trace link header
* Add "ETag" and "Last-Modified" automatically
* Add "Vary" automatically
* Add declaration :available-media-types?
* Add support for HEAD request
* Extractor for graphivz dot file that reads core.clj
* Bump hiccup dependency to 1.0.2
* Add can-put-to-missing? 
* Fix representation render-map-csv
* Make liberator build with lein 2.0.0RC1 (manage dependencies)
* Drop unnecessary methods from Representation
* Dispatch Representation on MapEquivalence and Sequential which
  increased robustnes
* Fixes to HTML Table representation (missing tr)
* Render Clojure Representation using \*print-dup\* 

## Bugs fixed

* \#28 Head requests
* Do not re-use generated ETag and Last-Modified during request
  because they can have changed after post! et. al.
* Handlers for redirect status work now reliably
* Fix Postbox example using value, not function for post!

# New in 0.8.0

## Changes

* Include olympics example data in source tree

## Bugs fixes 
* Handle line-break and whitespace in Accept headers
* Ignore case in character set negotiation
* \#12 String representation sets character set
* \#9 Missing media-type for "hello george" example
* \#11 
* \#14 Use newer org.clojure:data.csv

# New in 0.7.0

Revision 0.7.0 has been accidently skipped
