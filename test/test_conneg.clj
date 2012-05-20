(ns test-conneg
  (:require [clojure.string :as string])
  (:use
   compojure-rest.conneg
   midje.sweet))

(facts
 
 (best-allowed-charset "iso-8859-5, unicode-1-1;q=0.8" #{"iso-8859-5" "unicode-1-1"}) => "iso-8859-5"

 (best-allowed-charset "iso-8859-15;q=1, utf-8;q=0.8, utf-16;q=0.6, iso-8859-1;q=0.8" #{"iso-8859-15" "utf-16"})
 => "iso-8859-15"

 ;; p102: "The special value \"*\", if present in the Accept-Charset
 ;; field, matches every character set (including ISO-8859-1) which is
 ;; not mentioned elsewhere in the Accept-Charset field. If no \"*\"
 ;; is present in an Accept-Charset field, then all character sets not
 ;; explicitly mentioned get a quality value of 0, except for
 ;; ISO-8859-1, which gets a quality value of 1 if not explicitly
 ;; mentioned."

 ;; iso-8859-1 gets the highest score because there is no * so it gets a quality value of 1
 (best-allowed-charset "iso-8859-15;q=0.6, utf-16;q=0.9" #{"iso-8859-1" "iso-8859-15" "utf-16"})
 => "iso-8859-1"

 ;; utf-16 gets the highest score because there is no * but iso-8859-1 is mentioned at a lower score
 (best-allowed-charset "iso-8859-15;q=0.6, utf-16;q=0.9, iso-8859-1;q=0.1" #{"iso-8859-1" "iso-8859-15" "utf-16"})
 => "utf-16"

 (best-allowed-charset "iso-8859-15;q=0.6, *;q=0.8, utf-16;q=0.9" #{"iso-8859-15" "utf-16"})
 => "utf-16"

 ;; ASCII should be returned because it matches *, which gives it a 0.8 score, higher than iso-8859-15
 (best-allowed-charset "iso-8859-15;q=0.6, *;q=0.8, utf-16;q=0.9" #{"iso-8859-15" "ASCII"})
 => "ASCII"

 ;; Nothing is returned because ASCII is gets a score of 0
 (best-allowed-charset "iso-8859-15;q=0.6, utf-16;q=0.9" #{"ASCII"})
 => nil

 ;; p20: "HTTP character sets are identified by case-insensitive
 ;; tokens. The complete set of tokens is defined by the IANA
 ;; Character Set registry"
 ;; TODO Test for case-insensitivity p20, it's possible that compojure-rest or ring will be downcasing anyway, check this
 )

(facts
 (best-allowed-encoding "compress;q=0.4, gzip;q=0.2" #{"compress" "gzip"}) => "compress"
 (best-allowed-encoding "compress;q=0.4, gzip;q=0.8" #{"compress" "gzip"}) => "gzip"
 ;; TODO Not sure about this one
 (best-allowed-encoding "ogzip;q=1.0, identity; q=0.5, *;q=0" #{"foo"}) => "gzip"
 )

;; Language negotiation (14.4)
(facts

 ;; 14.4 Accept-Language

 ;; 14.12 Content-Language (p118)

 ;; p103 :-
 ;; Accept-Language: da, en-gb;q=0.8, en;q=0.7
 ;;
 ;; would mean: "I prefer Danish, but will accept British English and
 ;; other types of English."  A language-range matches a language-tag if
 ;; it exactly equals the tag...

 (best-allowed-language "da, en-gb;q=0.8, en;q=0.7" #{"da" "en-gb" "en"}) => "da"
 (best-allowed-language "da, en-gb;q=0.8, en;q=0.7" #{"en-gb" "en"}) => "en-gb"

 ;; ... or if it exactly equals a prefix of the tag such that the first tag
 ;; character following the prefix is "-".
 (best-allowed-language "da, en-gb;q=0.8, en;q=0.7" #{"en"}) => "en"
 (best-allowed-language "da, en-gb;q=0.8" #{"en-cockney"}) => nil
 (best-allowed-language "da, en-gb;q=0.8, en;q=0.7" #{"en-cockney"}) => "en-cockney" ; at q=0.7
 
 ;; TODO
 ;; The special range "*", if present in the Accept-Language field,
 ;; matches every tag not matched by any other range present in the
 ;; Accept-Language field.


 ;; Multiple languages MAY be
 ;; listed for content that is intended for multiple audiences. For
 ;; example, a rendition of the "Treaty of Waitangi," presented
 ;; simultaneously in the original Maori and English versions, would
 ;; call for
 ;;
 ;; Content-Language: mi, en
 (best-allowed-language "da, mi;q=0.8" #{["mi" "en"]}) => ["mi" "en"]
 )
