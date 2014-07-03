(defproject liberator "0.11.1"
  :description "Liberator - A REST library for Clojure."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.trace "0.7.3"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/data.json "0.2.1"]
                 [org.clojure/data.csv "0.1.2"]
                 [hiccup "1.0.3"]] ;; Used by code rendering default representations.

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :scm {:connection "scm:git:https://github.com/clojure-liberator/liberator.git"
        :url "https://github.com/clojure-liberator/liberator"}

  :plugins [[lein-midje "3.1.3" :exclusions [leiningen-core]]
            [lein-ring "0.8.10" :exclusions [org.clojure/clojure]]]

  :profiles {:dev {:dependencies [[ring/ring-jetty-adapter "1.2.1" :exclusions [joda-time]]
                                  [ring-mock "0.1.2"]
                                  [ring/ring-devel "1.2.1" :exclusions [joda-time]]
                                  [midje "1.6.0" :exclusions [org.clojure/clojure]]
                                  ;; only for examples
                                  [compojure "1.0.2" :exclusions [org.clojure/tools.macro]]
                                  [org.clojure/clojurescript "0.0-1450"]]
                   :source-paths [ "src" "examples/clj"]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-beta1"]]}}

  :source-paths ["src"]
  :test-paths ["test"]

  :ring {:handler examples.server/handler
         :adapter {:port 8000}}

  :aliases {"examples" ["run" "-m" "examples.server"]
            "test-all" ["with-profile" "+1.4:+1.5:+1.6" "test"]})
