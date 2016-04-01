(defproject liberator "0.14.1"
  :description "Liberator - A REST library for Clojure."
  :url "http://clojure-liberator.github.io/liberator"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.1"]
                 [org.clojure/data.csv "0.1.2"]
                 [hiccup "1.0.3"]] ;; Used by code rendering default representations.
  :deploy-repositories  [["releases" :clojars]]
  :lein-release {:deploy-via :clojars}

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
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :dl  {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :1.8dl [:1.8 :dl]}

  :source-paths ["src"]
  :test-paths ["test"]

  :ring {:handler examples.server/handler
         :adapter {:port 8000}}

  :aliases {"examples" ["run" "-m" "examples.server"]
            "test-all" ["with-profile" "+1.4:+1.5:+1.6:+1.7:+1.8:+1.8dl" "test"]})
