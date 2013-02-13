(ns test-handler-context
  (:use
   midje.sweet
   [ring.mock.request :only [request header]]
   [liberator.core :only [defresource resource]]
   [liberator.representation :only [ring-response]]))

(defn ^:private negotiate [header-key resource-key representation-key available accepted]
  (-> (request :get "")
      (#(if accepted (header % header-key accepted) %))
      ((resource resource-key available
                 :handle-ok (fn [{representation :representation}]
                              (representation representation-key))))
      ((fn [resp] (if (= 200 (:status resp))
                    (:body resp)
                    (:status resp))))))

(facts "Single header negotiation"
  (facts "Media type negotitation"
    (tabular
     (negotiate "Accept" :available-media-types :media-type ?available ?accepted) => ?negotiated
     ?available ?accepted ?negotiated
     []                         "text/html"                        406
     ["text/html" "text/plain"] nil                                "text/html"
     ["text/html"]              "text/html"                        "text/html"
     ["text/html" "text/plain"] "text/html"                        "text/html"
     ["text/html" "text/plain"] "text/html,text/foo"               "text/html"
     ["text/html" "text/plain"] "text/html;q=0.1,text/plain"       "text/plain"
     ["text/html" "text/plain"] "text/html;q=0.3,text/plain;q=0.2" "text/html"))

  (facts "Language negotitation"
   (facts "Only primary tag"
     (tabular
      (negotiate "Accept-Language" :available-languages :language ?available ?accepted) => ?negotiated
      ?available ?accepted ?negotiated
      []          "en" 406
      ["en"]      "en" "en"
      ["en" "de"] "de" "de"
      ["en" "de"] "de,fr" "de"
      ["en" "de"] "de;q=0.1,en" "en"
      ["en" "de"] "de;q=0.3,en;q=0.2;fr=0.9;la" "de"
      ["en" "de"] "de;q=0.3,en;q=0.2;fr=0.9;la" "de"))

   (future-facts "with subtag"
     (tabular 
      (negotiate "Accept-Language" :available-languages :language ?available ?accepted) => ?negotiated
      ?available ?accepted ?negotiated
      []          "en-GB" 406
      ["en"]      "en-GB" "en"
      ["en-GB" "de"] "de" "de"
      ["en" "de-AT"] "de,fr" "de"
      ["en-US" "de"] "de;q=0.1,en" "en"
      ["en-US" "en-GB"] "en-US" "en-US"
      ["en-US" "en-GB"] "en" "en")))
  

  (facts "Charset negotitation"
    (tabular
     (negotiate "Accept-Charset" :available-charsets :charset ?available ?accepted) => ?negotiated
     ?available ?accepted ?negotiated
     []          "ascii" 406
     ["utf-8"]     "ascii" 406
     ["utf-8"]     "utf-8" "utf-8"
     ["ascii" "utf-8"] "utf-8" "utf-8"
     ["ascii" "utf-8"] "utf-8,fr" "utf-8"
     ["ascii" "utf-8"] "ascii;q=0.1,utf-8" "utf-8"
     ["ascii" "utf-8"] "utf-8;q=0.3,ascii;q=0.2;iso8859-1=0.9;iso-8859-2" "utf-8"))
  
  (facts "Encoding negotitation"
    (tabular
     (negotiate "Accept-Encoding" :available-encodings :encoding ?available ?accepted) => ?negotiated
     ?available ?accepted ?negotiated
     []            "gzip" "identity"
     ["gzip"]      "gzip" "gzip"
     ["compress"]  "gzip" "identity"
     ["gzip" "compress"] "compress" "compress"
     ["gzip" "compress"] "compress,fr" "compress"
     ["gzip" "compress"] "compress;q=0.1,gzip" "gzip"
     ["gzip" "compress"] "compress;q=0.3,gzip;q=0.2;fr=0.9;la" "compress")))