(ns utils.core
  (:require
   [clojure.string :refer [split]]
   [camel-snake-kebab.core :as csk]
   [clojure.pprint :as pp]
   [project-specs :as pspecs]
   #?(:clj [clojure.spec.test.alpha :as stest]
      :cljs [cljs.spec.test.alpha :as stest :include-macros true])
   #?(:clj [clojure.spec.alpha :as s]
      :cljs [cljs.spec.alpha :as s] :include-macros true)
   #?(:clj [clojure.data.json :as json])))

#?(:clj (defn max-val [m] {:max (reduce max m)}))
#?(:cljs (defn max-val [m] (reduce max m)))
(s/fdef max-val
  :args #?(:clj ::pspecs/max-val-args :cljs ::pspecs/ranges)
  :ret #?(:clj ::pspecs/max-val-ret :cljs ::pspecs/range))
(stest/instrument `max-val)

#?(:clj (defn min-val [m] {:min (reduce min m)}))
#?(:cljs (defn min-val [m] (reduce min m)))
(s/fdef min-val
  :args #?(:clj ::pspecs/min-val-args :cljs ::pspecs/ranges)
  :ret #?(:clj ::pspecs/min-val-ret :cljs ::pspecs/range))
(stest/instrument `min-val)

(defn merge-maps [v] (apply merge v))
(defn get-min-max [v] ((juxt min-val max-val) v))
(defn metric-range [metric] (fn [v] (-> (map metric v) get-min-max merge-maps)))

#?(:cljs
   (defn get-global-metrics
     [matches]
     (let [in-degree (metric-range :in-degree)
           out-degree (metric-range :out-degree)
           degree (metric-range :degree)
           katz-centrality (metric-range :katz-centrality)
           passes (metric-range :passes)
           betweenness-centrality (metric-range :betweenness-centrality)
           local-clustering-coefficient (metric-range :local-clustering-coefficient)
           closeness-centrality (metric-range :closeness-centrality)
           alpha-centrality (metric-range :alpha-centrality)
           current_flow_betweenness_centrality (metric-range :current_flow_betweenness_centrality)
           eigenvector-centrality (metric-range :eigenvector-centrality)]

       (-> matches
           (#(map (fn [v] (get-in v [:min-max-values])) %))
           (#((juxt
               degree
               in-degree
               out-degree
               betweenness-centrality
               local-clustering-coefficient
               closeness-centrality
               alpha-centrality
               eigenvector-centrality
               passes
               katz-centrality
               current_flow_betweenness_centrality)
              %))
           ((fn [[degree
                  in-degree
                  out-degree
                  betweenness-centrality
                  local-clustering-coefficient
                  closeness-centrality
                  alpha-centrality
                  eigenvector-centrality
                  passes
                  katz-centrality
                  current_flow_betweenness_centrality]]
              {:degree degree
               :in-degree in-degree
               :out-degree out-degree
               :betweenness-centrality betweenness-centrality
               :local-clustering-coefficient local-clustering-coefficient
               :closeness-centrality closeness-centrality
               :alpha-centrality alpha-centrality
               :eigenvector-centrality eigenvector-centrality
               :passes passes
               :katz-centrality katz-centrality
               :current_flow_betweenness_centrality current_flow_betweenness_centrality}))))))

#?(:clj
   (defn hash-by
     "Hashmap a collection by a given key"
     [key acc cur]
     (assoc acc (-> cur key str keyword) cur)))

#?(:cljs
   (defn hash-by
     "Hashmap a collection by a given key"
     [key acc cur]
     (assoc acc (key cur) cur)))

#?(:clj
   (defn hash-by-id [v] (reduce (partial hash-by :wy-id) (sorted-map) v)))

#?(:clj
   (defn hash-by-name [v] (reduce (partial hash-by :name) (sorted-map) v)))

#?(:clj
   (def output-file-type
     {:edn #(-> % pp/pprint with-out-str)
      :json #(-> % (json/write-str :key-fn (fn [k] (-> k name str csk/->camelCase))))}))

#?(:clj
   (defn csv-data->maps [csv-data]
     (map zipmap
          (->> (first csv-data)
               (map #(-> % csk/->kebab-case keyword))
               repeat)
          (rest csv-data))))

(defn logger [v]
  (-> v #?(:cljs clj->js :clj identity) #?(:cljs js/console.log :clj pp/pprint)) v)

#?(:cljs
   (defn place-node
     [canvas x-% y-%]
     {:x (* (-> canvas .-width) (/ x-% 100))
      :y (* (-> canvas .-height) (/ y-% 100))}))

(def field-dimensions [123 80])
(defn canvas-dimensions
  [scale]
  (-> field-dimensions (#(map (partial * scale) %))))

#?(:cljs
   (defn set-canvas-dimensions
     [scale]
     {:gol-bottom (fn [c] (do
                            (set! (.-height c) (-> (canvas-dimensions scale) first))
                            (set! (.-width c) (-> (canvas-dimensions scale) second))))
      :gol-top (fn [c] (do
                         (set! (.-height c) (-> (canvas-dimensions scale) first))
                         (set! (.-width c) (-> (canvas-dimensions scale) second))))
      :gol-left (fn [c] (do
                          (set! (.-height c) (-> (canvas-dimensions scale) second))
                          (set! (.-width c) (-> (canvas-dimensions scale) first))))
      :gol-right (fn [c] (do
                           (set! (.-height c) (-> (canvas-dimensions scale) second))
                           (set! (.-width c) (-> (canvas-dimensions scale) first))))}))

#?(:cljs
   (def mobile-mapping
     {:gol-right :gol-left
      :gol-left :gol-top
      :gol-bottom :gol-left
      :gol-top :gol-left}))

#?(:cljs
   (def coord-mapping
     {:gol-bottom identity
      :gol-top (fn [[x y]] [(- 100 x) (- 100 y)])
      :gol-left (fn [[x y]] [(- 100 y) x])
      :gol-right (fn [[x y]] [y (- 100 x)])}))

#?(:cljs
   (defn assoc-pos
     [nodes position-metric canvas orientation]
     (let [placement (partial place-node canvas)]
       (map (fn [n]
              (let [coord (-> n position-metric)
                    pos ((-> coord-mapping orientation) coord)]
                (assoc-in
                 n
                 [:coord]
                 (apply placement pos)))) nodes))))

#?(:cljs
   (defn get-distance
     [x1 y1 x2 y2]
     (js/Math.sqrt (+ (js/Math.pow (- x2 x1) 2) (js/Math.pow (- y2 y1) 2)))))
(s/fdef get-distance
  :args (s/coll-of ::pspecs/int-or-double)
  :ret ::pspecs/int-or-double)
(stest/instrument `get-distance)

#?(:cljs
   (defn vector-length
     "||u|| = √(u1 + u2)"
     [[x y]]
     (js/Math.sqrt (+ (js/Math.pow x 2) (js/Math.pow y 2)))))

#?(:cljs
   (defn dot-product
     [[x1 y1] [x2 y2]]
     (+ (* x1 x2) (* y1 y2))))

#?(:cljs
   (defn radians-between
     "https://www.wikihow.com/Find-the-Angle-Between-Two-Vectors"
     [vector1 vector2]
     (->
      (/ (dot-product vector1 vector2) (* (vector-length vector1) (vector-length vector2)))
      js/Math.acos)))

#?(:cljs
   (defn find-node
     [config canvas-width nodes x y]
     (let [rsq (* (-> config :nodes :radius-click) canvas-width)
           nodes-length (-> nodes count dec)]
       (loop [i 0]
         (let [interate? (< i nodes-length)
               node (get nodes i)
               dx (- x (-> node .-coord .-x))
               dy (- y (-> node .-coord .-y))
               dist-sq (+ (* dx dx) (* dy dy))
               node-found? (< dist-sq rsq)]
           (if node-found?
             node
             (when interate? (-> i inc recur))))))))

#?(:clj
   (defn assoc-names
     [players match]
     (let [short-name (fn [p]
                        (assoc
                         p
                         :player-name
                         (-> p :player-id str keyword players :short-name)))
           get-sub-names (fn [p]
                           (assoc
                            p
                            :player-in-name
                            (-> p :player-in str keyword players :short-name)
                            :player-out-name
                            (-> p :player-out str keyword players :short-name)))
           get-names (fn [fnc location team]
                       (->> team
                            :formation
                            location
                            (map fnc)))]
       (->> match
            :teams-data
            vals
            (map (fn [team]
                   (assoc
                    team
                    :formation
                    {:bench
                     (->> team (get-names short-name :bench))
                     :lineup
                     (->> team (get-names short-name :lineup))
                     :substitutions
                     (->> team (get-names get-sub-names :substitutions))})))))))
