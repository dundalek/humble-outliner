(ns humble-outliner.helpers
  (:require
   [clojure.walk :as walk]
   [humble-outliner.model :as model]))

(defn to-compact-impl
  [key-fn entities]
  (walk/prewalk
   (fn [x]
     (cond
       (vector? x) (->> x
                        (mapcat (fn [{:keys [children] :as entity}]
                                  (cond-> [(key-fn entity)]
                                    (seq children) (conj children))))
                        (into []))
       :else x))
   (model/stratify entities)))

(defn from-compact-impl
  ([kw items]
   (from-compact-impl kw items nil))
  ([kw items parent-id]
   (loop [entities {}
          i 0
          [id maybe-children & other] items]
     (if id
       (let [entities (assoc entities id (cond-> {:order i}
                                           (not= kw :id) (assoc kw id)
                                           parent-id (assoc :parent parent-id)))]
         (if (vector? maybe-children)
           (recur (merge entities (from-compact-impl kw maybe-children id))
                  (inc i)
                  other)
           (recur entities
                  (inc i)
                  (cons maybe-children other))))
       entities))))

(def to-compact (partial to-compact-impl :id))
(def from-compact (partial from-compact-impl :id))
(def to-compact-by-text (partial to-compact-impl :text))
(def from-compact-by-text (partial from-compact-impl :text))

(defn update-compact [compact f & args]
  (to-compact (apply f (from-compact compact) args)))
