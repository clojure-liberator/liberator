(ns examples.dragdrop
  (:require
   [goog.net.XhrIo :as xhrio]
   [clojure.browser.dom :as dom]
   [goog.dom :as gdom]
   [goog.fx.DragDropGroup :as ddg]
   [goog.fx.DragDrop :as dd]
   [goog.events :as gevents]
   [goog.json :as gjson]
   ))

;; From Mark McGranaghan, http://mmcgrana.github.com/2011/09/clojurescript-nodejs.html
(defn clj->js
  "Recursively transforms ClojureScript maps into Javascript objects,
   other ClojureScript colls into JavaScript arrays, and ClojureScript
   keywords into JavaScript strings."
  [x]
  (cond
    (string? x) x
    (keyword? x) (name x)
    (map? x) (.-strobj (reduce (fn [m [k v]]
               (assoc m (clj->js k) (clj->js v))) {} x))
    (coll? x) (apply array (map clj->js x))
    :else x))


(defn add-athletes [json]
  (dom/log "add athletes: ")
  (dom/log-obj json)
  (let [parent (dom/get-element "data123")]
    (gdom/removeChildren parent)
    (doseq [ath json]
      (dom/append parent (dom/element [:li (.-name ath)]))
      )))

(defn load-athletes []
  (goog.net.XhrIo/send
   (.. js/window -document -URL)
   (fn [e]
    (let [status (.getStatus (.-target e))
          message (.getStatusText (.-target e))]
      (cond
       (and (>= status 200) (< status 400)) (add-athletes (.. e -target getResponseJson))
       :otherwise (dom/log (str "Failed.\n" status ": " message)))))
   "GET" "" (js-obj "Accept" "application/json")))

(defn dragOver [ev]
  (set! (.. ev -dropTargetItem -element -style -background) "yellow"))

(defn dragOut [ev]
  (set! (.. ev -dropTargetItem -element -style -background) "silver"))

(defn dropList [ev]
  (dom/log "drop"))

(defn dragList [ev]
  (dom/log "drag"))

(defn dropIt [ev]
  (set! (.. ev -dropTargetItem -element -style -background) "silver")
  ;; TODO Do the POST
  (dom/log "DROP!!!")
  (dom/log (str "DATA>" (gjson/serialize (clj->js {:name (.. ev -dragSourceItem -data)}))))
  (goog.net.XhrIo/send
   (.. js/window -document -URL)
   (fn [ev] (dom/log "POST returned"))
   "POST"
   
   (gjson/serialize (clj->js {:name (.. ev -dragSourceItem -data)}))
   (js-obj "Accept" "application/json"
           "Content-Type" "application/json"))
  (dom/log "DROP done!!!")
  (load-athletes))

(defn ^:export build-page []

  (let [content (dom/get-element "content")]
    (dom/append content (dom/element [:div {:id "dropArea"
                                            :style "float:left; width: 300px; min-height: 200px; background: white; border: 1px solid black"} "Olympic Event"
                                      [:ul {:id "data123"}]]))
    (dom/append content (dom/element [:ul {:id "athletes"
                                           :style "margin-left: 400px"}])))
  
  
  (let [athletes (dom/get-element "athletes")
        list1 (goog.fx.DragDropGroup.)
        dropArea (goog.fx.DragDrop. "dropArea")]
    (gdom/removeChildren athletes)

    (doseq [name ["Daniel Frank"
                   "Auguste Cavadini"
                   "Robert McKnight"
                   "Jessie Pollock"
                   "Stanisława Walasiewicz"
                   "Andrea Henkel"
                   "RaymondLenroy Bonney"
                   "Herv Flandin"
                   "John Allan Peterson"
                   "Jari Torkki"
                   ]]
      
      (let [athlete (dom/element [:li name])]
        (dom/append athletes athlete)
        (.addItem list1 athlete (.. athlete -firstChild -nodeValue))))
    
    (.addTarget list1 dropArea)
    (.setSourceClass list1 "source")
    (.setTargetClass dropArea "target")
    (.init list1)
    (.init dropArea)
    (gevents/listen list1 "dragover" dragOver)
    (gevents/listen list1 "dragout" dragOut)
    (gevents/listen list1 "drop" dropList)
    (gevents/listen list1 "drag" dragList)
    (gevents/listen dropArea "drop" dropIt))

  (load-athletes)
  (dom/log "SUCCESS!"))

