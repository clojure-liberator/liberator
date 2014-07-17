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

(defn relative-date [future]
  (Date. (long (+ (System/currentTimeMillis) future))))

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
