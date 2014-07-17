(ns things-bench
  (:require [liberator.core :refer (defresource request-method-in)]
            [liberator.representation :refer (ring-response)]
            [perforate.core :refer :all]
            [ring.mock.request :refer (request header)])
  (:import [java.security MessageDigest]))

(defresource thing-resource
  [things]
  ;; early lookup   
  :service-available? (fn [ctx] {::r (get @things (get-in ctx [:request :uri]))})
  :method-allowed? (request-method-in :get :put :delete)
  ;; lookup media types of the requested resource
  :available-media-types #(if-let [m (get-in % [::r :media-type])] [m])
  ;; the resource exists if a value is stored in @things at the uri
  ;; store the looked up value at key ::r in the context
  :exists? #(get % ::r)
  ;; ...it existed if the stored value is nil (and not some random
  ;; Objeced we use as a setinel)
  :existed? #(nil? (get @things (get-in % [:request :uri]) (Object.)))
  ;; use the previously stored value at ::r
  :handle-ok #(get-in % [::r :content])
  ;; update the representation
  :put! #(dosync
          (alter things assoc-in
                 [(get-in % [:request :uri])]
                 {:content (get-in % [:request :body])
                  :media-type (get-in % [:request :headers "content-type"]
                                      "application/octet-stream")
                  :last-modified (java.util.Date.)}))
  ;; ...store a nil value to marke the resource as gone
  :delete! #(dosync (alter things assoc (get-in % [:request :uri]) nil))
  :last-modified #(get-in % [::r :last-modified]))

(defgoal things-bench "'Things' Resource benchmarks")

(defcase things-bench "Things"
  []
  (let [t-r (thing-resource (ref nil))]
    (let [resp (t-r (request :get "/r1"))]
      (-> resp :status (= 404) assert))
    (let [resp (t-r (-> (request :put "/r1")
                                   (assoc :body "r1")
                                   (header "content-type" "text/plain")))]
      (-> resp :status (= 201) assert))
    (let [resp (t-r (-> (request :get "/r1")))]
      (-> resp :status (= 200) assert)
      (-> resp :body (= "r1") assert)
      (-> resp (get-in [:headers "Content-Type"])
          (= "text/plain;charset=UTF-8")
          assert))
    (let [resp (t-r (-> (request :delete "/r1")))]
      (-> resp :status (= 204) assert)
      (-> resp :body nil? assert))
    (let [resp (t-r (request :get "/r1"))]
      (-> resp :status (= 410) assert))))
