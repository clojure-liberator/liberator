;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns test
  (:use compojure)
  (:use compojure-rest))

(defn hello-resource []
  (compojure-rest/make-handler
   {
    :get (fn [req] (str "Hello " ((req :route-params {}) :who "unknown foreigner")))
    :generate-etag (fn [req] ((req :route-params) :who))
    :expires (constantly 10000)		; expire in 10 sec
    :last-modified (constantly -10000)	; last modified 10 sec ago
    :authorized? (fn [req] (not (= "tiger" ((req :route-params {}) :who))))
    :allowed? (fn [req] (not (= "scott" ((req :route-params {}) :who))))
    :exists? #(not (= "cat" ((% :route-params {}) :who)))
    }))


(defroutes my-app
  (ANY "/hello/:who" (hello-resource))
  (ANY "/simple/" (compojure-rest/make-handler { :get (fn [req] "Simple") }))
  (ANY "*" (page-not-found)))

(defn main []
  (do
    (defserver test-server {:port 8080} "/*" (servlet my-app))
    (start test-server)))
