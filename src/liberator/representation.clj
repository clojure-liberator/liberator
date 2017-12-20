(ns liberator.representation
  (:require
   [clojure.data.json :as json]
   [clojure.data.csv :as csv]
   [clojure.string :refer [split trim]]
   [liberator.util :as util]
   [hiccup.core :refer [html]]
   [hiccup.page :refer [html5 xhtml]]))

;; This namespace provides default 'out-of-the-box' web representations
;; for many IANA mime-types.

(defmacro ->when [form pred & term]
  `(if ~pred (-> ~form ~@term) ~form))

(defprotocol Representation
  (as-response [_ {representation :representation :as context}]
    "Coerce to a standard Ring response (a map
    containing :status, :headers and :body). Developers can call
    as-response directly, usually when they need to augment the context. It
    does all the charset conversion and encoding and returns are Ring
    response map so no further post-processing of the response will be
    carried out."))

(defn default-dictionary [k lang]
  (if (instance? clojure.lang.Named k)
    (name k)
    (str k)))

(defn html-table [data fields lang dictionary]
  [:div [:table
         [:thead
          [:tr
           (for [field fields] [:th (or (dictionary field lang)
                                        (default-dictionary field lang))])]]
         [:tbody (for [row data]
                   [:tr
                    (for [field fields]
                      [:td (if-let [s (get row field)]
                             (if (re-matches #"https?://.*" (str s))
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
  (render-map-csv data \,))

(defmethod render-map-generic "text/tab-separated-values" [data context]
  (render-map-csv data \tab))

(defmethod render-map-generic "application/json" [data context]
  (json/write-str data))

(defn render-as-clojure [data]
  (binding [*print-dup* true]
    (pr-str data)))

(defn render-as-edn [data]
  (pr-str data))

(defmethod render-map-generic "application/clojure" [data context]
  (render-as-clojure data))

(defmethod render-map-generic "application/edn" [data context]
  (render-as-edn data))

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

(defn render-seq-html-table
  [data
   {{:keys [media-type language] :as representation} :representation
    :keys [dictionary fields] :or {dictionary default-dictionary
                                   fields (keys (first data))}
    :as context} mode]
  (let [content (html-table data fields language dictionary)]
    (condp = mode
      :html  (html content)
      :xhtml (xhtml content))))


(defmethod render-seq-generic "text/html" [data context]
  (render-seq-html-table data context :html))

(defmethod  render-seq-generic "application/xhtml+xml" [data context]
  (render-seq-html-table data context :html))

(defmethod render-seq-generic "application/json" [data _]
  (json/write-str data))

(defmethod render-seq-generic "application/clojure" [data _]
  (render-as-clojure data))

(defmethod render-seq-generic "application/edn" [data _]
  (render-as-edn data))

(defn render-seq-csv
  [data
   {{:keys [language] :as representation} :representation
    :keys [dictionary fields] :or {dictionary default-dictionary
                                   fields (keys (first data))}
    :as context} sep]
  (let [sw (java.io.StringWriter.)]
    (csv/write-csv sw [(map #(or (dictionary % language)
                                 (default-dictionary % language)) fields)]
                   :newline :cr+lf :separator sep)
    (csv/write-csv sw (map (apply juxt (map (fn [x] (fn [m] (get m x))) fields)) data)
                   :newline :cr+lf :separator sep)
    (str sw)))

(defmethod render-seq-generic "text/csv" [data context]
   (render-seq-csv data context \,))

(defmethod render-seq-generic "text/tab-separated-values" [data context]
  (render-seq-csv data context \tab))

(defmethod render-seq-generic "text/plain" [data context]
  (clojure.string/join "\r\n\r\n"
                       (map #(render-map-generic % context)
                            data)))

(defmulti render-item (fn [m media-type] (type m)))

(defmethod render-item clojure.lang.Associative [m media-type]
  (render-map-generic m media-type))

(defmethod render-item clojure.lang.Seqable [m media-type]
  (render-seq-generic m media-type))

(defmethod render-seq-generic :default
  [data {{:keys [language media-type] :as representation} :representation :as context}]
  (if media-type
    {:status 500 :body
     (format "Cannot render sequential data as %s (language: %s)" media-type language)}
    (render-seq-generic data (assoc-in context [:representation :media-type]
                                       "application/json"))))

(defn in-charset [^String string ^String charset]
  (if (and charset (not (.equalsIgnoreCase charset "UTF-8")))
    (java.io.ByteArrayInputStream.
     (.getBytes string (java.nio.charset.Charset/forName charset)))

    ;; "If no Accept-Charset header is present, the default is that
    ;; any character set is acceptable." (p101). In the case of Strings, it is unnecessary to convert to a byte stream now, and doing so might even make things harder for test-suites, so we just return the string.
    string))



;; Representation embodies all the rules as to who should encode the content.
;; The aim is to do more for developer's who don't want to, while seeding control for developers who need it.
;;
;; Representation is a lot like compojure.response.Renderable, but it has to deal with automatic rendering of common Clojure datatypes, charset conversion and encoding.
;;
;; TODO This needs to be extended by NIO classes: CharBuffer, ByteBuffer, exploiting CharSetEncoder, etc..
(extend-protocol Representation

  nil
  (as-response [this _] nil) ; accept defaults

  clojure.lang.Sequential
  (as-response [data context]
    (as-response (render-seq-generic data context) context))

  clojure.lang.MapEquivalence
  (as-response [this context]
    (as-response (render-map-generic this context) context))

  ;; If a string is returned, we should carry out the conversion of both the charset and the encoding.
  String
  (as-response [this {representation :representation}]
    (let [charset (get representation :charset "UTF-8")]
      {:body
       (in-charset this charset)
       :headers {"Content-Type" (format "%s;charset=%s" (get representation :media-type "text/plain") charset)}}))

  ;; If an input-stream is returned, we have no way of telling whether it's been encoded properly (charset and encoding), so we have to assume it is, given that we told the developer what representation was negotiated.
  java.io.File
  (as-response [this _] {:body this})

  ;; We assume the input stream is already in the requested
  ;; charset. Decoding and encoding an existing charset unnecessarily
  ;; would be expensive.
  java.io.InputStream
  (as-response [this {representation :representation}]
    (let [charset (get representation :charset "UTF-8")]
      {:body this
       :headers {"Content-Type" (format "%s;charset=%s" (get representation :media-type "text/plain") charset)}})))

;; define a wrapper to tell a generic Map from a Ring response map
;; and to return a ring response as the representation
(defrecord RingResponse [ring-response value]
  Representation
  (as-response [_ context]
    (let [base (when value (as-response value context))]
      (util/combine base ring-response))))

(defn ring-response
  "Returns the given map as a ring response. The map is not converted
  with `as-response`.

  An optional representation value will be converted to a ring-response
  using `as-response` as usual  and the ring-response parameter will be
  merged over it.

  The merge is done with `liberator.core/combine` and thus merges
  recursively.

  Example:

  A handler returns

    (ring-response {:foo :bar}
                   {:status 999
                    :headers {\"X-Custom\" \"value\"})

  The final response will have the overriden status code 999 and a
  custom header set. Assuming the negotiated content type was
  application/json the response will be

    {:headers {\"Content-Type\" \"application/json\"
               \"X-Custom\" \"value\"}
     :status 999
     :body \"{'foo': 'bar'}\"} "
  ([ring-response-map] (ring-response nil ring-response-map))
  ([value ring-response-map] (->RingResponse ring-response-map value)))

(defn- content-type [ctx]
  (get-in ctx [:request :headers "content-type"]))

(defn- encoding [ctx]
  (or
   (second (flatten (re-seq #"charset=([^;]+)" (content-type ctx))))
   "ISO-8859-1"))

(defmulti parse-request-entity
  (fn [ctx]
    (when-let [media-type (content-type ctx)]
      (-> media-type
          (split #"\s*;\s*")
          first))))

(defmethod parse-request-entity "application/json" [ctx]
  (if-let [body (:body (:request ctx))]
    {:request-entity (json/read-str (slurp body :encoding (encoding ctx)) :key-fn keyword)}
    true))

(defmethod parse-request-entity :default [ctx]
  (if-let [body (:body (:request ctx))]
    {:request-entity body}
    true))

(defn parsable-content-type?
  "Tells if the request has a content-type that can be parsed by
  the default implementation for :processable?"
  [ctx]
  (contains? (methods parse-request-entity) (content-type ctx)))
