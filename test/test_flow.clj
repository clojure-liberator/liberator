(ns test-flow
  (:use liberator.core
        [liberator.representation :only (ring-response)]
        midje.sweet
        checkers
        [ring.mock.request :only [request header]]))



(facts "get existing resource"
  (let [resp ((resource :exists? true :handle-ok "OK") (request :get "/"))]
    (fact resp => OK)
    (fact resp => (body "OK"))))

(facts "get unexisting resource"
  (let [resp ((resource :exists? false :handle-not-found "NOT-FOUND") (request :get "/"))]
    (fact resp => NOT-FOUND)
    (fact resp => (body "NOT-FOUND"))))

(facts "get on moved temporarily"
       (let [resp ((resource :exists? false
                             :existed? true
                             :moved-temporarily? {:location "http://new.example.com/"})
              (request :get "/"))]
    (fact resp => (MOVED-TEMPORARILY "http://new.example.com/"))))

(facts "get on moved permantently"
  (let [resp ((resource :exists? false :existed? true
                        :moved-permanently? true
                        :location "http://other.example.com/")
              (request :get "/"))]
    (fact resp => (MOVED-PERMANENTLY "http://other.example.com/"))))

(facts "get on moved permantently with custom response"
  (let [resp ((resource :exists? false :existed? true
                        :moved-permanently? true
                        :handle-moved-permanently (ring-response {:body "Not here, there!"
                                                                  :headers {"Location" "http://other.example.com/"}}))
              (request :get "/"))]
    (fact resp => (MOVED-PERMANENTLY "http://other.example.com/"))
    (fact resp => (body "Not here, there!"))))

(facts "get on moved permantently with custom response"
  (let [resp ((resource :exists? false :existed? true
                        :moved-permanently? true
                        :handle-moved-permanently (ring-response {:body "Not here, there!"
                                                             :headers {"Location" "http://other.example.com/"}}))
              (request :get "/"))]
    (fact resp => (MOVED-PERMANENTLY "http://other.example.com/"))
    (fact resp => (body "Not here, there!"))))

(facts "get on moved permantently with automatic response"
  (let [resp ((resource :exists? false :existed? true
                        :moved-permanently? true
                        :location "http://other.example.com/")
              (request :get "/"))]
    (fact resp => (MOVED-PERMANENTLY "http://other.example.com/"))))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? true
                  :handle-created "Created")
      resp (r (request :post "/"))]
  (fact "Post to existing" resp => CREATED)
  (fact "Body of 201" resp => (body "Created")))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? true
                  :post-redirect? {:location "http://example.com/foo"})
      resp (r (request :post "/")) ]
  (fact "Post to existing resource and redirect" resp => (SEE-OTHER  "http://example.com/foo")))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? false
                  :post-redirect? true
                  :can-post-to-missing? true
                  :location "http://example.com/foo")
      resp (r (request :post "/")) ]
  (fact "Post to missing can redirect" resp => (SEE-OTHER  "http://example.com/foo")))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? false
                  :location "foo"
                  :can-post-to-missing? true)
      resp (r (request :post "/")) ]
  (fact "Post to missing if post to missing is allowed" resp => CREATED)
  (fact "Location is set" resp => (contains {:headers (contains {"Location" "foo"})})))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? false
                  :can-post-to-missing? false
                  :handle-not-found "not-found")
      resp (r (request :post "/")) ]
  (fact "Post to missing can give 404" resp => NOT-FOUND)
  (fact "Body of 404" resp => (body "not-found")))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? true
                  :can-post-to-missing? false)
      resp (r (request :post "/")) ]
  (fact "Post to existing if post to missing forbidden is allowed" resp => CREATED))



(let [r (resource :method-allowed? [:put]
                  :exists? false
                  :can-put-to-missing? false)
      resp (r (request :put "/"))]
  (fact "Put to missing can give 501" resp => NOT-IMPLEMENTED))

(let [r (resource :method-allowed? [:put]
                  :exists? false
                  :can-put-to-missing? true)
      resp (r (request :put "/"))]
  (fact "Put to missing can give 201" resp => CREATED))

(let [r (resource :method-allowed? [:put]
                  :processable? false)
      resp (r (request :put "/"))]
  (fact "Unprocessable can give 422" resp => (is-status 422)))

(facts "Head requests"
  (facts "on existing resource"
    (let [resp ((resource :exists? true :handle-ok "OK") (request :head "/"))]
      (fact resp => OK)
      (fact resp => (content-type "text/plain;charset=UTF-8"))
      (fact resp => (no-body))))
  
  (facts "unexisting resource"
    (let [resp ((resource :exists? false :handle-not-found "NOT-FOUND") (request :head "/"))]
      (fact resp => NOT-FOUND)
      (fact resp => (no-body))))
  
  (facts "on moved temporarily"
    (let [resp ((resource :exists? false
                          :existed? true
                          :moved-temporarily? true
                          :location "http://new.example.com/")
                (request :get "/"))]
      (fact resp => (MOVED-TEMPORARILY "http://new.example.com/"))
      (fact resp => (no-body)))))
