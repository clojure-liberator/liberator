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
       (let [entity (sorted-map :foo "bar" :baz "qux")]
         (tabular "Various media types are supported"
                  (as-response entity {:representation {:media-type ?media-type :charset "UTF-8"}})
                  => {:body ?body :headers { "Content-Type" (str ?media-type ";charset=UTF-8")}}
                  ?media-type   ?body
                  "text/csv"    "name,value\r\n:baz,qux\r\n:foo,bar\r\n"
                  "text/tab-separated-values" "name\tvalue\r\n:baz\tqux\r\n:foo\tbar\r\n"
                  "text/plain"  "baz=qux\r\nfoo=bar"
                  "text/html"   (str "<div><table><tbody>"
                                     "<tr><th>baz</th><td>qux</td></tr>"
                                     "<tr><th>foo</th><td>bar</td></tr>"
                                     "</tbody></table></div>")
                  "application/json" (clojure.data.json/write-str entity)
                  "application/clojure" (pr-str-dup entity)
                  "application/edn" (pr-str entity))))

(facts "Can produce representations from a seq of maps"
       (let [entity [{:foo 1 :bar 2} {:foo 2 :bar 3}]]
         (tabular "Various media types are supported"
                  (as-response entity {:representation {:media-type ?media-type :charset "UTF-8"}})
                  => {:body ?body :headers { "Content-Type" (str ?media-type ";charset=UTF-8")}}
                  ?media-type   ?body
                  "text/csv"    "bar,foo\r\n2,1\r\n3,2\r\n"
                  "text/tab-separated-values" "bar\tfoo\r\n2\t1\r\n3\t2\r\n"
                  "text/plain"  "bar=2\r\nfoo=1\r\n\r\nbar=3\r\nfoo=2"
                  "text/html"   (str "<div><table>"
                                     "<thead><tr><th>bar</th><th>foo</th></tr></thead>"
                                     "<tbody>"
                                     "<tr><td>2</td><td>1</td></tr>"
                                     "<tr><td>3</td><td>2</td></tr>"
                                     "</tbody>"
                                     "</table></div>")
                  "application/json" (clojure.data.json/write-str entity)
                  "application/clojure" (pr-str-dup entity)
                  "application/edn" (pr-str entity))))


