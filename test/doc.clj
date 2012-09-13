(ns doc)

(defn destructure 
  ([_ name then else] [name then else])
  ([_ name test then else] [name then else]))

(defn to-graph [[& args]]
  (condp = (first args)
    'defdecision
    (let [[name then else] (apply destructure args)]
      (format (str "\"%s\" -> \"%s\" [label = \"true\"] \n"
                   "\"%s\" -> \"%s\" [label=\"false\"]\n")
              name then name else))
    'defaction
    (let [[_ name then] args]
      (format "\"%s\"[shape=\"ellipse\"];\n\"%s\"-> \"%s\"\n" name name then))
    'defhandler
    (let [[_ name status message] args
          color (cond
                 (>= status 500) "#e31a1c"
                 (>= status 400) "#fb9a99"
                 (>= status 300) "#fbdf6f"
                 (>= status 200) "#b2df8a"
                 (>= status 100) "#a6cee3"
                 :else "#ffffff")]
      (format "\"%s\"[label=\"%s\\n%s\" style=\"filled\" fillcolor=\"%s\"];\n" name status name color))
    nil)
  
  )

(defn rank-max [names]
  (str "subgraph {\nrank=max;\n"
       (apply str (interpose ";\n" (map #(format "\"%s\"" %) names)))
       "\n}\n"))

(defn rank-same [names]
  (str "subgraph {\nrank=same;\n"
       (apply str (interpose ";\n" (map #(format "\"%s\"" %) names)))
       "\n}\n"))

(defn generate-graph []
  (let [nodes (let [pr (java.io.PushbackReader.
                        (clojure.java.io/reader "src/liberator/core.clj"))
                    eof (Object.)]
                (take-while #(not= eof %) (repeatedly #(read pr false eof))))
        handlers (->> nodes
                      (filter #(= 'defhandler (first %)))
                      (map second))
        actions (->> nodes
                      (filter #(= 'defaction (first %)))
                      (map second))]
    (->> nodes
         (map to-graph)
         (filter identity)
         (concat (rank-max handlers))
         (concat (rank-same actions))
         (apply str)
         (format "digraph {\nnode[shape=\"box\", splines=ortho]\n\"start\"[shape=circle];\n\"start\" -> \"service-available?\"\n%s\n}"))))
