(ns liberator.dev
  (:use liberator.core
        hiccup.core
        hiccup.page
        compojure.core
        clojure.tools.trace)
  (:import java.util.Date))

(def mount-url "/x-liberator/requests/")

(defonce logs (atom nil))

(defn next-id [] (apply str (take 5 (repeatedly #(rand-nth "abcdefghijklmnopqrstuvwzxy0123456789")))))

(def log-size 50)

(defn pushlog! [id log]
  (swap! logs #(->> (conj % [id log])
                    (take log-size))))

(defn make-logger [atom]
  (fn [msg] 
    (swap! atom conj msg)))

(defn- with-slash [s] (if (.endsWith s "/") s (str s "/")))

(def ^:dynamic *current-id* nil)

(declare wrap-trace-liblob)

(defn date-ago [d]
  (int  (/ (- ( System/currentTimeMillis) (.getTime d)) 1000)))

(defresource log-handler [id]
  :available-media-types ["text/html"]
  :exists? (fn [ctx] (if-let [l (first (filter (fn [[aid _]] (= id aid)) @logs))]
                       (trace "CTX" (assoc ctx ::log l))))
  :handle-ok (fn [{[_ [d r log]] ::log}]
               (html5
                [:head
                 [:title "Liberator Request Trace #" id " at " d]]
                [:body
                 [:a {:href mount-url} "List of all traces"]
                 [:h1 "Liberator Request Trace #" id " at " d " (" (date-ago d) "s ago)"]
                 [:p [:span (:request-method r)] " " [:span (:uri r)]]
                 [:ol (map (fn [l] [:li (h l)]) @log)]]))
  :handle-not-found (fn [ctx]
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
                    [:p "wrap your handler with " [:code (-> #'wrap-trace-liblob meta :name)] " to enable logging."
                     "The link to the log will be available as a " [:code "Link"] " header in the http response."]]
                   [:ol (map (fn [[id [d {:keys [request-method uri]} log]]]
                               [:ul
                                [:a {:href (h (str (with-slash mount-url) id))}
                                 [:span (h request-method)] " " [:span (h uri)]]
                                [:span " at " [:span (h d)] " " [:span "(" (date-ago d) "s ago)"]]]) @logs)])])))

(defn css-url []  (str (with-slash mount-url) "styles.css"))

(defn include-trace-css []
  (include-css (css-url)))

(defn current-trace-url []
  (str (with-slash mount-url) *current-id*))

(defn include-liberator-trace []
  (html
   [:div {:id "x-liberator-trace"}
    [:a {:href (current-trace-url)} (str  "Liberator Request Trace #" *current-id*)]]))

(defresource styles
  :available-media-types ["text/css"]
  :handle-ok"#x-liberator-trace {
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
  )

(defn wrap-trace [handler]
  (let [base-url (with-slash mount-url)]
    (routes
     (ANY (str base-url "styles.css") [] styles)
     (ANY [(str base-url ":id") :id #".+"] [id] #(log-handler % id))
     (ANY [(str base-url ":id")  :id #""] [id] list-handler)
     (ANY base-url [] list-handler)
     (fn [request]
       (let [log (atom [])] 
         (binding [*-logger* (make-logger log)
                   *current-id* (next-id)]
           (let [resp (handler request)]
             (if-not (empty? @log) 
               (do
                 (pushlog! *current-id* [(Date.) (select-keys request [:request-method :uri]) log])
                 (-> resp (update-in [:headers "Link"]
                                     #(str % (str "\n</" base-url *current-id* ">"
                                                  "; rel=x-liberator-trace")))))
               resp))))))))