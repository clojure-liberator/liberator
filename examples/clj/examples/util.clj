(ns examples.util
  (:require
   [clojure.java.io :as io])
  (:use
   [ring.util.mime-type :only [ext-mime-type]]
   [cljs.closure :only [build]]
   [compojure.core :only [routes ANY]]
   [liberator.core :only [defresource]]))

(defn wrap-binder [handler key value]
  (fn [request]
    (handler (assoc request key value))))

(let [static-dir (io/file "examples/static")]
  (defresource static

    :available-media-types
    #(let [path (get-in % [:request :route-params :*])]
       (if-let [mime-type (ext-mime-type path)]
         [mime-type]
         []))

    :exists?
    #(let [path (get-in % [:request :route-params :*])]
       (let [f (io/file static-dir path)]
         [(.exists f) {::file f}]))

    :handle-ok (fn [{f ::file}] f)

    :last-modified (fn [{f ::file}] (.lastModified f))))

(defn get-work-dir []
  (doto (io/file "target/cljs")
    (.mkdirs)))

(defn last-modified [f]
  (cond
   (not (.exists f)) 0
   (.isFile f) (.lastModified f)
   (.isDirectory f) (reduce max (map last-modified (.listFiles f)))))

(let [srcdir (io/file "examples/cljs")
      workdir (get-work-dir)]
  (defresource clojurescript-resource
    :available-media-types ["text/javascript"]
    :exists? (fn [context]
               (let [path (get-in context [:request ::jspath])
                     deps-file (io/file workdir "deps.js")]
                 (when (= path "goog/base.js")
                   (build (str srcdir) {:output-dir workdir :output-to (str deps-file)}))
                 (let [f (io/file workdir path)]
                     [(.exists f) {::file f}])))
    :handle-ok (fn [context]
                 (::file context))
    :last-modified (fn [context]
                     (.lastModified (::file context)))))

(defn create-cljs-route [prefix]
  (routes
   (ANY [(str prefix "/:path") :path #".*js"] [path]
        (wrap-binder clojurescript-resource ::jspath path))))

