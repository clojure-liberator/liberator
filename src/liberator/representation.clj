;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns liberator.representation
  (:require
   [clojure.data.json :as json]
   [clojure.data.csv :as csv])
  (:use
   [hiccup.core :only [html]]
   [hiccup.page :only [html5 xhtml]]
   [clojure.tools.logging :only [logf]]))

;; This namespace provides default 'out-of-the-box' web representations
;; for many IANA mime-types.

(defmacro ->when [form pred & term]
  `(if ~pred (-> ~form ~@term) ~form))

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

(defmulti render-map-generic "dispatch on media type"
  (fn [data context] (get-in context [:representation :media-type])))

(defmethod render-map-generic "text/plain"
  [data {:keys [dictionary language] :or {dictionary default-dictionary} :as context}]
  (->> data
       (map (fn [[k v]] (str (dictionary k language) "=" v)))
       (interpose "\r\n")
       (apply str)))

(defn- render-map-csv [data sep]
  (with-out-str
    (csv/write-csv *out* [["name" "value"]] :newline :cr+lf :separator sep)
    (csv/write-csv *out* (seq data) :newline :cr+lf :separator sep)))

(defmethod render-map-generic "text/csv" [data context]
  (render-map-csv \, data))

(defmethod render-map-generic "text/tab-separated-values" [data context]
  (render-map-csv \, 9))

(defmethod render-map-generic "application/json" [data context]
  (with-out-str (json/print-json data)))

(defmethod render-map-generic "application/clojure" [data context]
  (with-out-str (pr data)))

(defn- render-map-html-table
  [data
   {{:keys [media-type language] :as representation} :representation
    :keys [dictionary fields] :or {dictionary default-dictionary}
    :as context} mode]
  (let [content
        [:div [:table 

               [:tbody (for [[key value] data]
                         [:tr
                          [:th (or (dictionary key language) (default-dictionary key language))]
                          [:td value]])]]]]
    (condp = mode
      :html  (html content)
      :xhtml (xhtml content))))


(defmethod render-map-generic "text/html" [data context]
  (render-map-html-table data context :html))

(defmethod  render-map-generic "application/xhtml+xml" [data context]
  (render-map-html-table data context :html))


(defmulti render-seq-generic (fn [data context] (get-in context [:representation :media-type])))

(defn- render-seq-html-table
  [data
   {{:keys [media-type language] :as representation} :representation
    :keys [dictionary fields] :or {dictionary default-dictionary
                                   fields (keys (first data))}
    :as context} mode]
  {:body
   (let [content (html-table data fields language dictionary)]
     (condp = mode
       :html  (html content)
       :xhtml (xhtml content)))})


(defmethod render-seq-generic "text/html" [data context]
  (render-seq-html-table data context :html))

(defmethod  render-seq-generic "application/xhtml+xml" [data context]
  (render-seq-html-table data context :html))

(defmethod render-seq-generic "application/json" [data _]
  {:body
   (with-out-str
     (json/print-json data)
     (print "\r\n"))})

(defn render-seq-csv
  [data
   {{:keys [language] :as representation} :representation
    :keys [dictionary fields] :or {dictionary default-dictionary
                                   fields (keys (first data))}
    :as context} sep]
  {:body
   (with-out-str
     (csv/write-csv *out* [(map #(or (dictionary % language)
                                     (default-dictionary % language)) fields)]
                    :newline :cr+lf :separator sep)
     (csv/write-csv *out* (map (apply juxt (map (fn [x] (fn [m] (get m x))) fields)) data)
                    :newline :cr+lf :separator sep))})

(defmethod render-seq-generic "text/csv" [data context]
   (render-seq-csv data context \,))

(defmethod render-seq-generic "text/tab-separated-values" [data context]
  (render-seq-csv data context "\t"))

(defmethod render-seq-generic "application/clojure"
  [data
   {{:keys [language] :as representation} :representation
    :keys [dictionary fields] :or {dictionary default-dictionary
                                   fields (keys (first data))}
    :as context}]
  (->>
   data
   (map #(render-item % (assoc context
                          :dictionary dictionary
                          :fields fields)))
   (interpose "\r\n")
   (apply str)
   (hash-map :body)))

(defmethod render-seq-generic :default
   [data {{:keys [language media-type] :as representation} :representation :as context}]
   {:status 500 :body
    (format "Failure to provide negotiated media-type and language (type=%s, lang=%s)" media-type language)})


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

  (render-item [data context]
    (render-map-generic data context))

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
  (as-response [data context]
    (render-seq-generic data context)))

(defrecord MapRepresentation [m]
  Representation
  (as-response [this context]
    {:body (render-item m context)}))
