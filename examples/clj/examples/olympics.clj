(ns examples.olympics
  (:use [clojure.string :only [split]]
        [clojure.java.io :only [file reader]]))

;; http://download.freebase.com/datadumps/latest/browse/olympics/olympic_games.tsv

(defn parse-dataset [f]
  (let [[header & rows]
        (->> f
             reader line-seq
             (map #(split % #"\t")))]
    (map #(zipmap header %) rows)))

(def dataset
  (parse-dataset (file "examples/data/olympic_games.tsv")))

(defn get-olympic-games-index []
  (->> dataset
       (map #(select-keys % ["id" "name" "host_city" "mascot"]))
       (sort-by #(get % "name"))))





(defn get-olympic-games [id]
  (-> (filter #(= (get % "id") id) dataset)
      first
      (update-in ["competitions"] #(split % #","))))

(def athletes
  (parse-dataset (file "examples/data/olympic_athlete.tsv")))

(defn get-athletes-sample []
  (map #(get % "name") (map #(select-keys % ["name"]) (take 10 athletes))))

;;(get-athletes-sample)


;; (spit (clojure.java.io/file "/tmp/foo.clj") (with-out-str (doall (map prn (map #(select-keys % ["name"]) (take 10 athletes))))))

