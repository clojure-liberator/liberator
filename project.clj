(defproject liberator "0.15.3-SNAPSHOT"
  :description "Liberator - A REST library for Clojure."
  :url "http://clojure-liberator.github.io/liberator"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.csv "0.1.3"]
                 [hiccup "1.0.5"]] ;; Used by code rendering default representations.
  :deploy-repositories  [["releases" :clojars]]
  :lein-release {:deploy-via :clojars}

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :scm {:connection "scm:git:https://github.com/clojure-liberator/liberator.git"
        :url "https://github.com/clojure-liberator/liberator"}

  :plugins [[lein-midje "3.2.1"]
            [lein-shell "0.5.0"]]

  :profiles {:dev {:dependencies [[ring/ring-jetty-adapter "1.5.1"]
                                  [ring-mock "0.1.5" :exclusions [ring/ring-codec]]
                                  [ring/ring-devel "1.5.1"]
                                  [midje "1.8.3"]
                                  [compojure "1.5.2"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0" :upgrade? false]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0" :upgrade? false]]}
             :1.9a {:dependencies [[org.clojure/clojure "1.9.0-alpha17" :upgrade? false]
                                   [midje "1.9.0-alpha6"]]}

             :dl  {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :1.8dl [:1.8 :dl]}

  :source-paths ["src"]
  :test-paths ["test"]

  :aliases {"test-all" ["with-profile" "+1.7:+1.8:+1.8dl:+1.9a" "test"]
            "graph"    ["do"
                        ["run" "-m" "liberator.graph/generate-dot-file" "trace.dot"]
                        ["shell" "dot" "-O" "-Tsvg" "trace.dot"]
                        ["shell" "mv" "trace.dot.svg" "src/liberator/trace.svg"]]})

