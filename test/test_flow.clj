(ns test-flow
  (:use liberator.core
        midje.sweet
        checkers
        [ring.mock.request :only [request header]]))

(let [r (resource :method-allowed? (request-method-in :post)
                    :exists? true
                    :handle-created "Created")
        resp (r(request :post "/"))]
    (fact "Post to existing" resp => CREATED)
    (fact resp => (body "Created")))


(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? true
                  :post-redirect? true
                  :see-other "http://example.com/foo")
      resp (r (request :post "/")) ]
  (fact "Post to existing resource and redirect" resp => (SEE-OTHER  "http://example.com/foo")))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? false
                  :post-redirect? true
                  :can-post-to-missing? true
                  :see-other "http://example.com/foo")
      resp (r (request :post "/")) ]
  (fact "Post to missing can redirect" resp => (SEE-OTHER  "http://example.com/foo")))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? false
                  :can-post-to-missing? true)
      resp (r (request :post "/")) ]
  (fact "Post to missing if post to missing is allowed" resp => CREATED))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? false
                  :can-post-to-missing? false)
      resp (r (request :post "/")) ]
  (fact "Post to missing can give 404" resp => NOT-FOUND))

(let [r (resource :method-allowed? (request-method-in :post)
                  :exists? true
                  :can-post-to-missing? false)
      resp (r (request :post "/")) ]
  (fact "Post to existing if post to missing forbidden is allowed" resp => CREATED))

