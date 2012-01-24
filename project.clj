(defproject compojure-rest "0.2"
  :dependencies [[org.clojure/clojure "1.3.0"]
		 [compojure "1.0.1"]
                 ;;[com.twinql.clojure/clj-conneg "1.1.0"]
		 [ring/ring-jetty-adapter "1.0.1"]
                 ;;[org.clojure/data.codec "0.1.0"]
		 [commons-codec/commons-codec "1.5"]
                 ;;[commons-fileupload/commons-fileupload "1.2.1"]
                 ;;[commons-io/commons-io "1.4"]
                 [org.clojure/tools.trace "0.7.1"]
                 ]
  :dev-dependencies [
                     [swank-clojure "1.3.3"]
                     [malcolmsparks/ring-mock "0.2.0"]
		     ])
