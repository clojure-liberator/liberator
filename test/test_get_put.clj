(ns test-get-put
  (:use liberator.core
        midje.sweet
        checkers
        [ring.mock.request :only [request header]]))

;; tests for a simple resource that can be accessed on a given uri

(defn make-thing-resource [things]
  (resource 
   :method-allowed? (request-method-in :get :put :delete)
   :exists?  #(not (nil? (get @things (get-in % [:request :uri]))))
   :existed? #(nil? (get @things (get-in % [:request :uri])))
   :handle-ok #(get-in @things [(get-in % [:request :rui]) :content])
   :x-known-content-type? #(= "text/plain" (get-in % [:request :headers "content-type"]))
   :put! #(dosync (alter things assoc-in [(get-in % [:request :uri]) :content] (get-in % [:request :body])))
   :delete! #(dosync (alter things assoc-in [(get-in % [:request :uri]) :content] nil))))

(with-console-logger
  (let [r (make-thing-resource (ref nil))]
    (do
      (let [resp (r (request :get "/r1"))]
        (fact "get => 404" resp => NOT-FOUND))
      (let [resp (r (-> (request :put "/r1")
                        (assoc :body "r1")
                        (header "content-type" "text/plain")))]
        (fact "put => 202" resp => CREATED))
      (let [resp (r (-> (request :get "/r1")))]
        (fact "get => 200" resp => OK)
        (fact "get body is what was put before" resp => (body "r1"))))))
