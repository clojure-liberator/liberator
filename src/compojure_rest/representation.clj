;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns compojure-rest.representation
  (:require
   [clojure.data.json :as json]
   [clojure.data.csv :as csv])
  (:use
   [hiccup.core :only [html]]
   [hiccup.page :only [html5 xhtml]]
   [clojure.tools.logging :only [logf]]))

;; This namespace provides default 'out-of-the-box' web representations
;; for many IANA mime-types.

(defprotocol Representation
  (as-response [_ context]
    "Coerce to a standard Ring response (a map
    containing :status, :headers and :body). Developers can call
    as-response directly, usually when they need to augment the context. It
    does all the charset conversion and encoding and returns are Ring
    response map so no further post-processing of the response will be
    carried out.")
  (render-item [_ context])
  (in-charset [_ charset]
    "Output characters of the type under a given charset encoding")
  (encode [_ encoding]
    "Encode the type into the given encoding (eg. gzip, compress)"))

(defn default-dictionary [k lang]
  (println "k is " k)
  (name k))

(defn html-table [data fields lang dictionary]
  [:div [:table 
         [:thead
          (for [field fields] [:th (or (dictionary field lang)
                                       (default-dictionary field lang))])]
         [:tbody (for [row data]
                   [:tr
                    (for [field fields]
                      [:td (if-let [s (get row field)]
                             (if (re-matches #"https?://.*" s)
                               [:a {:href s} s]
                               s)
                             "")])])]]])

(defmacro ->when [pred form & term]
  `(if ~pred (-> ~form ~@term) ~form))

(defn wrap-convert-suffix-to-accept-header
  "A URI identifies a resource, not a representation. But conventional
practise often uses the suffix of a URI o indicate the media-type of the
resource - this is understandable given that browsers don't allow uses
control over the Accept header. However, if we drop the suffix from the
URI prior to processing it we can support a rich variety of
representations as well as allowing the user a degree of control by via
the URL. This function matches the suffix of a URI to a mapping between
suffixes and media-types. If a match is found, the suffix is dropped
from the URI and an Accept header is added to indicate the media-type
preference."
  [handler media-type-map]
  (fn [request]
    (let [uri (:uri request)
          is-browser? (fn [request]
                        (if-let [ua (get-in request [:headers "user-agent"])]
                          (re-matches #"Mozilla/.*" ua)))]
      
      (if-let [[suffix media-type] (some (fn [[k v]] (if (.endsWith uri k) [k v])) media-type-map)]
        (do
          (-> request
              (assoc-in [:headers "accept"] media-type)
              (assoc :uri (.substring uri 0 (- (count uri) (count suffix))))
              handler
              ;; Since we did not properly give an accept header, this is still considered to be a browser-based hack.
              ;; We set the content-type of the response to 'text/plain' so that the browser will render it for us.
              ;; Unless we're a browser and not returning something the browser can already render.
              (->when (and (is-browser? request)
                           (not (#{"text/html"
                                   "application/xhtml+xml"
                                   "application/xml"} media-type)))
                      (assoc-in [:headers "Content-Type"] "text/plain"))))
        (handler request)))))

;; Representation embodies all the rules as to who should encode the content.
;; The aim is to do more for developer's who don't want to, while seeding control for developers who need it.
;;
;; Representation is a lot like compojure.response.Renderable, but it has to deal with automatic rendering of common Clojure datatypes, charset conversion and encoding.
;;
;; TODO This needs to be extended by NIO classes: CharBuffer, ByteBuffer, exploiting CharSetEncoder, etc..
(extend-protocol Representation

  nil
  (as-response [this _] nil) ; accept defaults

  ;; Maps are what we are trying to coerce into.
  clojure.lang.APersistentMap
  (as-response [this _] this)

  (render-item [data {:keys [representation dictionary] :or {dictionary default-dictionary} :as context}]
    (let [{:keys [media-type language]} representation]
      (assert media-type)
      (assert data)
      (assert dictionary)
      (case media-type
        ("text/plain" "*/*") (str (reduce str (interpose "\r\n" (map (fn [[k v]] (str (dictionary k language) "=" v)) data))) "\r\n")
        
        ("text/csv" "text/tab-separated-values")
        (with-out-str
          (let [sep (case media-type "text/csv" \, "text/tab-separated-values" 9)]
            (csv/write-csv *out* [["name" "value"]] :newline :cr+lf :separator sep)
            (csv/write-csv *out* (seq data) :newline :cr+lf :separator sep)))
        
        "application/json" (with-out-str
                             (json/print-json data))
        "application/clojure" (with-out-str (pr data)))))

  ;; If a string is returned, we should carry out the conversion of both the charset and the encoding.
  String
  (as-response [this {:keys [representation]}]
    {:body
     (-> this
         (in-charset (:charset representation))
         (encode (:encoding representation))
         )})

  (render-item [this context]
    this)
  
  (in-charset [this charset]
    (if charset
      (java.io.ByteArrayInputStream.
       (.getBytes this (java.nio.charset.Charset/forName charset)))
      
      ;; "If no Accept-Charset header is present, the default is that
      ;; any character set is acceptable." (p101). In the case of Strings, it is unnecessary to convert to a byte stream now, and doing so might even make things harder for test-suites, so we just return the string.
      this))

  ;; TODO Convert strings to their encoded equivalents.
  (encode [this encoding] this)
  
  ;; If an input-stream is returned, we have no way of telling whether it's been encoded properly (charset and encoding), so we have to assume it is, given that we told the developer what representation was negotiated.
  java.io.File
  (as-response [this _] {:body this})
  
  java.io.InputStream
  (as-response [this _] {:body this})
  ;; We assume the input stream is already in the requested
  ;; charset. Decoding and encoding an existing charset unnecessarily
  ;; would be expensive.
  (in-charset [this charset] this)
  (encode [this encoding]
    (case encoding
      "gzip" (java.util.zip.GZIPInputStream. this)
      "identity" this
      ;; "If no Accept-Encoding field is present in a request, the server
      ;; MAY assume that the client will accept any content coding. In
      ;; this case, if "identity" is one of the available content-codings,
      ;; then the server SHOULD use the "identity" content-coding, unless
      ;; it has additional information that a different content-coding is
      ;; meaningful to the client." (p102)
      nil this))

  clojure.lang.PersistentVector
  (as-response [data context] (as-response (seq data) context))

  clojure.lang.ISeq
  (as-response [data {:keys [dictionary fields]
                 :or {dictionary default-dictionary
                      fields (keys (first data))}
                 :as context}]
    (let [{:keys [media-type language]} (:representation context)]
      (assert media-type)
      (as-response
       ;; TODO Refactor: Replace 'case' with protocols
       (case media-type
         ("text/html")
         (-> (html {:mode :html}
                   (html-table data fields language dictionary))
             (str "\r\n"))

         "application/xhtml+xml"
         (-> (xhtml {:mode :xml}
                    (html-table data fields language dictionary))
             (str "\r\n"))

         "application/xml"
         (-> (html
              [:document
               (for [row data]
                 [:entry
                  (for [field fields]
                    (when-let [s (get row field)]
                      (when-not (empty? s) [(keyword (str field "")) s])))])])
             (str "\r\n"))

         "application/json"
         (with-out-str
           (json/pprint-json data)
           (print "\r\n"))

         ("text/csv" "text/tab-separated-values")
         (with-out-str
           (let [sep (case media-type "text/csv" \, "text/tab-separated-values" 9)]
             (csv/write-csv *out* [(map #(or (dictionary % language)
                                             (default-dictionary % language)) fields)] :newline :cr+lf :separator sep)
             (csv/write-csv *out* (map (apply juxt (map (fn [x] (fn [m] (get m x))) fields)) data) :newline :cr+lf :separator sep)))
         
         ("application/clojure" "text/plain" "*/*")
         (->
          (reduce str (interpose "\r\n" (map #(render-item % (assoc context
                                                               :dictionary dictionary
                                                               :fields fields)) data)))
          (str (when (= media-type "application/clojure") "\r\n"))) ;; add a newline to application/clojure output

         {:status 500 :body (format "Failure to provide negotiated media-type and language (type=%s, lang=%s)" media-type language)})
       context))))

(defrecord MapRepresentation [m]
  Representation
  (as-response [this context]
    {:body (render-item m context)}))
