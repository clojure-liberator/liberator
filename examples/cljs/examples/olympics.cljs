(ns examples.olympics
  (:require
   [goog.net.XhrIo :as xhrio]
   [clojure.browser.dom :as dom]
   [goog.dom :as gdom]))

(defn ok [callback]
  (fn [e]
    (let [status (.getStatus (.-target e))
          message (.getStatusText (.-target e))]
      (cond
       (and (>= status 200) (< status 400)) (callback (.. e -target getResponseJson))
       :otherwise (dom/log (str "Failed.\n" status ": " message))))))

(defn get-content [callback]
  (goog.net.XhrIo/send
   (.. js/window -document -URL)
   (ok callback)
   "GET" "" (js-obj "Accept" "application/json")))

(defn ^:export build-index []
  (get-content
   (fn [json]
     (let [parent (dom/get-element "content")
           thead (dom/element [:tr
                               [:th "Name"]
                               [:th "Host city"]
                               [:th "Mascot"]])
           tbody (dom/element [:tbody])
           table (dom/element [:table {:border "2"} [:thead thead] tbody])]
       (gdom/removeChildren parent)
       (doseq [row json]
         (dom/append
          tbody
          (dom/element [:tr
                        [:td [:a {:href (str "." (or (.-id row) ""))} (.-name row)]]
                        [:td (or (.-host_city row) "")]
                        [:td (or (.-mascot row) "")]]))
         (dom/log-obj row))
       (dom/append parent table)))))

(defn ^:export build-instance []
  (get-content
   (fn [json]
     (let [parent (dom/get-element "content")
           div (dom/element [:div])]
       
       (gdom/removeChildren parent)
       
       (dom/append div (dom/element [:h2 (str "Welcome to " (or (.-host_city json) "(unknown)"))]))

       (let [competitions (dom/element [:ul])]
         (dom/append (dom/element [:h3 "Here are this year's events :-"]))
         (doseq [comp (.-competitions json)]
           (dom/append competitions (dom/element [:li comp])))
         (dom/append div competitions))
       
       (dom/append parent div)))))

