(ns test-flow
  (:use liberator.core
        [liberator.representation :only (ring-response)]
        midje.sweet
        checkers
        [ring.mock.request :only [request header]]))

(facts "customize the initial context"
       (let [resp ((resource :initialize-context {::field "some initial context"}
                             :handle-ok ::field) (request :get "/"))]
         (fact resp => (body "some initial context"))))

(facts "GET Requests"
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

       (facts "get on moved temporarily with custom handler"
              (let [resp ((resource :exists? false
                                    :existed? true
                                    :moved-temporarily? {:location "http://new.example.com/"}
                                    :handle-moved-temporarily "Temporary redirection...")
                          (request :get "/"))]
                (fact resp => (MOVED-TEMPORARILY "http://new.example.com/"))
                (fact resp => (body "Temporary redirection..."))))

       (facts "get on moved permantently"
              (let [resp ((resource :exists? false :existed? true
                                    :moved-permanently? true
                                    :location "http://other.example.com/")
                          (request :get "/"))]
                (fact resp => (MOVED-PERMANENTLY "http://other.example.com/"))))

       (facts "get on moved permantently with custom response"
              (let [resp ((resource :exists? false :existed? true
                                    :moved-permanently? {:location "http://other.example.com/"}
                                    :handle-moved-permanently "Not here, there!")
                          (request :get "/"))]
                (fact resp => (MOVED-PERMANENTLY "http://other.example.com/"))
                (fact resp => (body "Not here, there!"))))

       (facts "get on moved permantently with custom response and explicit header"
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
                (fact resp => (MOVED-PERMANENTLY "http://other.example.com/")))))

(facts "POST Requests"
       (let [r (resource :allowed-methods [:post]
                         :exists? true
                         :handle-created "Created")
             resp (r (request :post "/"))]
         (fact "Post to existing" resp => CREATED)
         (fact "Body of 201" resp => (body "Created")))

       (let [r (resource :allowed-methods [:post]
                         :exists? true
                         :post-enacted? true
                         :post-redirect? {:location "http://example.com/foo"})
             resp (r (request :post "/"))]
         (fact "Post completed to existing resource and redirect" resp => (SEE-OTHER "http://example.com/foo")))

       (let [r (resource :allowed-methods [:post]
                         :exists? true
                         :post-enacted? true)
             resp (r (request :post "/"))]
         (fact "Post completed to existing resource" resp => CREATED))

       (let [r (resource :allowed-methods [:post]
                         :exists? true
                         :post-enacted? true
                         :new? false
                         :respond-with-entity? false)
             resp (r (request :post "/"))]
         (fact "Post completed to existing resource with new? and respond-with-entity? as false" resp => NO-CONTENT))

       (let [r (resource :allowed-methods [:post]
                         :exists? true
                         :post-enacted? false)
             resp (r (request :post "/"))]
         (fact "Post in progress to existing resource" resp => ACCEPTED))

       (let [r (resource :allowed-methods [:post]
                         :exists? true
                         :conflict? true)
             resp (r (request :post "/"))]
         (fact "Post to existing with conflict" resp => CONFLICT))

       (let [r (resource :allowed-methods [:post]
                         :exists? true
                         :post-redirect? {:location "http://example.com/foo"})
             resp (r (request :post "/")) ]
         (fact "Post to existing resource and redirect" resp => (SEE-OTHER  "http://example.com/foo")))

       (let [r (resource :allowed-methods [:post]
                         :exists? false
                         :post-redirect? true
                         :can-post-to-missing? true
                         :location "http://example.com/foo")
             resp (r (request :post "/")) ]
         (fact "Post to missing can redirect" resp => (SEE-OTHER  "http://example.com/foo")))

       (let [r (resource :allowed-methods [:post]
                         :exists? false
                         :location "foo"
                         :can-post-to-missing? true)
             resp (r (request :post "/")) ]
         (fact "Post to missing if post to missing is allowed" resp => CREATED)
         (fact "Location is set" resp => (contains {:headers (contains {"Location" "foo"})})))

       (let [r (resource :allowed-methods [:post]
                         :exists? false
                         :can-post-to-missing? false
                         :handle-not-found "not-found")
             resp (r (request :post "/")) ]
         (fact "Post to missing can give 404" resp => NOT-FOUND)
         (fact "Body of 404" resp => (body "not-found")))

       (let [r (resource :allowed-methods [:post]
                         :exists? true
                         :can-post-to-missing? false)
             resp (r (request :post "/")) ]
         (fact "Post to existing if post to missing forbidden is allowed" resp => CREATED)))

(facts "PUT requests"
       (let [r (resource :allowed-methods [:put]
                         :exists? false
                         :can-put-to-missing? false)
             resp (r (request :put "/"))]
         (fact "Put to missing can give 501" resp => NOT-IMPLEMENTED))

       (let [r (resource :allowed-methods [:put]
                         :exists? false
                         :can-put-to-missing? true)
             resp (r (request :put "/"))]
         (fact "Put to missing can give 201" resp => CREATED))

       (let [r (resource :allowed-methods [:put]
                         :exists? true
                         :conflict? true)
             resp (r (request :put "/"))]
         (fact "Put to existing with conflict" resp => CONFLICT))
       
       (let [r (resource :allowed-methods [:put]
                         :processable? false)
             resp (r (request :put "/"))]
         (fact "Unprocessable can give 422" resp => UNPROCESSABLE))

       (let [r (resource :allowed-methods [:put]
                         :exists? true
                         :put-enacted? true)
             resp (r (request :put "/"))]
         (fact "Put to existing completed" resp => CREATED))

       (let [r (resource :allowed-methods [:put]
                         :exists? true
                         :put-enacted? true
                         :new? false
                         :respond-with-entity? false)
             resp (r (request :put "/"))]
         (fact "Put to existing resource with new? and respond-with-entity? as false" resp => NO-CONTENT))

       (let [r (resource :allowed-methods [:put]
                         :exists? true
                         :put-enacted? false)
             resp (r (request :put "/"))]
         (fact "Put in progress to existing resource" resp => ACCEPTED)))

(facts "HEAD requests"
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
