(ns liberator.conneg
  (:require [clojure.string :as string]
            [liberator.util :refer [protocol-exception]]))

;;; TODO: sort by level for text/html. Maybe also sort by charset.
;;; Finally, compare by precedence rules:
;;;   1. text/html;level=1
;;;   2. text/html
;;;   3. text/*
;;;   4. */*
;;;

(def accept-fragment-re
  #"^\s*(\*|[^()<>@,;:\"/\[\]?={}         ]+)/(\*|[^()<>@,;:\"/\[\]?={}         ]+)$")

(def accept-fragment-param-re
  #"([^()<>@,;:\"/\[\]?={} 	]+)=([^()<>@,;:\"/\[\]?={} 	]+|\"[^\"]*\")$")

(defn- clamp [minimum maximum val]
  (->> val
    (min maximum)
    (max minimum)))

(defn- parse-q [^String str]
  (Double/parseDouble str))

(defn- assoc-param [coll n v]
  (try
    (assoc coll
           (keyword n)
           (if (= "q" n)
             (clamp 0 1 (parse-q v))
             v))
    (catch Throwable e
      coll)))

(defn params->map [params]
  (loop
    [p (first params)
     ps (rest params)
     acc {}]
    (let [x (when p
              (rest (re-matches accept-fragment-param-re p)))
          accumulated (if (= 2 (count x))
                        (assoc-param acc (first x) (second x))
                        acc)]
      (if (empty? ps)
        accumulated
        (recur
          (first ps)
          (rest ps)
          accumulated)))))

(defn accept-fragment
  "Take something like
    \"text/html\"
  or
    \"image/*; q=0.8\"
  and return a map like
    {:type [\"image\" \"*\"]
      :q 0.8}

  If the fragment is invalid, nil is returned."

  ([f]
   (let [parts (string/split f #"\s*;\s*")]
     (when (not (empty? parts))
       ;; First part will be a type.
       (let [type-str (first parts)
             type-pair (rest (re-matches accept-fragment-re type-str))]
         (when type-pair
           (assoc
             (params->map (rest parts))
             :type type-pair)))))))

(defn acceptable-type
  "Compare two type pairs. If the pairing is acceptable,
   return the most specific.
  E.g., for

    [\"text\" \"plain\"] [\"*\" \"*\"]

  returns

    [\"text\" \"plain\"]."
  [type-pair acceptable-pair]

  (cond
    (or (= type-pair acceptable-pair)
        (= ["*" "*"] acceptable-pair))
    type-pair

    (= ["*" "*"] type-pair)
    acceptable-pair

    true
    ;; Otherwise, maybe one has a star.
    (let [[tmaj tmin] type-pair
          [amaj amin] acceptable-pair]
      (when (= tmaj amaj)
        (cond
          (= "*" tmin)
          acceptable-pair

          (= "*" amin)
          type-pair)))))

(defn assoc-server-weight-fn [allowed-types]
  (let [server-fragments (map accept-fragment allowed-types)]
    (fn [accept-fragment]
      (if-let [sq (:q (first (filter #(acceptable-type (:type accept-fragment) (:type %)) server-fragments)))]
        (assoc accept-fragment :sq sq)
        accept-fragment))))

(defn sorted-accept [accepts-header allowed-types]
  (reverse
   (sort-by (juxt #(get %1 :q 1) #(get %1 :sq 1))
            (map (assoc-server-weight-fn allowed-types)
                 (map accept-fragment
                      (string/split accepts-header #"[\s\n\r]*,[\s\n\r]*"))))))

(defn allowed-types-filter [allowed-types]
  (fn [accept]
    (some (partial acceptable-type accept)
          allowed-types)))

(defn- enpair
  "Ensure that a collection of types is a collection of pairs."
  [x]
  (filter identity
          (map (fn [y] (if (string? y)
                         (:type (accept-fragment y))
                         y))
               x)))

(defn stringify [type]
  (reduce str (interpose "/" type)))

(defn best-allowed-content-type
  "Return the first type in the Accept header that is acceptable.
  allowed-types is a set containing pairs (e.g., [\"text\" \"*\"])
  or strings (e.g., \"text/plain\").

  Definition of \"acceptable\":
  An Accept header fragment of \"text/*\" is acceptable when allowing
  \"text/plain\".
  An Accept header fragment of \"text/plain\" is acceptable when allowing
  \"text/*\"."

  ([accepts-header]
   (best-allowed-content-type accepts-header true))
  ([accepts-header allowed-types]    ; Set of strings or pairs. true/nil/:all for any.
     (let [sorted (map :type (sorted-accept accepts-header allowed-types))]
       (cond
        (contains? #{:all nil true} allowed-types)
        (first sorted)

        (fn? allowed-types)
        (some (enpair allowed-types) sorted)

        :otherwise
        (some (allowed-types-filter (enpair allowed-types)) sorted)))))

(defn split-qval [caq]
  (let [[charset & params] (string/split caq #"[\s\r\n]*;[\s\r\n]*")
        q (first (reverse (sort (filter (comp not nil?)
                                        (map #(let [[param value] (string/split % #"[\s\r\n]*=")]
                                                (if (= "q" param) (Float/parseFloat value)))
                                             params)))))]
    (when (and
           (not (nil? q))
           (> q 1.0))
      (throw (protocol-exception "Quality value of header exceeds 1")))
    (when (and
           (not (nil? q))
           (< q 0))
      (throw (protocol-exception "Quality value of header is less than 0")))
    [charset (or q 1)]))

(defn parse-accepts-header [accepts-header]
  (->> (string/split accepts-header #"[\s\r\n]*,[\s\r\n]*")
       (map split-qval)
       (into {})))

(defn select-best [candidates score-fn]
  (->> candidates
       (map (juxt identity #(or (score-fn %) 0)))
       (sort-by second)
       ;; If a parameter has a quality value of 0, then content with
       ;; this parameter is `not acceptable' for the client
       (remove #(zero? (second %)))
       reverse
       (map first) ; extract winning option
       first))


;; TODO Add tracing

(defn best-allowed-charset [accepts-header available]
  (let [accepts (->> (string/split (string/lower-case accepts-header) #"[\s\r\n]*,[\s\r\n]*")
                     (map split-qval)
                     (into {}))]
    (select-best available
                 (fn [charset]
                   (let [charset (string/lower-case charset)]
                     (or (get accepts charset)
                         (get accepts "*")
                         ;; "except for ISO-8859-1, which gets a quality
                         ;; value of 1 if not explicitly mentioned"
                         (if (= charset "iso-8859-1") 1 0)))))))

(defn best-allowed-encoding [accepts-header available]
  (let [accepts (->> (string/split accepts-header #"[\s\r\n]*,[\s\r\n]*")
                     (map split-qval)
                     (into {}))]
    (or
     (select-best (concat available ["identity"])
                  (fn [encoding]
                    (or (get accepts encoding)
                        (get accepts "*"))))


     ;; The "identity" content-coding is always acceptable, unless
     ;; specifically refused because the Accept-Encoding field includes
     ;; "identity;q=0", or because the field includes "*;q=0" and does not
     ;; explicitly include the "identity" content-coding. If the
     ;; Accept-Encoding field-value is empty, then only the "identity"
     ;; encoding is acceptable.
     (if-not (or (zero? (get accepts "identity" 1))
                 (and (zero? (get accepts "*" 1))
                      (not (contains? accepts "identity"))))
       "identity"))))

;; 3.10 Language Tags (p28)
;; language-tag  = primary-tag *( "-" subtag )
;; primary-tag   = 1*8ALPHA
;; subtag        = 1*8ALPHA
(defn remove-last-subtag [langtag]
  (->> (string/split langtag #"-") ; split into tags
       butlast ; remote the last subtag
       (interpose "-") (reduce str))) ; recompose


;; TODO What if no languages available?
;;    "If no Content-Language is specified, the default is that the content is intended for all language audiences. This might mean that the sender does not consider it to be specific to any natural language, or that the sender does not know for which language it is intended."

(defn best-allowed-language [accepts-header available]
  (let [accepts (->> (string/split accepts-header #"[\s\r\n]*,[\s\r\n]*")
                     (map split-qval)
                     (into {}))

        score (fn [langtag]
                (or
                 ;; "A language-range matches a language-tag if it exactly equals the tag"
                 (get accepts langtag)
                 (->> langtag
                      ;; "The language quality factor assigned to a
                      ;; language-tag by the Accept-Language field is
                      ;; the quality value of the longest language-range
                      ;; in the field that matches the language-tag"
                      (iterate remove-last-subtag) (take-while (comp not empty?))
                      (map #(get accepts %)) ; any score?
                      (filter identity)
                      first)            ; partial match
                 ;; "If no Content-Language is specified, the default is
                 ;; that the content is intended for all language
                 ;; audiences. This might mean that the sender does not
                 ;; consider it to be specific to any natural language,
                 ;; or that the sender does not know for which language
                 ;; it is intended."
                 (if (= "*" langtag) 0.01)
                 0))]
    (or
     (select-best available (fn [option]
                              (cond
                               (string? option) (score option) ; single langtag
                               ;; "Multiple languages MAY be
                               ;; listed for content that is intended for multiple audiences. For
                               ;; example, a rendition of the "Treaty of Waitangi," presented
                               ;; simultaneously in the original Maori and English versions, would
                               ;; call for"
                               ;;
                               ;; Content-Language: mi, en
                               (coll? option) (apply max (map score option))))))))


;; TODO Have we considered the case where no accept-language tag is provided? (rfc 2616 is clear about this)
;; TODO As above but what about no accept-charset, no accept-encoding, no accept?
