(ns liberator.graph)

(defn extract
  ([_ name then else] [name then else])
  ([_ name test then else] [name then else]))

(defn clean-id [str]
  (clojure.string/replace str #"[^a-zA-Z0-9_]+" ""))

(defn to-graph [[& args]]
  (condp = (first args)
    'defdecision
    (let [[name then else] (apply extract args)]
      (format (str "\"%s\" [id = \"%s\"] \n "
                   "\"%s\" -> \"%s\" [label = \"true\",  id = \"%s\"] \n"
                   "\"%s\" -> \"%s\" [label = \"false\", id = \"%s\"]\n")
              name (clean-id name)
              name then (clean-id (str name "_" then))
              name else (clean-id (str name "_" else))))
    'defaction
    (let [[_ name then] args]
      (format (str "\"%s\"[shape=\"ellipse\" id = \"%s\"];\n"
                   "\"%s\"-> \"%s\" [id = \"%s\"] \n")
              name (clean-id name)
              name then (clean-id (str name "_" then))))
    'defhandler
    (let [[_ name status message] args
          color (cond
                 (>= status 500) "#e31a1c"
                 (>= status 400) "#fb9a99"
                 (>= status 300) "#fbdf6f"
                 (>= status 200) "#b2df8a"
                 (>= status 100) "#a6cee3"
                 :else "#ffffff")]
      (format "\"%s\"[id=\"%s\" label=\"%s\\n%s\" style=\"filled\" fillcolor=\"%s\"];\n"
              name (clean-id name) status (clojure.string/replace name #"^handle-" "") color))
    nil))

(defn rank-max [names]
  (str "subgraph {\nrank=max;\n"
       (apply str (interpose "-> \n" (map #(format "\"%s\"" %) names)))
       ";\n}\n"))

(defn rank-same [names]
  (str "subgraph {\nrank=same;\n"
       (apply str (interpose ";\n" (map #(format "\"%s\"" %) names)))
       ";\n}\n"))

(defn rank-handler-groups [handlers]
  (->> handlers
       (group-by (fn [[name status]] (int (/ status 100))))
       vals
       (map (fn [sg] (map first sg)))
       (map rank-same)
       (apply str)
       ))

(defn parse-source-definitions []
  (let [nodes (let [pr (java.io.PushbackReader.
                        (clojure.java.io/reader "src/liberator/core.clj"))
                    eof (Object.)]
                (take-while #(not= eof %) (repeatedly #(read pr false eof))))
        decisions (->> nodes
                      (filter #(= 'defdecision (first %)))
                      (map second))
        handlers (->> nodes
                      (filter #(= 'defhandler (first %)))
                      (map (fn [[_ name status _]] [name status])))
        actions (->> nodes
                      (filter #(= 'defaction (first %)))
                      (map second))]
    {:nodes nodes
     :decisions decisions
     :handlers handlers
     :actions actions}))

(defn generate-graph-dot []
  (let [{:keys [nodes handlers actions]} (parse-source-definitions)]
    (->> nodes
         (map to-graph)
         (filter identity)
         (concat (rank-handler-groups handlers))
         (concat (rank-same (remove #{'initialize-context} actions)))
         (apply str)
         (format (str "digraph{\nid=\"trace\"; size=\"1000,1000\"; page=\"1000,1000\";\n\n"
                      "edge[fontname=\"sans-serif\"]\n"
                      "node[shape=\"box\", splines=ortho fontname=\"sans-serif\"]\n\n"
                      "%s"
                      "\n}")))))

(defn generate-dot-file [f]
  (spit f (generate-graph-dot)))

