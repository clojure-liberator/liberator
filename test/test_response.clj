(ns test-response
  (:use
   midje.sweet
   [ring.mock.request :only [request header]]
   [compojure.core :only [context ANY]]
   [liberator.core :only [defresource resource run-handler]]
   [liberator.representation :only [ring-response]]
   [checkers]))

(facts "Content negotiation"
 (tabular "Content-Type header is added automatically"
  (-> (request :get "/")
      (header "Accept" ?accept)
      ((resource :available-media-types [?available] :handle-ok "ok")))
  => (content-type (str ?expected ";charset=UTF-8"))
  ?accept ?available ?expected    
  "text/html" "text/html" "text/html"
  "text/plain" "text/plain" "text/plain"
  "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" "text/html" "text/html"
  "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" "text/html" "text/html"))

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

