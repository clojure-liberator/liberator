(ns compojure-rest.conneg
  (:use clojure.tools.trace)
  (:require [clojure.string :as string]))

;;;
;;; TODO: sort by level for text/html. Maybe also sort by charset.
;;; Finally, compare by precedence rules:
;;;   1. text/html;level=1
;;;   2. text/html
;;;   3. text/*
;;;   4. */*
;;;   

(def accept-fragment-re
  #"^(\*|[^()<>@,;:\"/\[\]?={}         ]+)/(\*|[^()<>@,;:\"/\[\]?={}         ]+)$")

(def accept-fragment-param-re
  #"^([^()<>@,;:\"/\[\]?={} 	]+)=([^()<>@,;:\"/\[\]?={} 	]+|\"[^\"]*\")$")

(defn- clamp [minimum maximum val]
  (min maximum
       (max minimum val)))

(defn- parse-q [#^String str]
  (Double/parseDouble str))

(defn- assoc-param [coll n v]
  (try
    (assoc coll
           (keyword n) 
           (if (= "q" n)
             (clamp 0 1 (parse-q v))
             v))
    (catch Throwable e
      coll)))
    
(defn params->map [params]
  (loop
    [p (first params)
     ps (rest params)
     acc {}]
    (let [x (when p
              (rest (re-matches accept-fragment-param-re p)))
          accumulated (if (= 2 (count x))
                        (assoc-param acc (first x) (second x))
                        acc)]
      (if (empty? ps)
        accumulated
        (recur
          (first ps)
          (rest ps)
          accumulated)))))
 
(defn accept-fragment
  "Take something like
    \"text/html\"
  or
    \"image/*; q=0.8\"
  and return a map like
    {:type [\"image\" \"*\"]
      :q 0.8}
    
  If the fragment is invalid, nil is returned.
  
  Eventually, a `weights` map will be input, used to accord a server-side
  weight to a format."

  ([f]
   (let [parts (string/split f #"\s*;\s*")]
     (when (not (empty? parts))
       ;; First part will be a type.
       (let [type-str (first parts)
             type-pair (rest (re-matches accept-fragment-re type-str))]
         (when type-pair
           (assoc
             (params->map (rest parts))
             :type type-pair)))))))

(defn sort-by-q [coll]
  (reverse     ; Highest first.
    (sort-by #(get %1 :q 1)
             coll)))

(defn sorted-accept [h]
  (sort-by-q
    (map accept-fragment
         (string/split h #"\s*,\s*"))))

(defn acceptable-type
  "Compare two type pairs. If the pairing is acceptable,
   return the most specific.
  E.g., for
  
    [\"text\" \"plain\"] [\"*\" \"*\"]
  
  returns
  
    [\"text\" \"plain\"]."
  [type-pair acceptable-pair]
  
  (cond
    (or (= type-pair acceptable-pair)
        (= ["*" "*"] acceptable-pair))
    type-pair
    
    (= ["*" "*"] type-pair)
    acceptable-pair
    
    true
    ;; Otherwise, maybe one has a star.
    (let [[tmaj tmin] type-pair
          [amaj amin] acceptable-pair]
      (when (= tmaj amaj)
        (cond
          (= "*" tmin)
          acceptable-pair
          
          (= "*" amin)
          type-pair)))))
              
(defn allowed-types-filter [allowed-types]
  (fn [accept]
    (some (partial acceptable-type accept)
          allowed-types)))

(defn- enpair
  "Ensure that a collection of types is a collection of pairs."
  [x]
  (filter identity
          (map (fn [y] (if (string? y)
                         (:type (accept-fragment y))
                         y))
               x)))

(defn- first-fn
  "Return (f x) for the first item in coll for which (f x) is true."
  [f coll]
  (first (filter identity (map f coll))))
  
(defn best-allowed-content-type
  "Return the first type in the Accept header that is acceptable.
  allowed-types is a set containing pairs (e.g., [\"text\" \"*\"])
  or strings (e.g., \"text/plain\").
  
  Definition of \"acceptable\":
  An Accept header fragment of \"text/*\" is acceptable when allowing
  \"text/plain\".
  An Accept header fragment of \"text/plain\" is acceptable when allowing
  \"text/*\"."

  ([accepts-header]
   (best-allowed-content-type accepts-header true))
  ([accepts-header
    allowed-types]    ; Set of strings or pairs. true/nil/:all for any.
     (let [sorted (map :type (sorted-accept accepts-header))]
      (cond
        (contains? #{:all nil true} allowed-types)
        (first sorted)
 
        (fn? allowed-types)
        (first-fn (enpair allowed-types) sorted)
 
        true
        (first-fn (allowed-types-filter (enpair allowed-types)) sorted)))))




