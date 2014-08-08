(ns test-util
  (:require [liberator.util :refer :all]
            [midje.sweet :refer :all]))

(facts "combine function"
  (facts "simple combinations"
    (fact "merges map" (combine {:a 1} {:b 2}) => {:a 1 :b 2})
    (fact "concats list" (combine '(1 2) #{3 4}) => '(1 2 3 4))
    (fact "concats vector" (combine [1 2] '(3 4)) => [1 2 3 4])
    (fact "concats set" (combine #{1 2} [3 4]) => #{1 2 3 4})
    (facts "replaces other types"
      (fact (combine 123 456) => 456)
      (fact (combine "abc" 123 => 123))
      (fact (combine [] "abc" => "abc")))
    (facts "replaces for different types"
      (fact (combine [1 2 3] 1) => 1)
      (fact  (combine '(1 2 3) 1) => 1)
      (fact  (combine {1 2 3 4} 1) => 1)))
  (facts "prevent merge with meta :replace"
    (fact "replaces map" (combine {:a 1} ^:replace {:b 2}) => {:b 2})
    (fact "replaces list" (combine '(1 2) ^:replace #{3 4}) => #{3 4})
    (fact "replaces vector"
      (combine [1 2] (with-meta (list 3 4) {:replace true})) => '(3 4))
    (fact "replaces set" (combine #{1 2} ^:replace [3 4]) => [3 4]))
  (facts "deep merges"
    (fact "map values are recursively merged"
      (combine {:a [1]
                :b '(2)
                :c {:x [3]}
                :d 4
                :e [:nine]}
               {:a '(5)
                :b #{6}
                :c {:x [7]}
                :d 8
                :e ^:replace [:ten]})
      => {:a [1 5]
          :b '(2 6)
          :c {:x [3 7]}
          :d 8
          :e [:ten]}))
  (facts "response updates"
    (combine {:status 200
              :body "foo"
              :headers {"Content-Type" "text/plain"
                        "X-Dummy" ["banana" "apple"]}}
             {:headers {"Content-Type" "text/something+plain"
                        "X-Dummy" ["peach"]}})
    => {:status 200
        :body "foo"
        :headers {"Content-Type" "text/something+plain"
                  "X-Dummy" ["banana" "apple" "peach"]}}))

