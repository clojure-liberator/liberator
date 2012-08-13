;; Use leiningen 2
(require 'clojure.java.shell)

(defn get-version []
  (let [[major minor]
        (re-seq  #"[^-]+"
                 (.trim (:out (clojure.java.shell/sh
                               "git" "describe" "--tags" "--long" "HEAD"))))]
    (if (zero? (Integer/parseInt minor))
      major
      (format "%s.%s" major minor))))

(defproject liberator (get-version)
  :description "Liberator - A REST library for Clojure."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.trace "0.7.3"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/data.json "0.1.2"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/clojurescript "0.0-1236"]
                 [hiccup "1.0.0"] ;; Used by code rendering default representations.
                 ] 

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :plugins [[lein-midje "2.0.0-SNAPSHOT"]
            [lein-ring "0.7.1"]]

  :profiles {:dev {:dependencies [[ring/ring-jetty-adapter "1.1.0"]
                                  [ring-mock "0.1.2"]
                                  [com.stuartsierra/lazytest "1.2.3"]
                                  [compojure "1.0.2"] ;; only for examples
                                  [midje "1.4.0"]
                                  ]}}

  ;; This is only needed for lazytest
  :repositories {"stuart" "http://stuartsierra.com/maven2"}
  
  :source-paths ["src" "examples/clj"]
  :test-paths ["test"]

  :ring {:handler examples.server/handler
         :adapter {:port 8000}}

  :aliases {"examples" ["run" "-m" "examples.server"]}
  )

