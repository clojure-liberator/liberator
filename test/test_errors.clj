(ns test-errors
  (:use liberator.core
        [liberator.representation :only [ring-response]]
        midje.sweet
        checkers
        [ring.mock.request :only [request header]]))

(facts "default exception handler rethrows exception"
  (fact ((resource :exists? (fn [_] (throw (RuntimeException. "test"))))
         (request :get "/")) => (throws RuntimeException "test")))

(facts "custom exception handler is invoked"
  (let [resp ((resource :exists? (fn [_] (throw (RuntimeException. "foo")))
                        :handle-exception (fn [{ex :exception}]
                                            (str "error: " (.getMessage ex))))
              (request :get "/"))]
    (fact resp => INTERNAL-SERVER-ERROR)
    (fact resp => (body #"error: foo"))))

(facts "custom exception handler can return ring response"
  (let [resp ((resource :exists? (fn [_] (throw (RuntimeException. "foo")))
                        :handle-exception (fn [_]
                                            (ring-response {:status 555 :body "bar"})))
              (request :get "/"))]
    (fact resp => (is-status 555))
    (fact resp => (body "bar"))))

(facts "custom exception handler is converted to response"
  (let [resp ((resource :available-media-types ["application/edn"]
                        :exists? (fn [_] (throw (RuntimeException. "foo")))
                        :handle-exception "baz")
              (request :get "/"))]
    (fact resp => INTERNAL-SERVER-ERROR)
    (fact resp => (body "baz"))
    (fact resp => (content-type #"application/edn;charset=.*"))))

(facts "custom exception handler content-type is negotiated"
  (let [resp ((resource :available-media-types ["application/edn" "text/plain"]
                        :exists? (fn [_] (throw (RuntimeException. "foo")))
                        :handle-exception "baz")
              (-> (request :get "/")
                  (header "Accept" "text/plain")))]
    (fact resp => INTERNAL-SERVER-ERROR)
    (fact resp => (body "baz"))
    (fact resp => (content-type #"text/plain;charset=.*"))))

(facts "custom exception handler content-type is not negotiated prior to media-type-available? and defaults to text/plain"
  (let [resp ((resource :available-media-types ["application/edn" "foo/bar"]
                        :service-available? (fn [_] (throw (RuntimeException. "foo")))
                        :handle-exception "baz")
              (-> (request :get "/")
                  (header "Accept" "text/plain")))]
    (fact resp => INTERNAL-SERVER-ERROR)
    (fact resp => (body "baz"))
    (fact resp => (content-type #"text/plain;charset=.*"))))

(facts "custom exception handler not invoked if handler throws exception"
  (let [res (resource :service-available? (fn [_ _] (throw (RuntimeException. "foo")))
                      :handle-exception (fn [_ _] (throw (RuntimeException. "bar"))))]
    (fact (res (-> (request :get "/")
                   (header "Accept" "text/plain"))) => (throws RuntimeException))))

(facts "error throw by liberator itself don't get handle managment"
       (let [resp ((resource
                    :service-available? (fn [_] {:resource [1 2]})
                    :exists? (fn [_] (throw (RuntimeException. "right")))
                    :handle-exception (fn [{ex :exception}]
                                        (str "error: " (.getMessage ex))))
                   (request :get "/"))]
         (fact resp => INTERNAL-SERVER-ERROR)
         (fact resp => (body #"error: right"))))
