(ns good-guesser.square-grid)

(def pi 3.14159265)

(defn cos [x]
  (Math/cos x))

(defn sin [x]
  (Math/sin x))

(defn round [x]
  (int (Math/round (double x))))

(defn square-grid []
  (map (partial apply str)
       (let [wid 50
             ht  30
             res 20]
         (reduce (fn [acc item]
                   (let [size (+ 2 (rand-int 5))
                         x    (+ (* 1.5 size) (rand-int (- wid (* 3 size))))
                         y    (+ (* 1.5 size) (rand-int (- ht (* 3 size))))
                         rot  (* pi 2 (rand))
                         dx  (sin rot)
                         dy  (cos rot)
                         idx (- dy)
                         idy dx
                         axes-range (map (partial * size (/ 1 res)) (range (- res) res))]
                     (reduce (fn [acc2 [xx yy :as item2]]
                               (assoc-in acc2 [yy xx] \*))
                             acc
                             (for [xx axes-range
                                   yy axes-range]
                               [(int (+ x (* dx xx) (* idx yy))) (int (+ y (* dy xx) (* idy yy)))]))))
                 (vec (repeat ht (vec (repeat wid \space))))
                 (range (inc (rand-int 10)))))))

#_(defn run-validation [num]
    (reset! gg/cache {})
    (io/delete-file "num-squares-validation.gg" true)
    (dotimes [_ num]
      (let [num-squares (inc (rand-int 10))]
        (good-guesser :num-squares-validation (square-grid num-squares) count-pixels concave-pixels :actual-value num-squares)))
    (doall (for [_ (range num)]
             (let [num-squares (inc (rand-int 10))]
               (let [guess (max 1 (min 10 (round (good-guesser :num-squares-validation (square-grid num-squares) count-pixels concave-pixels))))]
                 [num-squares guess])))))

;;(def result (time (run-validation 100)))
;;(count (filter (fn [[a b]] (<= (ut/abs (- a b)) 1)) result))
;;(count (filter (partial apply =) result))
