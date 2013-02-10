(ns test-representation
  (:use
   midje.sweet
   [liberator.representation]))

;; test for issue #19
;; https://github.com/clojure-liberator/liberator/pull/19

(facts "Can produce representations from map"
       (tabular "Various media types are supported"
                (as-response (->MapRepresentation {:foo "bar" :baz "qux"}) {:representation {:media-type ?media-type}})
                => {:body ?body}
                ?media-type   ?body
                "text/csv"    "name,value\r\n:foo,bar\r\n:baz,qux\r\n"
                "text/tab-separated-values" "name\tvalue\r\n:foo\tbar\r\n:baz\tqux\r\n"
                "text/plain"  "foo=bar\r\nbaz=qux"))



