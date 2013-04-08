(ns liberator.dev
  (:use hiccup.core
        hiccup.page
        compojure.core
        clojure.tools.trace
        [liberator.core :only [defresource]])
  (:require [liberator.core :as core]
            [clojure.string :as string]
            [clojure.data.json :as json])
  (:import java.util.Date))

(def mount-url "/x-liberator/requests/")

(defonce logs (atom nil))

(defn next-id [] (apply str (take 5 (repeatedly
                                     #(rand-nth "abcdefghijklmnopqrstuvwzxy0123456789")))))

(def log-size 100)

(defn save-log! [id msg]
  (swap! logs #(->> (conj % [id msg])
                    (take log-size))))

(defn- with-slash [s] (if (.endsWith s "/") s (str s "/")))

(def ^:dynamic *current-id* nil)

(defn seconds-ago [d]
  (int  (/ (- ( System/currentTimeMillis) (.getTime d)) 1000)))

(defn log-by-id [id]
  (first (filter (fn [[aid _]] (= id aid)) @logs)))

(defresource log-handler [id]
  :available-media-types ["text/html" "application/json"]
  :exists? (fn [ctx] (assoc ctx ::log (log-by-id id)))
  :handle-ok
  (fn [{[_ [d r log]] ::log {media-type :media-type} :representation}]
    (condp = media-type
      "text/html"
      (html5
       [:head
        [:title "Liberator Request Trace #" id " at " d]]
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
        [:ol (map (fn [[l r]] [:li (h l) ": " (h (string/join " " r))]) log)]
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
  bottom:0;
  right:0;
  
  margin-bottom: 1em;
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

(defn wrap-trace [handler]
  (fn [request]
    (let [request-log (atom [])]
      (binding [*current-id* (next-id)]
        (core/with-logger (core/atom-logger request-log)
          (let [resp (handler request)]
            (if-not (empty? @request-log)
              (do
                (save-log! *current-id*
                           [(Date.)
                            (select-keys request [:request-method :uri :headers])
                            @request-log])
                (assoc-in resp [:headers "X-Liberator-Trace-Id"] *current-id*))
              resp)))))))

(defn wrap-trace-ui [handler]
  (let [base-url (with-slash mount-url)]
    (routes
     (ANY (str base-url "styles.css") [] styles)
     (ANY [(str base-url ":id") :id #".+"] [id] #((log-handler id) %))
     (ANY [(str base-url ":id")  :id #""] [id] list-handler)
     (ANY base-url [] list-handler)
     (fn [req]
       (let [resp (handler req)]
         (if-let [id (get-in resp [:headers "X-Liberator-Trace-Id"])]
           (update-in resp [:headers "Link"]
                      #(str % (str "\n</" (trace-url id) ">"
                                   "; rel=x-liberator-trace")))
           resp))))))

(defn wrap-trace-header [handler]
  (fn [req]
    (let [resp (handler req)] ()
      (if-let [id (get-in resp [:headers "X-Liberator-Trace-Id"])]
        (let [[_ [_ _ l]] (log-by-id id)]
          (assoc-in resp [:headers "X-Liberator-Trace"]
                    (map #(clojure.string/join " " %) l)))
        resp))))
