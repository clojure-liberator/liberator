;; Use leiningen 2
(defproject liberator "0.5.0"
  :description "Liberator - A REST library for Clojure."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.trace "0.7.3"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/data.json "0.1.2"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/clojurescript "0.0-1236"]
                 [hiccup "1.0.0"]] ;; Used by code rendering default representations. 
  :plugins [[lein-midje "2.0.0-SNAPSHOT"]
            [lein-ring "0.7.1"]]

  :profiles {:dev {:dependencies [[ring/ring-jetty-adapter "1.1.0"]
                                  [ring-mock "0.1.2"]
                                  [ring/ring-devel "1.1.0"]
                                  [compojure "1.0.2"] ;; only for examples
                                  [midje "1.4.0"]
                                  ]}}

  :source-paths ["src" "examples/clj"]
  :test-paths ["test"]

  :ring {:handler examples.server/handler
         :adapter {:port 8000}}

  :aliases {"examples" ["run" "-m" "examples.server"]})

