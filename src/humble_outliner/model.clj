(ns humble-outliner.model)

;; Assumption the order is integer based and we re-number all children within
;; a level. In practice we would likely use fractional indexing like:
;; https://github.com/rocicorp/fractional-indexing

(defn recalculate-entities-order [entities order]
  (reduce (fn [entities [order id]]
            (assoc-in entities [id :order] order))
          entities
          (map-indexed list order)))

(defn stratify
  ([entities]
   (stratify (group-by #(-> % val :parent) entities) nil))
  ([parent->children id]
   (->> (parent->children id)
        (sort-by #(-> % val :order))
        (map (fn [[id value]]
               (assoc value
                      :id id
                      :children (stratify parent->children id))))
        (into []))))

(comment
  (stratify
   {3 {:order 2}
    2 {:order 1}
    1 {:order 0}
    5 {:order 1 :parent 1}
    4 {:order 0 :parent 1}}))

(defn index-of [coll e]
  #_(first (keep-indexed #(when (= e %2) %1) coll))
  (loop [i 0
         coll (seq coll)]
    (when (some? coll)
      (if (= e (first coll))
        i
        (recur (inc i) (next coll))))))

(defn insert-after [v target value]
  (if-some [idx (some-> (index-of v target) inc)]
    (into (conj (subvec v 0 idx) value)
          (subvec v idx))
    (conj v value)))

(defn insert-before [v target value]
  (if-some [idx (index-of v target)]
    (into (conj (subvec v 0 idx) value)
          (subvec v idx))
    (into [value] v)))

(defn remove-at [v idx]
  (into (subvec v 0 idx)
        (subvec v (inc idx))))

(defn set-item-text [db id text]
  (-> db
      (update-in [:entities id] assoc :text text)))

(defn get-children-order [entities parent-id]
  (->> entities
       (filter #(= parent-id (-> % val :parent)))
       (sort-by #(-> % val :order))
       (map key)
       (into [])))

(defn last-child [entities id]
  (some->> entities
           (filter #(= id (-> % val :parent)))
           (sort-by #(-> % val :order))
           last
           key))

(defn first-child [entities id]
  (some->> entities
           (filter #(= id (-> % val :parent)))
           (sort-by #(-> % val :order))
           first
           key))

(defn find-last-descendent [entities id]
  (loop [id id]
    (if-some [child-id (last-child entities id)]
      (recur child-id)
      id)))

(defn find-prev-sibling [entities id]
  ;; can return nil
  (let [parent-id (get-in entities [id :parent])
        order (get-children-order entities parent-id)
        idx (index-of order id)]
    (assert (some? idx))
    (when (pos? idx)
      (get order (dec idx)))))

(defn find-item-up [entities id]
  ;; can return nil
  (if-some [prev-id (find-prev-sibling entities id)]
    (find-last-descendent entities prev-id)
    (get-in entities [id :parent])))

(defn next-sibling [entities id]
  (let [parent-id (get-in entities [id :parent])
        order (get-children-order entities parent-id)
        idx (index-of order id)
        _ (assert (some? idx))
        last-item? (= idx (dec (count order)))]
    (when-not last-item?
      (get order (inc idx)))))

(defn find-next-successor [entities id]
  (loop [id id]
    (if-some [sibling-id (next-sibling entities id)]
      sibling-id
      (when-some [parent-id (get-in entities [id :parent])]
        (recur parent-id)))))

(defn find-item-down [entities id]
  ;; can return nil
  (if-some [child-id (first-child entities id)]
    child-id
    (find-next-successor entities id)))

(defn reparent-items [entities item-ids new-parent-id]
  (reduce (fn [entities sibling-id]
            (update entities sibling-id assoc :parent new-parent-id))
          entities
          item-ids))

(defn indent-following-siblings [entities id]
  (let [order (get-children-order entities (get-in entities [id :parent]))
        idx (index-of order id)
        following-siblings (subvec order (inc idx))]
    (reparent-items entities following-siblings id)))

(defn item-add [entities target-id new-id]
  (let [order (get-children-order entities target-id)
        has-children? (seq order)]
    (if has-children?
      (-> entities
          (update new-id assoc :parent target-id)
          (recalculate-entities-order (into [new-id] order)))
      (let [parent-id (get-in entities [target-id :parent])
            order (-> (get-children-order entities parent-id)
                      (insert-after target-id new-id))]
        (-> entities
            (update new-id assoc :parent parent-id)
            (recalculate-entities-order order))))))

(defn item-move-up [entities id]
  (let [parent-id (get-in entities [id :parent])
        order (get-children-order entities parent-id)
        idx (index-of order id)
        above-idx (dec idx)
        above-id (get order above-idx)]
    (if above-id
      (let [new-order (assoc order
                             idx above-id
                             above-idx id)]
        (recalculate-entities-order entities new-order))
      (if-some [previous-parent-sibling (when parent-id
                                          (find-prev-sibling entities parent-id))]
        (let [order (-> (get-children-order entities previous-parent-sibling)
                        (conj id))]
          (-> entities
              (assoc-in [id :parent] previous-parent-sibling)
              (recalculate-entities-order order)))
        entities))))

(defn item-move-down [entities id]
  (let [parent-id (get-in entities [id :parent])
        order (get-children-order entities parent-id)
        idx (index-of order id)
        below-idx (inc idx)
        below-id (get order below-idx)]
    (if below-id
      (let [new-order (assoc order
                             idx below-id
                             below-idx id)]
        (recalculate-entities-order entities new-order))
      (if-some [next-parent-sibling (when parent-id
                                      (next-sibling entities parent-id))]
        (let [order (into [id] (get-children-order entities next-parent-sibling))]
          (-> entities
              (assoc-in [id :parent] next-parent-sibling)
              (recalculate-entities-order order)))
        entities))))

(defn item-indent [entities id]
  (let [parent-id (get-in entities [id :parent])
        order (get-children-order entities parent-id)
        above-idx (dec (index-of order id))
        above-id (get order above-idx)]
    (if above-id
      (let [last-order (inc (->> entities
                                 (filter #(= above-id (-> % val :parent)))
                                 (map #(-> % val :order))
                                 (reduce max -1)))]
        (update entities id assoc
                :parent above-id
                :order last-order))
      entities)))

(defn item-outdent [entities id]
  (if-some [parent-id (get-in entities [id :parent])]
    (let [grad-parent-id (get-in entities [parent-id :parent])
          order (-> (get-children-order entities grad-parent-id)
                    (insert-after parent-id id))]
      (-> entities
          (indent-following-siblings id)
          (assoc-in [id :parent] grad-parent-id)
          (recalculate-entities-order order)))
    entities))
