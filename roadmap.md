---
layout: default
title: Future development
---
# Roadmap
There is still a lot to do. While we track bugs and issues at github,
this page shows the roadmap for liberator's future. This is a
collection of ideas and planned features, but there is no commitment
on when or whether a certain feature will be available.

## Core Execution
* Support language subtags ("en-gb", etc.)
* Send sensible default response for OPTIONS requests, include
  "Allow"-Header etc.
* Declarative cache control

## Representation generation
* Make adding additional media types easier. This includes refactoring
  the representation generation to dispatch on the media type first
  and on the data type second. Currently the first dispatch considers
  the data type.
* Support moder API media types out of the box, like HAL, and JSON-API
* Make adding custom headers easier

## Request entity processing
* Automatically parse known content-type for requests with body. E.g.
  for POST request, parse application/json and handle the associated
  decisions, like :malformed?, automatically.
* Add support for status 422 to reject a semantically invalid body.
