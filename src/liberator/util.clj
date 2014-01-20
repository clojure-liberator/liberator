(ns liberator.util
  (:import java.util.TimeZone
           java.text.SimpleDateFormat
           java.util.Locale
           java.util.Date))

(defn make-function [x]
  (if (or (fn? x) (keyword? x)) x (constantly x)))

(defn apply-if-function [function-or-value request]
  (if (fn? function-or-value)
    (function-or-value request)
    function-or-value))

(defprotocol DateCoercions
  (as-date [_]))

(extend-protocol DateCoercions
  java.util.Date
  (as-date [this] this)
  Long
  (as-date [millis-since-epoch]
    (java.util.Date. millis-since-epoch))
  nil
  (as-date [this] nil))

(defn http-date-format []
  (let [df (new SimpleDateFormat
                "EEE, dd MMM yyyy HH:mm:ss z"
                Locale/US)]
    (do (.setTimeZone df (TimeZone/getTimeZone "GMT"))
        df)))

(defn relative-date [future]
  (Date. (+ (System/currentTimeMillis) future)))

(defn http-date [date]
  (format "%s" (.format (http-date-format) date)))

(defn parse-http-date [date-string]
  (if (nil? date-string)
    nil
    (try
      (.parse (http-date-format) date-string)
      (catch java.text.ParseException e nil))))

(defn by-method [& kvs]
  (fn [ctx]
    (let [m (apply hash-map kvs)
          method (get-in ctx [:request :request-method])]
      (if-let [fd (make-function (or (get m method) (get m :any)))] (fd ctx)))))

(defn merge-with-map
  "Returns a map that consists of the rest of the maps conj-ed onto
  the first.  If a key occurs in more than one map, the mapping(s)
  from the latter (left-to-right) will be combined with the mapping in
  the result by looking up the proper merge function and in the
  supplied map of key -> merge-fn and using that for the merge. If a
  key doesn't have a merge function, the right value wins (as with
  clojure.core/merge)."
  [merge-fns & maps]
  (when (some identity maps)
    (let [merge-entry (fn [m e]
			(let [k (key e) v (val e)]
			  (if-let [f (and (contains? m k)
                                          (merge-fns k))]
			    (assoc m k (f (get m k) v))
			    (assoc m k v))))
          merge2 (fn [m1 m2]
		   (reduce merge-entry (or m1 {}) (seq m2)))]
      (reduce merge2 maps))))

(defn flatten-resource
  "Accepts a map (or a sequence, which gets turned into a map) of
  resources; if the map contains the key :base, the kv pairs from THAT
  map are merged in to the current map. If there are clashes, the new
  replaces the old by default.

  Combat this by supplying a :base-merge-with function in the
  map. This key should point to a map from keyword -> binary function;
  this function will be used to resolve clashes for that particular
  keyword."  [kvs]
  (let [m (if (map? kvs)
            kvs
            (apply hash-map kvs))
        trim #(dissoc % :base)]
    (if-let [base (:base m)]
      (let [combined (flatten-resource base)
            trimmed (trim m)]
        (if-let [merger (if (contains? m :base-merge-with)
                          (:base-merge-with m)
                          (:base-merge-with combined))]
          (if (fn? merger)
            (merge-with merger combined trimmed)
            (merge-with-map merger combined trimmed))
          (merge combined trimmed)))
      (trim m))))
