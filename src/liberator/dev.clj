(ns liberator.dev
  (:use hiccup.core
        hiccup.page
        [liberator.core :only [defresource]])
  (:require [liberator.core :as core]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [ring.util.response :as response]
            [clojure.string :as s])
  (:import java.util.Date))

(def mount-url "/x-liberator/requests/")

(defonce logs (atom nil))

(defn next-id [] (apply str (take 5 (repeatedly
                                     #(rand-nth "abcdefghijklmnopqrstuvwzxy0123456789")))))

(def log-size 100)

(defn save-log! [id msg]
  (swap! logs #(->> (conj % [id msg])
                    (take log-size)
                    (doall))))

(defn- with-slash [^String s] (if (.endsWith s "/") s (str s "/")))

(def ^:dynamic *current-id* nil)

(defn seconds-ago [^Date d]
  (int  (/ (- ( System/currentTimeMillis) (.getTime d)) 1000)))

(defn log-by-id [id]
  (first (filter (fn [[aid _]] (= id aid)) @logs)))

;; see graph/clean-id, unitfy
(defn- clean-id [str]
  (clojure.string/replace (or str "") #"[^a-zA-Z0-9_]+" ""))

(defn result->bool [r]
  (if (vector? r) (first r)
      r))

(defn hl-result [r]
  (if (result->bool r)
    "hl-true"
    "hl-false"))

(defresource log-handler [id]
  :available-media-types ["text/html" "application/json"]
  :exists? (fn [ctx] (if-let [l (log-by-id id)] (assoc ctx ::log l)))
  :handle-ok
  (fn [{[_ [d r log]] ::log {media-type :media-type} :representation}]
    (condp = media-type
      "text/html"
      (html5
       [:head
        [:title "Liberator Request Trace #" id " at " d]]
       [:script
        (s/join "\n"
              [""
               "function insertStyle() {"
               "var svg = document.getElementById(\"trace\").contentDocument;\n"
               "var style = svg.createElementNS(\"http://www.w3.org/2000/svg\",\"style\"); "
               (str  "style.textContent = '"
                     (clojure.string/replace
                      (slurp (clojure.java.io/resource "liberator/trace.css"))
                      #"[\r\n]" " ") "'; ")
               "var root = svg.getElementsByTagName(\"svg\")[0];"
               "root.appendChild(style);  "
               "root.setAttribute(\"width\", \"100%\"); root.setAttribute(\"height\", \"100%\"); "
               (s/join "\n"
                     (map (fn [[l [n r]]]
                            (format
                             "svg.getElementById(\"%s\").setAttribute(\"class\", svg.getElementById(\"%s\").getAttribute(\"class\") + \" %s\"); " (clean-id n) (clean-id n) (hl-result r))) log))

               (s/join "\n"
                     (map (fn [[[l1 [n1 r1]] [lr2 [n2 r2]]]]
                             (let [id (format "%s_%s" (clean-id n1) (clean-id n2))]
                               (format
                                "svg.getElementById(\"%s\").setAttribute(\"class\", svg.getElementById(\"%s\").getAttribute(\"class\") + \" %s\");" id id (if (result->bool r1) "hl-true" "hl-false"))))
                          (map vector log (rest log))))
               
               "};"
               "setTimeout(function(){insertStyle()}, 500);"
               "setTimeout(function(){insertStyle()}, 1000);"
               "setTimeout(function(){insertStyle()}, 5000);"
               
               ""])]
       [:body
        [:a {:href mount-url} "List of all traces"]
        [:h1 "Liberator Request Trace #" id " at " d " (" (seconds-ago d) "s ago)"]
        [:h2 "Request was &quot;" [:span {:style "text-transform: uppercase"}
                                   (:request-method r)] " " [:span (:uri r)] "&quot;"]
        [:h3 "Parameters"]
        [:dl (mapcat (fn [[k v]] [[:dt (h k)] [:dd (h v)]]) (:params r))]
        [:h3 "Headers"]
        [:dl (mapcat (fn [[k v]] [[:dt (h k)] [:dd (h v)]]) (:headers r))]
        [:h3 "Trace"]
        [:ol (map (fn [[l [n r]]] [:li (h l) ": " (h n) " "
                                  (if (nil? r) [:em "nil"] (h (pr-str r)))]) log)]
        [:div {:style "text-align: center;"}
         [:object {:id "trace" :data (str mount-url "trace.svg") :width "90%"
                   :style "border: 1px solid #666;"}]]

        
        [:h3 "Full Request"]
        [:pre [:tt (h (with-out-str (clojure.pprint/pprint r)))]]])
      "application/json"
      (with-out-str
        (json/write {:date (str d)
                     :request {:method (:request-method r)
                               :uri (:uri r)
                               :parameters (:params r)
                               :headers (:headers r)}
                     :trace log} *out*))))

  :handle-not-found
  (fn [ctx]
    (html5 [:head [:title "Liberator Request Trace #" id " not found."]]
           [:body [:h1 "Liberator Request Trace #" id " not found."]
            [:p "The requested trace was not found. Maybe it is expired."]
            [:p "You can access a " [:a {:href mount-url} "list of traces"] "."]])))

(defresource list-handler
  :available-media-types ["text/html"]
  :handle-ok (fn [_] 
               (html5
                [:head
                 [:title "Liberator Request Traces"]]
                [:body
                 [:h1 "Liberator Request Traces"]
                 (if (empty? @logs)
                   [:div
                    [:p "No request traces have been recorded, yet."]
                    [:p "wrap your handler with " [:code "wrap-trace-ui"] " to enable logging."
                     "The link to the log will be available as a " [:code "Link"]
                     " header in the http response."]]
                   [:ol (map (fn [[id [d {:keys [request-method uri]} log]]]
                               [:ul
                                [:a {:href (h (str (with-slash mount-url) id))}
                                 [:span (h request-method)] " " [:span (h uri)]]
                                [:span " at " [:span (h d)] " " [:span "(" (seconds-ago d) "s ago)"]]]) @logs)])])))

(defn css-url [] (str (with-slash mount-url) "styles.css"))

(defn include-trace-css []
  (include-css (css-url)))

(defn trace-url
  "Build the url under which the trace information can be found for the
   given trace id"
  [id]
  (str (with-slash mount-url) id))

(defn current-trace-url
  "Return the url under with the trace of the current request can be accessed"
  []
  (trace-url *current-id*))

(defn include-trace-panel
  "Create a html snippet with a link to the current requests' trace"
  []
  (html
   [:div {:id "x-liberator-trace"}
    [:a {:href (current-trace-url)} (str  "Liberator Request Trace #" *current-id*)]]))

(defresource styles
  :available-media-types ["text/css"]
  :handle-ok "#x-liberator-trace {
  display:block;

  position:absolute;
  top:0;
  right:0;

  margin-top: 1em;
  margin-right: 1em;
  padding: 0 1em;
  color: #333;
  background-color: #f0f0f0;
  font-size: 12px;
  border: 1px solid #999;
  border-radius: .3em;
  text-align: center;
}"
  :etag "1")

(def trace-id-header "X-Liberator-Trace-Id")

(def trace-svg (clojure.java.io/resource "liberator/trace.svg"))

(defn- handle-and-add-trace-link [handler req]
  (let [resp (handler req)]
    (if-let [id (get-in resp [:headers trace-id-header])]
      (update-in resp [:headers "Link"]
                 #(if %1 [%1 %2] %2)
                 (format "</%s>; rel=x-liberator-trace" (trace-url id)))
      resp)))

(defn- wrap-trace-ui [handler]
  (let [base-url (with-slash mount-url)]
    (fn [req]
      (if (.startsWith (:uri req) base-url)
        (let [subpath (s/replace (:uri req) base-url "")]
          (case subpath
            "trace.svg" (response/content-type (response/url-response  trace-svg) "image/svg+xml")
            "styles.css" (styles req)
            "" (list-handler req)
            ((log-handler subpath) req)))

        (handle-and-add-trace-link handler req)))))

(defn- wrap-trace-header [handler]
  (fn [req]
    (let [resp (handler req)]
      (if-let [id (get-in resp [:headers trace-id-header])]
        (let [[_ [_ _ l]] (log-by-id id)]
          (assoc-in resp [:headers "X-Liberator-Trace"]
                    (map #(s/join " " %) l)))
        resp))))

(defn- cond-wrap [fn expr wrapper]
  (if expr (wrapper fn) fn))

(defn wrap-trace
  "Wraps a ring handler such that a request trace is generated.

   Supported options:

   :ui     - Include link to a resource that dumps the current request
   :header - Include full trace in response header"
  [handler & opts]
  (->
   (fn [request]
     (let [request-log (atom [])]
       (binding [*current-id* (next-id)]
         (core/with-logger (core/atom-logger request-log)
           (let [resp (handler request)]
             (if-not (empty? @request-log)
               (do
                 (save-log! *current-id*
                            [(Date.)
                             (select-keys request [:request-method :uri :headers :params])
                             @request-log])
                 (assoc-in resp [:headers trace-id-header] *current-id*))
               resp))))))
   (cond-wrap (some #{:ui} opts) wrap-trace-ui)
   (cond-wrap (some #{:header} opts) wrap-trace-header)))
