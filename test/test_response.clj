(ns test-response
  (:use
   midje.sweet
   [ring.mock.request :only [request header]]
   [compojure.core :only [context ANY]]
   [liberator.core :only [defresource resource run-handler]]
   [liberator.representation :only [ring-response]]))

;; TODO: Ensure that we use compojure.response/Renderable underneath in any body function

(future-facts
 (->
  (request :get "/users/10/display")
  ((context "/users/:id" [id]
            (ANY "/display" []
                 (resource
                  :handle-ok {"text/plain" (format "User id is %s" id)}))))
  :body)
 => "User id is 10")  


(comment
  (deftest response-tests
    
    (testing "Content negotiation"
      (are [accept content-type expected-type]
           (= expected-type
              (-> (request :get "/")
                  (header "Accept" accept)
                  ((resource :get {content-type "Some content"}))
                  (get-in [:headers "Content-Type"])))
           "text/html" "text/html" "text/html"
           "text/plain" "text/plain" "text/plain"
           "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" "text/html" "text/html"
           "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" "text/html" "text/html"
           ))))

;; TODO: Add tests for ETag.

(facts "Vary header is added automatically"
  (tabular "Parameter negotiation is added to vary header"
    (-> (run-handler "handle-ok" 200 "ok"
                     {:resource {:handle-ok (fn [_] "body")}
                      :representation ?representation})
        (get-in [:headers "Vary"])) => ?vary
        ?representation ?vary
        {}                    nil
        {:media-type "x"}     "Accept"
        {:media-type ""}      nil
        {:language "x"}       "Accept-Language"
        {:language nil}       nil
        {:charset "ASCII"}        "Accept-Charset"
        {:encoding "x"}       "Accept-Encoding"
        {:media-type "m"
         :language "l"
         :charset "ASCII"
         :encoding "x"}       "Accept, Accept-Charset, Accept-Language, Accept-Encoding"
        {:media-type "m"
         :charset "ASCII"
         :encoding "x"}       "Accept, Accept-Charset, Accept-Encoding")
  
  
  (fact "Vary header can be overriden by handler"
        (-> (run-handler "handle-ok" 200 "ok" {:resource {:handle-ok (fn [c] (ring-response {:body "ok" :headers {"Vary" "*"}}))}
                                           :representation {:media-type "text/plain"}})
            (get-in [:headers "Vary"]))
    => "*"))
