(ns test-representation
  (:use
   midje.sweet
   [liberator.representation]))

;; test for issue #19
;; https://github.com/clojure-liberator/liberator/pull/19

(facts "Can produce csv from map"
       (fact
        (as-response (->MapRepresentation {:foo "foo" :bar "bar"}) {:representation {:media-type "text/csv"}})
        => {:body "name,value\r\n:foo,foo\r\n:bar,bar\r\n"}))

(facts "Can produce tsv from map"
       (fact
        (as-response (->MapRepresentation {:foo "foo" :bar "bar"}) {:representation {:media-type "text/tab-separated-values"}})
        => {:body "name\tvalue\r\n:foo\tfoo\r\n:bar\tbar\r\n"}))

