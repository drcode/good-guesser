(ns good-guesser.good-guesser
  (:require [incanter.stats :as is]
            [incanter.core :as ic]
            [fbc-utils.core :as ut]
            [clojure.string :as st]
            [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [fbc-utils.debug :refer [let-dbg]]
            [clojure.java.io :as io]))

(defn add-bias [x]
  (ic/bind-columns (repeat (ic/nrow x) 1) x))

(defn normal-equation [x y]
  (let [xtx  (ic/mmult (ic/trans x) x)
        xtxi (ic/solve xtx)
        xty  (ic/mmult (ic/trans x) y)]
    (when xtxi
      (ic/mmult xtxi xty))))

(defn estimate [[bias & more :as regression] xs] ;given a multiple regression, estimate y based on the xs
  (apply + bias (map * more xs)))

(defn multiple-regression [xs-coll y-coll]
  (assert (= (count xs-coll) (count y-coll)))
  (or (normal-equation (add-bias (ic/matrix xs-coll)) y-coll) [0]))

(defn file-name [nam]
  (str (name nam) ".gg"))

(defn strip-terminal-newline [s]
  (if (and (pos? (count s)) (= (last s) \newline))
    (apply str (butlast s))
    s))

;;(multiple-regression [[1 4] [2 6] [3 12]] [1 2 8])
;;(multiple-regression [[1 4] [2 6]] [7 3])
;;(multiple-regression [] [])
;;(multiple-regression [[373 1 0] [423 4 0] [741 13 0] [463 11 0]] [4 6 14 10])

(def cache (atom {}))

(def max-unlabeled-examples 20)

(defn set-flags [{:keys [cache-entry
                         params]
                  :as   bundle}]
  (let [{:keys             [last-modified
                            regression-out-of-date
                            unlabeled-num
                            visited]
         input-funs-cached :input-funs
         visualizer-cached :visualizer} cache-entry
        {:keys [nam
                input-funs
                visualizer
                actual-value
                raw-input]}             params
        fname                           (file-name nam)
        file-exists                     (ut/exists fname)
        file-modified                   (and file-exists (not= (.lastModified (io/file fname)) last-modified))
        input-funs-changed              (or (not= input-funs input-funs-cached) (not= visualizer visualizer-cached))
        old-examples-out-of-date        (or file-modified input-funs-changed)
        regression-out-of-date          (or regression-out-of-date old-examples-out-of-date input-funs-changed actual-value)
        old-guesses-out-of-date         (and regression-out-of-date (pos? unlabeled-num))
        save-new-example                (or (and (< unlabeled-num max-unlabeled-examples) (not (visited raw-input))) actual-value)
        regression-needed               (or (not actual-value) (pos? unlabeled-num))
        file-out-of-date                (or (not file-exists) file-modified old-guesses-out-of-date)]
    (merge (assoc-in bundle [:cache-entry :regression-out-of-date] regression-out-of-date)
           {:file-out-of-date         file-out-of-date
            :file-needs-appending     (and (not file-out-of-date) save-new-example)
            :save-new-example         save-new-example
            :old-examples-out-of-date old-examples-out-of-date
            :old-guesses-out-of-date  old-guesses-out-of-date
            :regression-needed        regression-needed})))

(defn load-examples [{:keys [old-examples-out-of-date
                             params]
                      :as   bundle}]
  (if old-examples-out-of-date
    (let [{:keys [nam]} params
          fname         (file-name nam)
          examples      (ut/forv [[k v] (partition 2 (if (ut/exists fname)
                                                       (edn/read-string (str \[ (slurp fname) \]))
                                                       []))]
                                 (let [labeled (not (symbol? v))]
                                   {:input   k
                                    :labeled labeled
                                    :output  (if labeled
                                               v
                                               (edn/read-string (apply str (rest (name v)))))}))
          labeled       (filter :labeled examples)]
      (update bundle :cache-entry merge
              {:examples      examples
               :visited       (set (map :input examples))
               :unlabeled-num (- (count examples) (count labeled))}))
    bundle))

(defn add-new-example [{:keys [cache-entry
                               params
                               save-new-example]
                        :as   bundle}]
  (let [{:keys [examples
                visited
                unlabeled-num]}  cache-entry
        {:keys [actual-value
                raw-input]} params]
    (if save-new-example
      (update bundle
              :cache-entry
              merge
              {:examples      (conj examples
                                    {:input   raw-input
                                     :labeled (boolean actual-value)
                                     :output  actual-value})
               :visited       (conj visited raw-input)
               :unlabeled-num (cond-> unlabeled-num
                                (not actual-value) inc)})
      bundle)))

(defn apply-inputs [{:keys [params]
                     :as bundle}
                    raw-input]
  (let [{:keys [input-funs]} params]
    (mapv apply input-funs (repeat [raw-input]))))

(defn run-regression [{:keys [cache-entry
                              regression-needed
                              params]
                       :as   bundle}]
  (let [{:keys [verbose]}                params
        {:keys [examples
                regression-out-of-date]} cache-entry]
    (if (and regression-needed regression-out-of-date)
      (let [labeled    (filter :labeled examples)
            xs-coll    (for [{:keys [input]
                              :as   example} labeled]
                         (apply-inputs bundle input))
            y-coll     (mapv :output labeled)
            regression (multiple-regression xs-coll y-coll)]
        (when verbose
          (println "New regression: " regression))
        (update bundle
                :cache-entry
                merge
                {:regression             regression
                 :regression-out-of-date false}))
      bundle)))

(defn calculate-old-guesses [{:keys [cache-entry
                                     old-guesses-out-of-date]
                              :as   bundle}]
  (if old-guesses-out-of-date
    (let [{:keys [examples
                  regression]} cache-entry]
      (assoc-in bundle
                [:cache-entry :examples]
                (ut/forv [{:keys [labeled
                                  input]
                           :as   example} examples]
                         (if (not labeled)
                           (assoc example :output (estimate regression (apply-inputs bundle input)))
                           example))))
    bundle))

(defn calculate-new-guess [{:keys [cache-entry
                                   save-new-example
                                   old-guesses-out-of-date
                                   params]
                            :as   bundle}]
  (let [{:keys [actual-value
                raw-input]}  params
        {:keys [examples
                regression]} cache-entry
        result               (cond actual-value                                   actual-value
                                   (and save-new-example old-guesses-out-of-date) (:output (last examples))
                                   :else                                          (estimate regression (apply-inputs bundle raw-input)))
        bundle (if save-new-example
                 (assoc-in bundle [:cache-entry :examples (dec (count examples)) :output] result)
                 bundle)]
    (assoc bundle :result result)))

(defn examples-text [examples]
  (st/join "\n"
           (mapcat (fn [{:keys [input
                                output
                                visualization
                                labeled]
                         :as   example}]
                     `[~(strip-terminal-newline (with-out-str (pp/pprint input)))
                       ~@(when visualization
                           (map (partial str ";;") visualization))
                       ~(if labeled
                          output
                          (symbol (str "?" output)))])
                   examples)))

(defn create-visualizations [{:keys [params
                                     cache-entry
                                     old-guesses-out-of-date
                                     save-new-example]
                              :as bundle}]
  (let [{:keys [examples
                regression]} cache-entry
        {:keys [raw-input
                visualizer]} params
        update-example       (fn [{:keys [input
                                          output
                                          labeled
                                          output]
                                   :as   example}]
                               (assoc example
                                      :visualization
                                      (visualizer input
                                                  (if labeled
                                                    (estimate regression (apply-inputs bundle input))
                                                    output))))]
    (cond (not visualizer)        bundle
          old-guesses-out-of-date (assoc-in bundle [:cache-entry :examples] (mapv update-example examples))
          save-new-example        (update-in bundle [:cache-entry :examples (dec (count examples))] update-example)
          :else                   bundle)))

(defn save-examples [{:keys [cache-entry
                             params
                             file-out-of-date
                             file-needs-appending]
                      :as   bundle}]
  (let [{:keys [nam
                preview]}    params
        {:keys [examples]}   cache-entry
        fname                (file-name nam)
        update-modified-date (fn []
                               (assoc-in bundle [:cache-entry :last-modified] (.lastModified (io/file fname))))]
    (cond preview              (do (println (examples-text examples))
                                   bundle)
          file-out-of-date     (do (spit fname (examples-text examples))
                                   (update-modified-date))
          file-needs-appending (do (spit fname (str "\n" (examples-text [(last examples)])) :append true)
                                   (update-modified-date))
          :else                bundle)))

(defn finalize-cache [{:keys [cache-entry
                              params]
                       :as   bundle}]
  (let [{:keys [input-funs
                visualizer]} params]
    (update bundle
            :cache-entry
            merge
            {:input-funs input-funs
             :visualizer visualizer})))

(defn good-guesser [nam raw-input & args]
  (let [[input-funs optional] (split-with (complement keyword?) args)
        {:keys [preview]
         :as   optional}      (into {}
                                    (map vec (partition 2 optional)))
        _                     (assert (every? #{:preview :actual-value :verbose :visualizer} (keys optional)))
        cache-entry           (or (@cache nam) {:unlabeled-num 0
                                                :visited #{}
                                                :examples []})
        bundle                {:cache-entry cache-entry
                               :params      (merge {:nam        nam
                                                    :raw-input  raw-input
                                                    :input-funs input-funs}
                                                   optional)}
        {:keys [cache-entry
                result]
         :as   bundle}        (-> bundle
                                  set-flags
                                  load-examples
                                  add-new-example
                                  run-regression
                                  calculate-old-guesses
                                  calculate-new-guess
                                  create-visualizations
                                  #_output-debug-info
                                  save-examples
                                  finalize-cache)]
    (when-not preview
      (swap! cache assoc nam cache-entry))
    result))

