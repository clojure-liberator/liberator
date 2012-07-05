(ns compojure-rest.util
  (:import java.util.TimeZone
           java.text.SimpleDateFormat
           java.util.Locale
           java.util.Date))

(defn apply-if-function [function-or-value request]
  (if (fn? function-or-value)
    (function-or-value request)
    function-or-value))

(defn http-date-format []
  (let [df (new SimpleDateFormat
                "EEE, dd MMM yyyy HH:mm:ss"
                Locale/US)]
    (do (.setTimeZone df (TimeZone/getTimeZone "GMT"))
        df)))

(defn relative-date [future]
  (Date. (+ (System/currentTimeMillis) future)))

(defn http-date [date]
  (format "%s GMT" (.format (http-date-format) date)))

(defn parse-http-date [date-string]
  (if (nil? date-string)
    nil
    (try 		      
      (.parse (http-date-format) date-string)
      (catch java.text.ParseException e nil))))
