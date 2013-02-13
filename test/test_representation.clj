(ns test-representation
  (:use
   midje.sweet
   [liberator.representation]))

;; test for issue #19
;; https://github.com/clojure-liberator/liberator/pull/19

(defn- pr-str-dup [x]
  (binding [*print-dup* true]
    (pr-str x)))

(facts "Can produce representations from map"
       (let [entity {:foo "bar" :baz "qux"}]
         (tabular "Various media types are supported"
                  (as-response entity {:representation {:media-type ?media-type :charset "UTF-8"}})
                  => {:body ?body :headers { "Content-Type" (str ?media-type ";charset=UTF-8")}}
                  ?media-type   ?body
                  "text/csv"    "name,value\r\n:foo,bar\r\n:baz,qux\r\n"
                  "text/tab-separated-values" "name\tvalue\r\n:foo\tbar\r\n:baz\tqux\r\n"
                  "text/plain"  "foo=bar\r\nbaz=qux"
                  "text/html"   (str "<div><table><tbody>"
                                     "<tr><th>foo</th><td>bar</td></tr>"
                                     "<tr><th>baz</th><td>qux</td></tr>"
                                     "</tbody></table></div>")
                  "application/json" (clojure.data.json/write-str entity)
                  "application/clojure" (pr-str-dup entity))))

(facts "Can produce representations from a seq of maps"
       (let [entity [{:foo 1 :bar 2} {:foo 2 :bar 3}]]
         (tabular "Various media types are supported"
                  (as-response entity {:representation {:media-type ?media-type :charset "UTF-8"}})
                  => {:body ?body :headers { "Content-Type" (str ?media-type ";charset=UTF-8")}}
                  ?media-type   ?body
                  "text/csv"    "foo,bar\r\n1,2\r\n2,3\r\n"
                  "text/tab-separated-values" "foo\tbar\r\n1\t2\r\n2\t3\r\n"
                  "text/plain"  "foo=1\r\nbar=2\r\n\r\nfoo=2\r\nbar=3"
                  "text/html"   (str "<div><table>"
                                     "<thead><tr><th>foo</th><th>bar</th></tr></thead>"
                                     "<tbody>"
                                     "<tr><td>1</td><td>2</td></tr>"
                                     "<tr><td>2</td><td>3</td></tr>"
                                     "</tbody>"
                                     "</table></div>")
                  "application/json" (clojure.data.json/write-str entity)
                  "application/clojure" (pr-str-dup entity))))

