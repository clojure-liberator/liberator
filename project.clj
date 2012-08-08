(defproject liberator "0.3.0"
  :description "Liberator - A REST library for Clojure."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.trace "0.7.3"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/data.json "0.1.2"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/clojurescript "0.0-1236"]
                 [hiccup "1.0.0"]
                 [compojure "1.0.2" :scope "test"]
                 [ring/ring-jetty-adapter "1.1.0" :scope "test"]
                 [ring/ring-devel "1.1.0" :scope "test"]
                 [midje "1.4.0" :scope "test"]
                 [ring-mock "0.1.2" :scope "test"]] 
  :plugins [[lein-ring "0.6.4"]
            [lein-swank "1.4.4"]]
  :profiles {:dev {:dependencies [[ring/ring-jetty-adapter "1.1.0"]
                                  [ring-mock "0.1.2"]
                                  [compojure "1.0.2"] ;; only for examples
                                  [midje "1.3.1"]]}}
  :source-paths ["src"]
  :test-paths ["test"]
  :extra-classpath-dirs ["examples/clj"])
