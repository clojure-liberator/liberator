(ns compojure-rest.util
  (:import java.util.TimeZone
           java.text.SimpleDateFormat
           java.util.Locale
           java.util.Date))

(defn apply-if-function [function-or-value request]
  (if (fn? function-or-value)
    (function-or-value request)
    function-or-value))

(def http-date-format-template "EEE, dd MMM yyyy HH:mm:ss Z")

(defprotocol CoerceTimezone
  (as-timezone [x]))

(extend-protocol CoerceTimezone
  String (as-timezone [tz] (TimeZone/getTimeZone tz))
  TimeZone (as-timezone [tz] tz))

(defn http-date-format
  ([] (http-date-format (TimeZone/getDefault)))
  ([tz] (let [df (new SimpleDateFormat
		      http-date-format-template
		      Locale/US)]
	  (do (.setTimeZone df (as-timezone tz))
	      df))))

(defn relative-date [int]
  (new Date (+ int (System/currentTimeMillis))))

(defn http-date
  ([date] (.format (http-date-format) date))
  ([date timezone] (.format (http-date-format timezone) date)))

(defn parse-http-date [date-string]
  (try 		      
   (.parse http-date-format date-string)
   (catch java.text.ParseException e nil)))