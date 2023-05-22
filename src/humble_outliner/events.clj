(ns humble-outliner.events
  (:require
   [clojure.string :as str]
   [humble-outliner.model :as model]
   [humble-outliner.state :as state]
   [humble-outliner.theme :as theme]
   [io.github.humbleui.core :as core]))

(defn- update-input-state! [db id f & args]
  (apply swap! state/*input-states update id f args)
  db)

(defn- reset-input-blink-state! [id]
  (swap! state/*input-states update id assoc :cursor-blink-pivot (core/now)))

(defn- focus-item!
  ([db id] (focus-item! db id {:from 0 :to 0}))
  ([db id {:keys [from to]}]
   (update-input-state! db id assoc
                        :from from
                        :to to
                        ;; reset blink state on focus so that cursor is always visible when switching focus and does not "disappear" for brief moments
                        :cursor-blink-pivot (core/now))
   (-> db (assoc :focused-id id))))

(defn- switch-focus! [db id]
  (let [{:keys [from]} (get @state/*input-states (:focused-id db) 0)]
    (focus-item! db id {:from from :to from})))

(defn focus-before [id]
  (fn [db]
    (if-some [focus-id (model/find-item-up (:entities db) id)]
      (switch-focus! db focus-id)
      db)))

(defn focus-after [id]
  (fn [db]
    (if-some [focus-id (model/find-item-down (:entities db) id)]
      (switch-focus! db focus-id)
      db)))

(defn item-input-focused [id]
  (fn [db]
    (-> db (assoc :focused-id id))))

(defn item-input-changed [id text]
  (fn [db]
    (-> db
        (update-in [:entities id] assoc :text text))))

(defn item-indented [id]
  (fn [{:keys [entities] :as db}]
    (let [new-entities (model/item-indent entities id)]
      (if (identical? entities new-entities)
        db
        (do
          (reset-input-blink-state! id)
          (assoc db :entities new-entities))))))

(defn item-outdented [id]
  (fn [{:keys [entities] :as db}]
    (let [new-entities (model/item-outdent entities id)]
      (if (identical? entities new-entities)
        db
        (do
          (reset-input-blink-state! id)
          (assoc db :entities new-entities))))))

(defn item-move-up [id]
  (fn [db]
    (update db :entities model/item-move-up id)))

(defn item-move-down [id]
  (fn [db]
    (update db :entities model/item-move-down id)))

(defn item-enter-pressed [target-id from]
  (fn [db]
    (let [{:keys [next-id]} db
          existing-text (get-in db [:entities target-id :text])]
      (if (pos? from)
        (let [new-current-text (subs existing-text 0 from)
              new-text (subs existing-text from)]
          (-> db
              (update :next-id inc)
              (model/set-item-text target-id new-current-text)
              (model/set-item-text next-id new-text)
              (update :entities model/item-add target-id next-id)
              (focus-item! next-id)))
        (let [parent-id (get-in db [:entities target-id :parent])
              order (-> (model/get-children-order (:entities db) parent-id)
                        (model/insert-before target-id next-id))]
          (-> db
              (update :next-id inc)
              (model/set-item-text next-id "")
              (assoc-in [:entities next-id :parent] parent-id)
              (update :entities model/recalculate-entities-order order)))))))

(defn item-beginning-backspace-pressed [item-id]
  (fn [db]
    (let [{:keys [entities]} db
          sibling-id (model/find-prev-sibling entities item-id)
          children-order (model/get-children-order entities item-id)
          merge-allowed? (or (zero? (count children-order))
                             (and sibling-id
                                  (zero? (count (model/get-children-order entities sibling-id)))))]
      (if-some [merge-target-id (when merge-allowed?
                                  (model/find-item-up entities item-id))]
        (let [text (get-in entities [item-id :text])
              text-above (get-in entities [merge-target-id :text])
              new-text-above (str (str/trimr text-above) text)
              new-cursor-position (count text-above)]
          (-> db
              (model/set-item-text merge-target-id new-text-above)
              (update :entities dissoc item-id)
              (update :entities model/reparent-items children-order merge-target-id)
              (focus-item! merge-target-id {:from new-cursor-position
                                            :to new-cursor-position})))
        db))))

(defn theme-toggled []
  (fn [db]
    (update db :theme theme/next-theme)))
