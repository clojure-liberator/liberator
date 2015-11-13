(ns test-response
  (:use
   midje.sweet
   [ring.mock.request :only [request header]]
   [compojure.core :only [context ANY]]
   [liberator.core :only [defresource resource run-handler]]
   [liberator.representation :only [ring-response as-response]]
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
                     {:resource {:handle-ok (fn [_] "body")
                                 :as-response as-response}
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
        (-> (run-handler "handle-ok" 200 "ok"
                         {:resource {:handle-ok (fn [c] (ring-response
                                                        {:body "ok" :headers {"Vary" "*"}}))
                                     :as-response as-response}
                          :representation {:media-type "text/plain"}})
            (get-in [:headers "Vary"]))
    => "*"))

(facts "Adding `Allow` header automatically"
  
  (fact "done for `OPTIONS` request" 
    (-> (request :options "/")
        ((resource :handle-ok "ok" :allowed-methods [:get :head :options])))
    => (header-value "Allow" "GET, HEAD, OPTIONS"))

  (fact "Accept-Patch check for `OPTIONS` request" 
    (-> (request :options "/")
        ((resource :handle-ok "ok" :allowed-methods [:get :head :options :patch]
                   :patch-content-types ["application/json-patch+json"])))
    => (header-value "Accept-Patch" "application/json-patch+json"))

  (fact "done when method is not allowed"
    (-> (request :post "/")
        ((resource :handle-ok "ok" :allowed-methods [:get :head :options])))
    => (header-value "Allow", "GET, HEAD, OPTIONS"))

  (fact "not done when header already exists"
    (-> (request :options "/")
        ((resource :handle-options (ring-response {:headers {"Allow" "GET"}})
                   :allowed-methods [:get :head :options])))
    => (header-value "Allow", "GET"))

  (fact "not done any other time"
    (-> (request :get "/")
        ((resource :handle-ok "ok")))
    => (fn [c] (not (contains? (:headers c) "Allow"))))
)


(facts "Options can return a body"
      (fact "return a simple response"
             (-> (request :options "/")
                 ((resource :allowed-methods [:get :options]
                            :handle-ok "ok"
                            :handle-options "options")))
             => (body "options"))
      (fact "return a ring response"
            (let [resp (-> (request :options "/")
                           ((resource :allowed-methods [:get :options]
                                      :available-media-types ["text/plain" "text/html"]
                                      :handle-ok "ok"
                                      :handle-options (fn [ctx]
                                                        ;; workaround until issue #152 is fixed
                                                        (-> "options"
                                                            (as-response (assoc-in ctx [:representation :media-type]
                                                                                   "text/plain"))
                                                            (assoc-in [:headers "X-Foo"] "bar")
                                                            (ring-response))))))]
              resp => (body "options")
              resp) => (header-value "X-Foo" "bar")))
