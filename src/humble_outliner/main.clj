(ns humble-outliner.main
  "The main app namespace.
  Responsible for initializing the window and app state when the app starts."
  (:require
   [clojure.string :as str]
   [io.github.humbleui.cursor :as cursor]
   [io.github.humbleui.paint :as paint]
   [io.github.humbleui.ui :as ui] ;; [io.github.humbleui.window :as window]
   [io.github.humbleui.ui.clip :as clip]
   [io.github.humbleui.ui.focusable :as focusable]
   [io.github.humbleui.ui.listeners :as listeners]
   [io.github.humbleui.ui.with-cursor :as with-cursor]
   [humble-outliner.state :as state]
   [io.github.humbleui.core :as core]))

(defonce *input-states (atom {}))

(defn update-input-state! [db id f & args]
  (apply swap! *input-states update id f args)
  db)

(defn reset-input-blink-state! [id]
  (swap! *input-states update id assoc :cursor-blink-pivot (core/now)))

(defn switch-focus! [db id]
  (let [{:keys [from]} (get @*input-states (:focused-id db) 0)]
    (update-input-state! db id assoc
                         :from from
                         :to from
                         ;; reset blink state on focus so that cursor is always visible when switching focus and does not "disappear" for brief moments
                         :cursor-blink-pivot (core/now))
    (-> db
        (assoc :focused-id id))))

(defn focus-item! [db id]
  (update-input-state! db id assoc
                       :from 0
                       :to 0
                       :cursor-blink-pivot (core/now))
  (-> db
      (assoc :focused-id id)))

(defn recalculate-entities-order [entities order]
  (reduce (fn [entities [order id]]
            (assoc-in entities [id :order] order))
          entities
          (map-indexed list order)))

(do
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

  (stratify
   {3 {:order 2}
    2 {:order 1}
    1 {:order 0}
    5 {:order 1 :parent 1}
    4 {:order 0 :parent 1}}))

(def default-db
  (-> {:entities {1 {:text "hello"}
                  2 {:text "world"}
                  3 {:text "abc"}
                  4 {:text "cdf" :parent 3}}
       :next-id 5
       :focused-id nil}
      (update :entities recalculate-entities-order [1 2 3 4])
      (focus-item! 1)))

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

(defonce *db
  (atom default-db))

(comment
  (reset! *db default-db))

(defn dispatch! [action]
  (swap! *db action)
  true)

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

(defn action-focus-before [id]
  (fn [db]
    (if-some [focus-id (find-item-up (:entities db) id)]
      (switch-focus! db focus-id)
      db)))

(defn action-focus-after [id]
  (fn [db]
    (if-some [focus-id (find-item-down (:entities db) id)]
      (switch-focus! db focus-id)
      db)))

(defn event-item-input-focused [id]
  (fn [db]
    (-> db (assoc :focused-id id))))

(defn event-item-input-changed [id text]
  (fn [db]
    (-> db
        (update-in [:entities id] assoc :text text))))

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
        (reset-input-blink-state! id)
        (update entities id assoc
                :parent above-id
                :order last-order))
      entities)))

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

(defn item-outdent [entities id]
  (if-some [parent-id (get-in entities [id :parent])]
    (let [grad-parent-id (get-in entities [parent-id :parent])
          order (-> (get-children-order entities grad-parent-id)
                    (insert-after parent-id id))]
      (reset-input-blink-state! id)
      (-> entities
          (indent-following-siblings id)
          (assoc-in [id :parent] grad-parent-id)
          (recalculate-entities-order order)))
    entities))

(defn event-item-indented [id]
  (fn [db]
    (update db :entities item-indent id)))

(defn event-item-outdented [id]
  (fn [db]
    (update db :entities item-outdent id)))

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

(defn event-item-enter-pressed [target-id from]
  (fn [db]
    (let [{:keys [next-id]} db
          existing-text (get-in db [:entities target-id :text])]
      (if (pos? from)
        (let [new-current-text (subs existing-text 0 from)
              new-text (subs existing-text from)]
          (-> db
              (update :next-id inc)
              (set-item-text target-id new-current-text)
              (set-item-text next-id new-text)
              (update :entities item-add target-id next-id)
              (focus-item! next-id)))
        (let [parent-id (get-in db [:entities target-id :parent])
              order (-> (get-children-order (:entities db) parent-id)
                        (insert-before target-id next-id))]
          (-> db
              (update :next-id inc)
              (set-item-text next-id "")
              (assoc-in [:entities next-id :parent] parent-id)
              (update :entities recalculate-entities-order order)))))))

(defn event-item-beginning-backspace-pressed [item-id]
  (fn [db]
    (let [{:keys [entities]} db
          sibling-id (find-prev-sibling entities item-id)
          children-order (get-children-order entities item-id)
          merge-allowed? (or (zero? (count children-order))
                             (and sibling-id
                                  (zero? (count (get-children-order entities sibling-id)))))]
      (if-some [merge-target-id (when merge-allowed?
                                  (find-item-up entities item-id))]
        (let [text (get-in entities [item-id :text])
              text-above (get-in entities [merge-target-id :text])
              new-text-above (str (str/trimr text-above) text)
              new-cursor-position (count text-above)]
          (-> db
              (set-item-text merge-target-id new-text-above)
              (update :entities dissoc item-id)
              (update :entities reparent-items children-order merge-target-id)
              (focus-item! merge-target-id)
              (update-input-state! merge-target-id assoc
                                   :from new-cursor-position
                                   :to new-cursor-position)))
        db))))

(defn text-field [{:keys [id focused *state]}]
  (let [opts {:focused focused
              :on-focus (fn [] ; no parameters for on-focus
                          ; (println "on-focus" id)
                          (dispatch! (event-item-input-focused id)))
              :on-change (fn [{:keys [text]}]
                           ; (println "on-change" id)
                           (dispatch! (event-item-input-changed id text)))}
        keymap {:enter #(dispatch! (event-item-enter-pressed id (:from @*state)))
                :up #(dispatch! (action-focus-before id))
                :down #(dispatch! (action-focus-after id))}]
    (ui/with-context {:hui.text-field/cursor-blink-interval 500
                      :hui.text-field/cursor-width          1
                      :hui.text-field/padding-top           (float 8)
                      :hui.text-field/padding-bottom        (float 8)
                      :hui.text-field/padding-left          (float 0)
                      :hui.text-field/padding-right         (float 0)}
      (focusable/focusable opts
        (listeners/event-listener {:capture? true} :key
          (fn [e ctx]
            (when (and (:hui/focused? ctx) (:pressed? e))
              (cond
                (and (= :backspace (:key e))
                     (zero? (:from @*state))
                     (zero? (:to @*state)))
                (do (dispatch! (event-item-beginning-backspace-pressed id))
                    true)

                (and (= :tab (:key e))
                     (:shift (:modifiers e)))
                (dispatch! (event-item-outdented id))

                (= :tab (:key e))
                ; (println "indent")
                (dispatch! (event-item-indented id)))))

          (listeners/on-key-focused keymap
            (with-cursor/with-cursor :ibeam
              (ui/text-input opts *state))))))))

(def dot-spacer
  (ui/gap 6 6))

(def dot
  (ui/valign 0.5
    (clip/clip-rrect 3
      (ui/rect (paint/fill 0xFFCDCCCA)
        dot-spacer))))

(defn outline-item [id]
  (ui/dynamic _ [{:keys [focused-id]} @*db
                 {:keys [text]} (get-in @*db [:entities id])
                 focused (= id focused-id)
                 *state (cursor/cursor *input-states id)
                 _ (swap! *state assoc :text text)]
    (ui/row
      (if (or focused (seq text))
        dot
        dot-spacer)
      (ui/gap 10 0)
      (ui/width 300
        (text-field {:id id
                     :focused focused
                     :*state *state})))))

(defn outline-tree [items]
  (ui/row
    (ui/gap 20 0)
    (ui/column
      (for [{:keys [id children]} items]
        (ui/column
          (outline-item id)
          (when (seq children)
            (outline-tree children)))))))

(def app
  ; we must wrap our app in a theme
  (ui/default-theme {:cap-height 12}
    (ui/valign 0.5
      (ui/halign 0.5
        (ui/column
         ; (ui/button #(dispatch! (action-append-item))
         ;   (ui/label "Add item"))
          (ui/dynamic _ [items (->> (:entities @*db)
                                    (stratify))]
            (outline-tree items)))))))

;; reset current app state on eval of this ns
(reset! state/*app app)

(defn -main
  "Run once on app start, starting the humble app."
  [& args]
  (ui/start-app!
    (reset! state/*window
            (ui/window
              {:title    "Editor"
               :bg-color 0xFFFFFFFF}
              state/*app)))
  (state/redraw!))