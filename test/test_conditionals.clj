(ns test-flow
  (:use liberator.core
        midje.sweet
        checkers
        liberator.util
        [ring.mock.request :only [request header]]))

(defn if-modified-since [req value]
  (header req "If-Modified-Since" value))

(defn if-unmodified-since [req value]
  (header req "If-Unmodified-Since" value))


;; get requests
(facts "get if-modified-since true"
  (let [resp ((resource :exists? true
                        :handle-ok "OK"
                        :last-modified (as-date 1001))
              (-> (request :get "/")
                  (if-modified-since (http-date (as-date 1000)))))]
    (fact resp => OK)
    (fact resp => (body "OK"))))

(facts "get if-modified-since false"
  (let [resp ((resource :exists? true
                        :last-modified (as-date 1000))
              (-> (request :get "/")
                  (if-modified-since (http-date (as-date 1000)))))]
    (fact resp => NOT-MODIFIED)
    (fact resp => (body nil?))))

(facts "get if-unmodified-since true"
  (let [resp ((resource :exists? true
                        :last-modified (as-date 1000)
                        :handle-precondition-failed "precondition failed")
              
              (-> (request :get "/")
                  (if-unmodified-since (http-date (as-date 900)))))]
    (fact resp => PRECONDITION-FAILED)
    (fact resp => (body "precondition failed"))))

(facts "get if-unmodified-since false"
  (let [resp ((resource :exists? true
                        :last-modified (as-date 1000)
                        :handle-ok "OK")
              
              (-> (request :get "/")
                  (if-unmodified-since (http-date (as-date 1000)))))]
    (fact resp => OK)
    (fact resp => (body "OK"))))


;; put requests
(facts "put if-modified-since true"
  (let [resp ((resource :exists? true
                        :method-allowed? (request-method-in :put)
                        :handle-created "CREATED"
                        :last-modified (as-date 1001))
              (-> (request :put "/")
                  (if-modified-since (http-date (as-date 1000)))))]
    (fact resp => CREATED)
    (fact resp => (body "CREATED"))))

(facts "put if-modified-since false"
  (let [resp ((resource :exists? true
                        :method-allowed? (request-method-in :put)
                        :handle-not-modified "NM"
                        :last-modified (as-date 100000))
              (-> (request :put "/")
                  (if-modified-since (http-date (as-date 200000)))))]
    (fact resp => NOT-MODIFIED)
    (fact resp => (body "NM"))))

(facts "put if-unmodified-since false"
  (let [resp ((resource :exists? true
                        :method-allowed? (request-method-in :put)
                        :handle-accepted "A"
                        :last-modified (as-date 200000))
              (-> (request :put "/")
                  (if-unmodified-since (http-date (as-date 100000)))))]
    (fact resp => PRECONDITION-FAILED)))

(facts "put if-unmodified-since true"
  (let [resp ((resource :exists? true
                        :method-allowed? (request-method-in :put)
                        :handle-created "CREATED"
                        :last-modified (as-date 100000))
              (-> (request :put "/")
                  (if-unmodified-since (http-date (as-date 200000)))))]
    (fact resp => CREATED)
    (fact resp => (body "CREATED"))))


