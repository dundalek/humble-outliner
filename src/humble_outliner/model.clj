(ns humble-outliner.model)

(defn recalculate-entities-order [entities order]
  (reduce (fn [entities [order id]]
            (assoc-in entities [id :order] order))
          entities
          (map-indexed list order)))
