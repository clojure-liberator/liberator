(ns test-handler-context
  (:use
   midje.sweet
   [ring.mock.request :only [request header]]
   [compojure.core :only [context ANY]]
   [liberator.core :only [defresource resource]]))

(defn ^:private negotiate [header-key resource-key representation-key available accepted]
  (-> (request :get "")
      (header header-key accepted)
      ((resource resource-key available
                 :handle-ok (fn [{representation :representation}]
                              (representation representation-key))))
      ((fn [resp] (if (= 200 (:status resp))
                     (:body resp)
                     (:status resp))))))

;.;. [36mWORK TO DO[0m "Single header negotiation - Charset negotitation" at (NO_SOURCE_FILE:1)
;.;. 
;.;. [31mFAIL[0m "Single header negotiation - Encoding negotitation" at (NO_SOURCE_FILE:1)
;.;. With table substitutions: [?available []
;.;.                            ?accepted "en"
;.;.                            ?negotiated 406]
;.;.     Expected: 406
;.;.       Actual: nil
;.;. 
;.;. [31mFAIL[0m "Single header negotiation - Encoding negotitation" at (NO_SOURCE_FILE:1)
;.;. With table substitutions: [?available ["en"]
;.;.                            ?accepted "en"
;.;.                            ?negotiated "en"]
;.;.     Expected: "en"
;.;.       Actual: nil
;.;. 
;.;. [31mFAIL[0m "Single header negotiation - Encoding negotitation" at (NO_SOURCE_FILE:1)
;.;. With table substitutions: [?available ["en" "de"]
;.;.                            ?accepted "de"
;.;.                            ?negotiated "de"]
;.;.     Expected: "de"
;.;.       Actual: nil
;.;. 
;.;. [31mFAIL[0m "Single header negotiation - Encoding negotitation" at (NO_SOURCE_FILE:1)
;.;. With table substitutions: [?available ["en" "de"]
;.;.                            ?accepted "de,fr"
;.;.                            ?negotiated "de"]
;.;.     Expected: "de"
;.;.       Actual: nil
;.;. 
;.;. [31mFAIL[0m "Single header negotiation - Encoding negotitation" at (NO_SOURCE_FILE:1)
;.;. With table substitutions: [?available ["en" "de"]
;.;.                            ?accepted "de;q=0.1,en"
;.;.                            ?negotiated "en"]
;.;.     Expected: "en"
;.;.       Actual: nil
;.;. 
;.;. [31mFAIL[0m "Single header negotiation - Encoding negotitation" at (NO_SOURCE_FILE:1)
;.;. With table substitutions: [?available ["en" "de"]
;.;.                            ?accepted "de;q=0.3,en;q=0.2;fr=0.9;la"
;.;.                            ?negotiated "de"]
;.;.     Expected: "de"
;.;.       Actual: nil
(facts "Single header negotiation"
  (facts "Media type negotitation"
    (tabular
     (negotiate "Accept" :available-media-types :media-type ?available ?accepted) => ?negotiated
     ?available ?accepted ?negotiated
     []                         "text/html" 406
     ["text/html"]              "text/html" "text/html"
     ["text/html" "text/plain"] "text/html" "text/html"
     ["text/html" "text/plain"] "text/html,text/foo" "text/html"
     ["text/html" "text/plain"] "text/html;q=0.1,text/plain" "text/plain"
     ["text/html" "text/plain"] "text/html;q=0.3,text/plain;q=0.2" "text/html"))
  
  (facts "Language negotitation"
    (tabular
     (negotiate "Accept-Language" :available-languages :language ?available ?accepted) => ?negotiated
     ?available ?accepted ?negotiated
     []          "en" 406
     ["en"]      "en" "en"
     ["en" "de"] "de" "de"
     ["en" "de"] "de,fr" "de"
     ["en" "de"] "de;q=0.1,en" "en"
     ["en" "de"] "de;q=0.3,en;q=0.2;fr=0.9;la" "de"))

  (future-facts "Charset negotitation"
    (tabular
     (negotiate "Accept-Charset" :available-charsets :charset ?available ?accepted) => ?negotiated
     ?available ?accepted ?negotiated
     []          "ascii" 406
     ["ascii"]     "ascii" "ascii"
     ["ascii" "utf-8"] "utf-8" "utf-8"
     ["ascii" "utf-8"] "utf-8,fr" "utf-8"
     ["ascii" "utf-8"] "utf-8;q=0.1,ascii" "ascii"
     ["ascii" "utf-8"] "utf-8;q=0.3,ascii;q=0.2;iso8859-1=0.9;iso-8859-2" "utf-8"))
  
  (future-facts "Encoding negotitation"
    (tabular
     (negotiate "Accept-Encoding" :available-encodings :encoding ?available ?accepted) => ?negotiated
     ?available ?accepted ?negotiated
     []          "gzip" 406
     ["gzip"]      "gzip" "gzip"
     ["gzip" "compress"] "compress" "compress"
     ["gzip" "compress"] "compress,fr" "compress"
     ["gzip" "compress"] "compress;q=0.1,gzip" "gzip"
     ["gzip" "compress"] "compress;q=0.3,gzip;q=0.2;fr=0.9;la" "compress")))