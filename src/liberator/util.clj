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

(defn ^SimpleDateFormat http-date-format []
  (let [df (new SimpleDateFormat
                "EEE, dd MMM yyyy HH:mm:ss z"
                Locale/US)]
    (do (.setTimeZone df (TimeZone/getTimeZone "GMT"))
        df)))

(defn relative-date [^long future]
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

;; A more sophisticated update of the request than a simple merge
;; provides.  This allows decisions to return maps which modify the
;; original request in the way most probably intended rather than the
;; over-destructive default merge.
(defn combine
  "Merge two values such that two maps a merged, two lists, two
  vectors and two sets are concatenated.

  Maps will be merged with maps. The map values will be merged
  recursively with this function.

  Lists, Vectors and Sets will be concatenated with values that are
  `coll?` and will preserve their type.

  For other combination of types the new value will be returned.

  If the newval has the metadata attribute `:replace` then it will
  replace the value regardless of the type."
  [curr newval]
  (cond
   (-> newval meta :replace) newval
   (and (map? curr) (map? newval)) (merge-with combine curr newval)
   (and (list? curr) (coll? newval)) (concat curr newval)
   (and (vector? curr) (coll? newval)) (concat curr newval)
   (and (set? curr) (coll? newval)) (set (concat curr newval))
   :otherwise newval))

