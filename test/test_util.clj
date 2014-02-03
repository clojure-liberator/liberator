;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns test-util
  (:use midje.sweet
        liberator.util))

(fact "merge-with-map: If the supplied function map has a function to
   merge with, use that. Otherwise, right wins."
  (merge-with-map {:a +
                   :b -}
                  {:a 1 :b 10}
                  {:a 2 :c "hi!"}
                  {:a 3 :b 5 :c "ho!"})
  => {:a 6, :b 5, :c "ho!"})

(defn sans-merge [m]
  (chatty-checker [l] (= m (dissoc l :base-merge-with))))

(fact "Merging maps with flatten-resource works:"
  (flatten-resource
   {:base {:a "a"} :b "b"}) => {:a "a" :b "b"}

  "Clashing keys are either knocked out:"
  (flatten-resource
   {:base {:a "a"} :a "b"}) => {:a "b"}

  "Or merged with the :base-merge-with binary function:"
  (flatten-resource
   {:base {:a "a"}
    :base-merge-with (fn [a b] a)
    :a "b"}) => (sans-merge {:a "a"})

  (let [base {:a "a"
              :base {:key "value"
                     :a "deepest"}
              :base-merge-with (fn [a b] a)}
        nested {:base base
                :a "b"
                :new "old"}]
    "This works recursively, with the lowest merge-with function
  sticking around for some time:"
    (flatten-resource nested)
    => (sans-merge {:a "deepest"
                    :key "value"
                    :new "old"})

    "Combat this by nuking it with a nil, or a new map:"
    (let [merger (fn [a b] a)]
      (flatten-resource
       {:base base
        :a "b"
        :base-merge-with nil
        :new "old"})
      => (sans-merge {:a "b"
                      :key "value"
                      :new "old"}))))
