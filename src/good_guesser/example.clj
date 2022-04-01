(ns good-guesser.example
  (:require [good-guesser.square-grid :refer [square-grid]]
            [good-guesser.good-guesser :refer [good-guesser]]
            [clojure.pprint :refer [pprint]]))

(defn count-pixels [bmp]
  (count (filter #{\*} (apply concat bmp))))

(defn concave-pixels [bmp]
  (apply +
         (map (fn [& rows]
                (let [triples (apply map vector rows)]
                  (apply +
                         (map (fn [& k]
                                (if (and (= (get-in (vec k) [1 1]) \space)
                                         (> (count-pixels k) 4))
                                  1
                                  0))
                              triples
                              (rest triples)
                              (rest (rest triples))))))
              bmp
              (rest bmp)
              (rest (rest bmp)))))

(defn num-squares [bmp]
  (pprint bmp)
  (good-guesser :num-squares bmp count-pixels concave-pixels))
