(ns test
  (:use [liberator.core]
        [compojure.core :only [context ANY routes defroutes]]
        [hiccup.core]
        [ring.adapter.jetty]
        [ring.middleware.stacktrace :only (wrap-stacktrace)]
        [ring.middleware.reload :only (wrap-reload)])
  
  (:import java.io.InputStreamReader)
  (:import [java.security MessageDigest]))



(defn sha [text]
  (->> text
    .getBytes
    (.digest (MessageDigest/getInstance "SHA"))
    (map #(format "%02x" %))
    (apply str)))

(def products (ref []))

(defn has? [key val]
  #(when (= val (% key)) %))

(defn product-by-id [id]
  (some (has? :id id) @products))

(defn max-id [ps]
  (apply max (conj (map :id ps) 0)))

(defn add-product [title]
  (dosync
   (let [next-id (inc (max-id @products))]
     (alter products #(conj % { :id next-id :title title }))
     next-id)))

(defn remove-product-by-id [id]
  (dosync 
   (let [oldps @products]
     (= oldps (alter products (fn [ps] (remove (has? :id id) ps)))))))

(defn update-product-with-id [id title]
  (dosync
   (alter products (fn [ps] (conj (remove (has? :id id) ps) { :id id :title title})))))

(defn all-products [] @products)

(def hello-resource
     (resource 
      :to_html (fn [_ req _] 
		 (let [who (-> req :route-params :*)]
		   (str "hello, " (if (empty? who) "stranger" who) ".")))))

(def products-resource
     (resource 
      :method-allowed? #(some #{(% :request-method)} [:get :post])
      :content-types-provided { "text/html" :to_html, "text/plain" :to_text }
      :created (fn [_ req _] (str "Product " (add-product (slurp (:body req)))  " created."))
      :to_html (fn [_ req _] 
		 (html [:html
			[:head [:title "All Products"]]
			[:body [:h1 "All Products"]
			 [:ul (map (fn [p] [:li [:a { :href (p :id)} (p :title)]]) 
				   (all-products))]]]))
      :to_text (fn [_ req _]
		 (apply str (map #(str (% :id) ": " (% :title) "\n") (all-products))))))

(def product-resource
     (resource
      :method-allowed? #(some #{(% :request-method)} [:get :delete :put ])
      :content-types-provided { "text/html" :to_html, "text/plain" :to_text }
      :exists? (fn [req] (if-let [id (read-string (-> req :route-params :id))]
			   (if-let [product (product-by-id id)]
			     { ::product product })
			   nil))
      :conflict? (fn [req] (let [id (read-string (-> req :route-params :id))]
			     (dosync 
			      (when (product-by-id id)
				(update-product-with-id id (slurp (:body req))))
			      false)))
      :etag    (fn [req] (sha (str (-> req ::product :title))))
      :delete-enacted? (fn [req] (remove-product-by-id (read-string (-> req :route-params :id))))
      :to_html (fn [rmap req status]
		 (let [product (req ::product)]	
		   (html [:h1 (product :id)] [:p (product :title)])))
      :to_text (fn [rmap req status]
		 (let [product (req ::product)]	
		   (str  (product :id) ": " (product :title))))))


(defroutes my-app
  (ANY "/hello/*"      hello-resource)
  (ANY "/products/"    products-resource)
  (ANY "/products/:id" product-resource)
  (ANY "/echo/:foo"    [] (resource  
                           :content-types-provided 
                           { "text/plain" 
                             (fn [_ req _] 
                               (with-out-str (clojure.pprint/pprint
                                              (dissoc req :servlet-request)))),
                             "text/html"
                             (fn [_ req _]
                               (html [:pre 
                                      (h (with-out-str (clojure.pprint/pprint
                                                        (dissoc req :servlet-request))))]))}))
  (ANY "*" [] {:status 404 :body "Resource not found"}))

(def handler
  (-> my-app
      wrap-reload
      wrap-stacktrace))

(defn main []
  (do
    (run-jetty #'handler {:port 3000 :join? false})))


