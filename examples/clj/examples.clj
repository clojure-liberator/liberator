(ns examples
  (:require [examples.olympics :as olympics]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [liberator.dev :as dev])
  (:use [liberator.core :only [defresource request-method-in]]
        [liberator.representation :only [Representation ring-response]]
        [compojure.core :only [context ANY routes defroutes]]
        [hiccup.page :only [html5]]
        [clojure.string :only [split]]
        [examples.util :only [wrap-binder static clojurescript-resource create-cljs-route]]
        [examples.collection]
        [hiccup.element :only [javascript-tag]]))

;; The classic 'Hello World' example.
(defresource hello-world
  :handle-ok "Hello World!"
  :etag "fixed-etag"
  :available-media-types ["text/plain"])

;; Language negotiation
(defresource hello-george
  :available-media-types ["text/plain" "text/html"] 
  :handle-ok (fn [context] (case (get-in context [:representation :language])
                             "en" "Hello George!"
                             "bg" "Zdravej, Georgi"
                             "Hello!"))
  :available-languages ["en" "bg" "*"])

;; Here's a resource you can POST to.
(def postbox-counter (atom 0))

(defresource postbox
  :method-allowed? (request-method-in :post :get)
  :post! (fn [_] (swap! postbox-counter inc))
  :handle-created (fn [_] (str "Your submission was accepted. The counter is now " @postbox-counter))
  :handle-ok (fn [_] (str "The counter is " @postbox-counter)))

;; Content negotiation examples
(defresource chameleon [mtypes]
  :handle-ok
  (fn [context]
    (case (get-in context [:representation :media-type])
      "text/html" (html5 [:body [:p "OK"]])
      nil
      ))
  :available-media-types mtypes)


;; Olympics
(defresource olympic-games-index
  :available-media-types ["text/html" "application/xhtml+xml;q=0.8" "*/*;q=0.6"]
  :handle-ok (fn [_] (ring-response (olympics/get-olympic-games-index)
                                    {:headers {"Cache-Control" "public,max-age=60s"}})))

;; We define a view that will pull in 
(defrecord OlympicsHtmlPage [main]
  Representation
  (as-response [this context]
    {:body (html5
            [:head
             [:title "Olympics"]
             [:script {:type "text/javascript" :src "/cljs/goog/base.js"}]
             [:script {:type "text/javascript" :src "/cljs/deps.js"}]
             (javascript-tag (format "goog.require('%s');"
                                     (reduce str (interpose "." (butlast (split main #"\."))))))
             (dev/include-trace-css)]
            [:body
             [:h1 "Olympics"]
             [:div#content]
             (dev/include-trace-panel)
             (javascript-tag main)])}))


(defresource olympic-games-index-fancy
  :available-media-types ["text/html" "application/xhtml+xml;q=0.8" "*/*;q=0.6"]
  :handle-ok (fn [context]
               (case (get-in context [:representation :media-type])
                 ("text/html" "application/xhtml+xml")
                 (OlympicsHtmlPage. "examples.olympics.build_index()")
                 (olympics/get-olympic-games-index))))

(defresource olympic-games
  :available-media-types ["text/html" "application/xhtml+xml;q=0.8" "*/*;q=0.6"]
  :handle-ok (fn [context]
               (case (get-in context [:representation :media-type])
                 ("text/html" "application/xhtml+xml")
                 (OlympicsHtmlPage. (str "examples.olympics.build_instance()"))
                 (olympics/get-olympic-games (get-in context [:request ::id])))))

;; Drag drop demo

(def athletes (atom []))

(defrecord DragDropPage [main]
  Representation
  (as-response [this context]
    {:body (html5
            [:head
             [:title "Drag Drop Demo"]
             [:link {:rel "stylesheet" :type "text/css" :media "screen,projection,print" :href "/static/style.css"}]
             [:script {:type "text/javascript" :src "/cljs/goog/base.js"}]
             [:script {:type "text/javascript" :src "/cljs/deps.js"}]
             (javascript-tag (format "goog.require('%s');"
                                     (reduce str (interpose "." (butlast (split main #"\."))))))]
            [:body
             [:h1 "Drag Drop Demo"]
;;             [:div#dropArea {:style "background: white; border: 1px solid black"} "Olympic Event - drag athletes here to add"]
             [:div#content]
            
             (javascript-tag main)])}))

(defresource drag-drop
  :method-allowed? #(some #{(get-in % [:request :request-method])} [:get :post])
  :available-media-types ["text/html" "application/json" "text/plain"]
  :available-charsets ["utf-8"]
  :create! (fn [context] (swap! athletes conj {:name (:name (json/read (io/reader (get-in context [:request :body]))))}))
  :handle-ok (fn [context]
               (case (get-in context [:representation :media-type])
                 ;; If HTML, some presentation.
                 ("text/html" "application/xhtml+xml")
                 (DragDropPage. "examples.dragdrop.build_page()")
                 ;; Otherwise 'just the data please'.
                 @athletes)))

(defresource athletes-resource
  :available-media-types ["text/plain" "application/json"]
  :available-charsets ["utf-8"]
  :handle-ok (olympics/get-athletes-sample))

(defresource index 
  :available-media-types ["text/html"]
  :handle-ok (fn [context]
               (html5 [:head [:title "Liberator examples"]] 
                      [:body
                       [:h1 "Liberator Examples"]
                       [:ul
                        [:li [:a {:href "/hello-world"} "Hello World"]]
                        [:li [:a {:href "/hello-george"} "Hello George (with negotiation)"]]
                        [:li [:a {:href "/olympics/index"} "Olympic Games"]]
                        [:li [:a {:href "/olympics/index-fancy"} "Olympic Games, fancy"]]
                        [:li [:a {:href "/drag-drop"} "Drag and Drop (featuring clojure script)"]]
                        [:li [:a {:href "/drag-drop/athletes"} "Athletes"]]
                        [:li [:a {:href "/x-liberator/requests/"} "Liberator request dump"]]]]))) 

(defn assemble-routes []
  (->
   (routes
    (ANY "/" [] index)
    (create-cljs-route "/cljs")
    (ANY "/hello-world" [] hello-world)
    (ANY "/hello-george" [] hello-george)
    (ANY "/olympics/index" [] olympic-games-index)
    (ANY "/olympics/index-fancy" [] olympic-games-index-fancy)
    (ANY "/drag-drop" [] drag-drop)
    (ANY "/drag-drop/athletes" [] athletes-resource)
    (ANY "/static/*" [] static)
    (ANY ["/olympics/:stem" :stem #"m/.*"] [stem]
         (-> olympic-games
             (wrap-binder ::id (str "/" stem))))
    (ANY ["/collection/:id" :id #".*"] [id] (entry-resource id))
    (ANY "/collection" [] list-resource))

   
   (dev/wrap-trace :ui :header)))


