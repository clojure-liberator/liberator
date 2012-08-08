;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns liberator.accept-charset
  (:require [clojure.string :as string]))

;; "iso-8859-1;q=0.5" -> ["iso-8859-1" 0.5]
(defn split-caq [caq]
  (let [[charset & params] (string/split caq #"\s*;\s*")
        q (first (reverse (sort (filter (comp not nil?)
					(map #(let [[param value] (string/split % #"\s*=")] 
						(if (= "q" param) (Float/parseFloat value)))
					     params)))))]
    [charset q]))

;; ["iso-8859-1;q=0.5" "abc"] -> {"iso-8859-1" 0.5 "abc" 1 }
(defn parse-caqs [caqs]
  (sorted-map (tree-seq (map split-caq caqs))))

(split-caq "iso-8859-1;q=0.5")

(let [accept-header "iso-8859-15;q=1, utf-8;q=0.8; utf-16;q=0.6; iso-8859-1;q=0.8"
      available #{""}]
  (map first (reverse (sort-by second (map split-caq (string/split accept-header  #"\s*,\s*"))))))

;; Accept-Charset: iso-8859-15;q=1, utf-8;q=0.8; utf-16;q=0.6; iso-8859-1;q=0.8
;; [["utf-16" 0.8] ["iso-8859-15"] ["utf-8" 0.9]]
;; -> [["iso-8859-15" 1] ["iso-8859-1" 0.89] ["utf-8" 0.84] ["utf-16" 0.69] 
;; -> "iso-8859-15"
(defn accept-charset [accept-charset-header allowed-charsets]
  (let [charset-and-quality-strings (string/split accept-charset-header #"\s*,\s*") 
	charset-and-qualities (parse-caqs charset-and-quality-strings)]))

(accept-charset "iso-8859-15;q=1, utf-8;q=0.8; utf-16;q=0.6; iso-8859-1;q=0.8" "utf-8")

