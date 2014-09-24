(ns test-conditionals
  (:use [liberator.core :only [resource request-method-in
                               with-console-logger]]
        midje.sweet
        checkers
        liberator.util
        [ring.mock.request :only [request header]]))

(defn if-modified-since [req value]
  (header req "if-modified-since" value))

(defn if-unmodified-since [req value]
  (header req "if-unmodified-since" value))

(defn if-match [req value]
  (header req "if-match" (if (= "*" value) value (str "\"" value "\""))))

(defn if-none-match [req value]
  (header req "if-none-match" (if (= "*" value) value (str "\"" value "\""))))


;; get requests

(facts "get requests"
  (facts "if-modified-since true"
    (let [resp ((resource :exists? true
                          :handle-ok "OK"
                          :last-modified (as-date 1001))
                (-> (request :get "/")
                    (if-modified-since (http-date (as-date 1000)))))]
      (fact resp => OK)
      (fact resp => (body "OK"))
      (fact resp => (header-value "Last-Modified" (http-date (as-date 1000))))))

  (facts "if-modified-since false"
    (let [resp ((resource :exists? true
                          :last-modified (as-date 1000))
                (-> (request :get "/")
                    (if-modified-since (http-date (as-date 1000)))))]
      (fact resp => NOT-MODIFIED)
      (fact resp => (no-body))
      (fact resp => (header-value "Last-Modified" (http-date (as-date 1000))))))

  (facts "if-unmodified-since true"
    (let [resp ((resource :exists? true
                          :last-modified (as-date 1000)
                          :handle-precondition-failed "precondition failed")
                (-> (request :get "/")
                    (if-unmodified-since (http-date (as-date 900)))))]
      (fact resp => PRECONDITION-FAILED)
      (fact resp => (body "precondition failed"))))

  (facts "if-unmodified-since false"
    (let [resp ((resource :exists? true
                          :last-modified (as-date 1000)
                          :handle-ok "OK")
                (-> (request :get "/")
                    (if-unmodified-since (http-date (as-date 1000)))))]
      (fact resp => OK)
      (fact resp => (body "OK"))))

 (facts "if-match true"
    (let [resp ((resource :exists? true
                          :etag (constantly "TAG1")
                          :handle-ok "OK")
                (-> (request :get "/")
                    (if-match "TAG1")))]
      (fact resp => OK)
      (fact resp => (body "OK"))
      (fact resp => (header-value "ETag" "\"TAG1\""))))

 (facts "if-match false"
    (let [resp ((resource :exists? true
                          :etag (constantly "TAG1")
                          :handle-ok "OK")
                (-> (request :get "/")
                    (if-match "TAG2")))]
      (fact resp => PRECONDITION-FAILED)))

 (facts "if-none-match true"
   (let [resp ((resource :exists? true
                         :etag (constantly "TAG1")
                         :handle-ok "OK")
               (-> (request :get "/")
                   (if-none-match "TAG1")))]
     (fact resp => NOT-MODIFIED)))

 (facts "if-none-match false"
   (let [resp ((resource :exists? true
                         :etag (constantly "TAG2")
                         :handle-ok "T2")
               (-> (request :get "/")
                   (if-none-match "TAG1")))]
     (fact resp => OK))))


;; put and post requests
(tabular 
 (facts "conditional request for post and put"
   (facts "if-modified-since true"
     (let [resp ((resource :exists? true
                           :method-allowed? (request-method-in ?method)
                           :handle-created "CREATED"
                           :last-modified (as-date 1001))
                 (-> (request ?method "/")
                     (if-modified-since (http-date (as-date 1000)))))]
       (fact resp => CREATED)
       (fact resp => (body "CREATED"))))

   (facts "if-modified-since false"
     (let [resp ((resource :exists? true
                           :method-allowed? (request-method-in ?method)
                           :handle-not-modified "NM"
                           :last-modified (as-date 100000))
                 (-> (request ?method "/")
                     (if-modified-since (http-date (as-date 200000)))))]
       (fact resp => NOT-MODIFIED)
       (fact resp => (body "NM"))))

   (facts "if-unmodified-since false"
     (let [resp ((resource :exists? true
                           :method-allowed? (request-method-in ?method)
                           :handle-accepted "A"
                           :last-modified (as-date 200000))
                 (-> (request ?method "/")
                     (if-unmodified-since (http-date (as-date 100000)))))]
       (fact resp => PRECONDITION-FAILED)))

   (facts "if-unmodified-since true"
     (let [resp ((resource :exists? true
                           :method-allowed? (request-method-in ?method)
                           :handle-created "CREATED"
                           :last-modified (as-date 100000))
                 (-> (request ?method "/")
                     (if-unmodified-since (http-date (as-date 200000)))))]
       (fact resp => CREATED)
       (fact resp => (body "CREATED"))))

   (facts "if-unmodified-since with last-modified changes due do post"
          (let [resp ((resource :exists? true
                                :method-allowed? (request-method-in ?method)
                                :post! (fn [ctx] {::LM 1001})
                                :handle-created "CREATED"
                                :last-modified (fn [ctx] (as-date (get ctx ::LM 1000))))
                      (-> (request ?method "/")
                          (if-unmodified-since (http-date (as-date 1000)))))]
            (fact resp => CREATED)
            (fact resp => (body "CREATED"))
            (fact resp => (header-value "Last-Modified" (http-date (as-date 1001))))))

   (facts "if-match true"
     (let [resp ((resource :exists? true
                           :method-allowed? (request-method-in ?method)
                           :handle-created "CREATED"
                           :etag (constantly "TAG1"))
                 (-> (request ?method "/")
                     (if-match "TAG1")))]
       (fact resp => CREATED)
       (fact resp => (body "CREATED"))))

   (facts "if-match false"
     (let [resp ((resource :exists? true
                           :method-allowed? (request-method-in ?method)
                           :handle-precondition-failed "PF"
                           :etag (constantly "TAG1"))
                 (-> (request ?method "/")
                     (if-match "TAG2")))]
       (fact resp => PRECONDITION-FAILED)
       (fact resp => (body "PF"))))

   (facts "if-none-match true"
     (let [resp ((resource :exists? true
                           :method-allowed? (request-method-in ?method)
                           :handle-created "CREATED"
                           :etag (constantly "TAG1"))
                 (-> (request ?method "/")
                     (if-none-match "TAG1")))]
       (fact resp => PRECONDITION-FAILED)))

   (facts "if-none-match false"
     (let [resp ((resource :exists? true
                           :method-allowed? (request-method-in ?method)
                           :etag (constantly "TAG1"))
                 (-> (request ?method "/")
                     (if-none-match "TAG2")))]
       (fact resp => CREATED))))

 ?method
 :put
 :post)



(facts "if-match * false on unexisting"
  (tabular 
   (let [resp ((resource :method-allowed? true
                         :exists? false
                         :etag (constantly "TAG1"))
               (-> (request ?method "/")
                   (if-match "*")))]
     (fact resp => PRECONDITION-FAILED))
   ?method
   :get
   :post
   :put
   :delete))

(facts "if-none-match * false on existing"
  (tabular 
   (let [resp ((resource :method-allowed? true
                         :exists? true
                         :etag (constantly "TAG1"))
               (-> (request ?method "/")
                   (if-none-match "*")))]
     (if (= ?method :get)
       (fact resp => NOT-MODIFIED)
       (fact resp => PRECONDITION-FAILED)))
   ?method
   :get
   :post
   :put
   :delete))

