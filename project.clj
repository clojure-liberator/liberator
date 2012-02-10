(defproject compojure-rest "0.2"
  :dependencies [[org.clojure/clojure "1.3.0"]
		 [compojure "1.0.1"]
		 [commons-codec/commons-codec "1.5"]
                 [org.clojure/tools.trace "0.7.1"]
                 ]
  :dev-dependencies [
                     [swank-clojure "1.3.3"]
                     [malcolmsparks/ring-mock "0.2.0"]
                     [ring/ring-jetty-adapter "1.0.1"]
                     [hiccup "0.3.7"]
		     ])
