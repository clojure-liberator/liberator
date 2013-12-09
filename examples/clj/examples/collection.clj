(ns examples.collection
  (:require [liberator.core :refer (defresource by-method)]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [liberator.dev :refer (wrap-trace)])
  (:import java.net.URL))

(defonce entries (ref {}))

(defn build-entry-url [request id]
  (println "R" request)
  (URL. (format "%s://%s:%s%s/%s"
                (name (:scheme request))
                (:server-name request)
                (:server-port request)
                (:uri request)
                (str id))))

(defn body-as-string [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

(defn parse-json [context key]
  (when (#{:put :post} (get-in context [:request :request-method]))
    (try
      (if-let [body (body-as-string context)]
        (let [data (json/read-str body)]
          [false {key data}])
        {:message "No body"})
      (catch Exception e
        (.printStackTrace e)
        {:message (format "IOException: %s" (.getMessage e))}))))

(defn check-content-type [ctx content-types]
  (if (#{:put :post} (get-in ctx [:request :request-method]))
    (or 
     (some #{(get-in ctx [:request :headers "content-type"])}
           content-types)
     [false {:message "Unsupported Content-Type"}])
    true))

(defresource list-resource
  :available-media-types ["application/json"]
  :allowed-methods [:get :post]
  :known-content-type? #(check-content-type % ["application/json"])
  :malformed? #(parse-json % ::data)
  :post! #(let [id (str (inc (rand-int 100000)))]
            (dosync (alter entries assoc id (::data %)))
            {::id id})
  :post-redirect? (fn [ctx] {:location (build-entry-url (get ctx :request)
                                                       (get ctx ::id))})
  :handle-ok #(map (fn [id] (str (build-entry-url (get % :request) id)))
                   (keys @entries)))

(defresource entry-resource [id]
  :allowed-methods [:get :put :delete]
  :known-content-type? #(check-content-type % ["application/json"])
  :exists? (fn [_]
             (prn "ID" id)
             (let [e (get @entries id)]
                    (if-not (nil? e)
                      {::entry e})))
  :existed? (fn [_] (nil? (get @entries id ::sentinel)))
  :available-media-types ["application/json"]
  :handle-ok ::entry
  :delete! (fn [_] (dosync (alter entries assoc id nil)))
  :malformed? #(parse-json % ::data)
  :can-put-to-missing? false
  :put! #(dosync (alter entries assoc id (::data %)))
  :new? (fn [_] (nil? (get @entries id ::sentinel))))
