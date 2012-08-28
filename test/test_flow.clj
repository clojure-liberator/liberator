(ns test-flow
  (:use liberator.core
        midje.sweet
        checkers
        [ring.mock.request :only [request header]]))

(def r-post-to-existing
  (resource :method-allowed? (request-method-in :post)
            :exists? true
            :handle-created "OK"))
(with-console-logger
  (let [resp (-> (request :post "/")
                 (r-post-to-existing))]
    (fact resp => CREATED)
    (fact resp => (body "OK"))))


(with-console-logger
  (let [r (resource :method-allowed? (request-method-in :post)
                    :exists? true
                    :post-redirect? true
                    :handle-created "OK"
                    :new? false? 
                    :see-other "http://example.com/foo")
        resp (r (request :post "/")) ]
    (fact resp => (SEE-OTHER  "http://example.com/foo"))))

