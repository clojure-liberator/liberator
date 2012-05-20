(ns examples.olympics
  (:use [clojure.string :only [split]]
        [clojure.java.io :only [file reader]]))

;; http://download.freebase.com/datadumps/latest/browse/olympics/olympic_games.tsv

(def dataset
  (let [[header & rows]
        (->> "Downloads/olympic_games.tsv"
             (file (System/getProperty "user.home"))
             reader line-seq
             (map #(split % #"\t")))]
    (map #(zipmap header %) rows)))

(defn get-olympic-games-index []
  (->> dataset
       (map #(select-keys % ["id" "name" "host_city" "mascot"]))
       (sort-by #(get % "name"))))

(defn get-olympic-games [id]
  (-> (filter #(= (get % "id") id) dataset)
      first
      (update-in ["competitions"] #(split % #",") ; 'competitions' is delimited with hyphens
       )))
