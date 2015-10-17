(ns sandbox
  (:require [clojure.string :as str]
            [liberator.core :refer [defresource wrap]]))

(defn wrap-authorized-token [token-check-fn]
  {:authorized?
   (wrap 
    (fn [f]
      (fn [{{{authentication "WWW-Authenticate"} :headers} :request :as ctx}]
        (if-let [[scheme token] (str/split (or authentication "") #"\s+")]
          (if (= scheme "Token")
            (if (token-check-fn token)
              (f ctx)
              [false {:message "Not authorized. Invalid token!"}])
            [false {:message "Not authorized, please specify Token in WWW-Authenticate header"}])))))
   :handle-unauthorized
   (wrap (fn [h] (fn [ctx] (or (h ctx) (:message ctx)))))})


(defresource token-authorized
  (wrap-authorized-token #(= % "tiger"))
  {:authorized? (fn [{{{q :q} :params} :request}]
                  (or (not= q "forbidden")
                      [false {:message "parameter q must not be \"forbidden\". "}]))
   :handle-unauthorized :message})


(token-authorized {:request-method :get
                   :headers {"WWW-Authenticate" "Token tiger"}
                   :params {:q "some"}})
  

