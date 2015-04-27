# Liberator [![Build Status](https://travis-ci.org/clojure-liberator/liberator.svg?branch=master)](https://travis-ci.org/clojure-liberator/liberator)

Liberator is a Clojure library for building RESTful applications.

## Quick Links

You can find documentation at http://clojure-liberator.github.io/liberator

If you have any questions, visit our fine google group at https://groups.google.com/forum/#!forum/clojure-liberator

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

# Documentation

Documentation and a tutorial can be found on [http://clojure-liberator.github.io](http://clojure-liberator.github.io).

# License

Liberator is licensed under EPL 1.0 (see file epl-v10.html).
