(ns humble-outliner.main
  "The main app namespace.
  Responsible for initializing the window and app state when the app starts."
  (:require
   [clojure.string :as str]
   [humble-outliner.model :as model]
   [humble-outliner.state :as state :refer [dispatch!]]
   [humble-outliner.theme :as theme]
   [io.github.humbleui.core :as core]
   [io.github.humbleui.cursor :as cursor]
   [io.github.humbleui.ui :as ui]
   [io.github.humbleui.ui.clip :as clip]
   [io.github.humbleui.ui.focusable :as focusable]
   [io.github.humbleui.ui.listeners :as listeners]
   [io.github.humbleui.ui.with-cursor :as with-cursor]))

(defn update-input-state! [db id f & args]
  (apply swap! state/*input-states update id f args)
  db)

(defn reset-input-blink-state! [id]
  (swap! state/*input-states update id assoc :cursor-blink-pivot (core/now)))

(defn focus-item!
  ([db id] (focus-item! db id {:from 0 :to 0}))
  ([db id {:keys [from to]}]
   (update-input-state! db id assoc
                        :from from
                        :to to
                        ;; reset blink state on focus so that cursor is always visible when switching focus and does not "disappear" for brief moments
                        :cursor-blink-pivot (core/now))
   (-> db (assoc :focused-id id))))

(defn switch-focus! [db id]
  (let [{:keys [from]} (get @state/*input-states (:focused-id db) 0)]
    (focus-item! db id {:from from :to from})))

(defn action-focus-before [id]
  (fn [db]
    (if-some [focus-id (model/find-item-up (:entities db) id)]
      (switch-focus! db focus-id)
      db)))

(defn action-focus-after [id]
  (fn [db]
    (if-some [focus-id (model/find-item-down (:entities db) id)]
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
        order (model/get-children-order entities parent-id)
        above-idx (dec (model/index-of order id))
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

(defn item-outdent [entities id]
  (if-some [parent-id (get-in entities [id :parent])]
    (let [grad-parent-id (get-in entities [parent-id :parent])
          order (-> (model/get-children-order entities grad-parent-id)
                    (model/insert-after parent-id id))]
      (reset-input-blink-state! id)
      (-> entities
          (model/indent-following-siblings id)
          (assoc-in [id :parent] grad-parent-id)
          (model/recalculate-entities-order order)))
    entities))

(defn event-item-indented [id]
  (fn [db]
    (update db :entities item-indent id)))

(defn event-item-outdented [id]
  (fn [db]
    (update db :entities item-outdent id)))

(defn event-item-move-up [id]
  (fn [db]
    (update db :entities model/item-move-up id)))

(defn event-item-move-down [id]
  (fn [db]
    (update db :entities model/item-move-down id)))

(defn event-item-enter-pressed [target-id from]
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

(defn event-item-beginning-backspace-pressed [item-id]
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

(defn event-theme-toggled []
  (fn [db]
    (update db :theme theme/next-theme)))

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
                (dispatch! (event-item-indented id))

                (and (= :up (:key e))
                     (= #{:shift :alt} (:modifiers e)))
                (dispatch! (event-item-move-up id))

                (and (= :down (:key e))
                     (= #{:shift :alt} (:modifiers e)))
                (dispatch! (event-item-move-down id)))))

          (listeners/on-key-focused keymap
            (with-cursor/with-cursor :ibeam
              (ui/text-input opts *state))))))))

(def dot-size 6)

(def dot-spacer
  (ui/gap dot-size dot-size))

(defn dot []
  (ui/dynamic ctx [{::theme/keys [bullet-fill]} ctx]
    (ui/valign 0.5
      (clip/clip-rrect 3
        (ui/rect bullet-fill
          dot-spacer)))))

(defn outline-item [id]
  (ui/dynamic _ [{:keys [focused-id]} @state/*db
                 {:keys [text]} (get-in @state/*db [:entities id])
                 focused (= id focused-id)
                 *state (cursor/cursor state/*input-states id)
                 _ (swap! *state assoc :text text)]
    (ui/row
      (if (or focused (seq text))
        (dot)
        dot-spacer)
      (ui/gap 12 0)
      (ui/width 300
        (text-field {:id id
                     :focused focused
                     :*state *state})))))

(defn indentline []
  (ui/dynamic ctx [{::theme/keys [indentline-fill]} ctx]
    (ui/row
      ;; dot-size is 6px, with 1px line and 2px left gap the line is technically off center.
      ;; But if the dot size is an odd number and line centered, then it looks optically off.
      (ui/gap 2 0)
      (ui/rect indentline-fill
        (ui/gap 1 0)))))

(defn outline-tree [items]
  (ui/row
    (ui/gap 24 0)
    (ui/column
      (for [{:keys [id children]} items]
        (ui/column
          (outline-item id)
          (when (seq children)
            (ui/row
              (indentline)
              (outline-tree children))))))))

(defn theme-switcher []
  (ui/row
    [:stretch 1 nil]
    (ui/padding 6
      (ui/button #(dispatch! (event-theme-toggled))
        (ui/label "Switch theme")))))

(def app
  ; we must wrap our app in a theme
  (ui/dynamic _ [{:keys [theme]} @state/*db]
    (theme/with-theme
      theme
      (ui/dynamic ctx [{::theme/keys [background-fill]} ctx]
        (ui/rect background-fill
          (ui/column
            (theme-switcher)
            (ui/vscrollbar
              ;; We don't need top padding as there is a gap from the theme switcher.
              ;; There is an extra bottom padding to compesate, otherwise items
              ;; are cut off when scrolling, not sure why.
              (ui/padding 20 0 20 80
                (ui/column
                  (ui/dynamic _ [items (->> (:entities @state/*db)
                                            (model/stratify))]
                    (outline-tree items)))))))))))

(defn window []
  (ui/window
    ;; Ideally, we would pass :bg-color option since window does canvas/clear.
    ;; But it does not seem to work to grab the theme from context via top-level ui/dynamic.
    ;; Therefore there is another canvas/clear in the `app` component that sets the background.
    {:title "Outliner"}
    state/*app))

;; reset current app state on eval of this ns
(reset! state/*app app)

(defn -main
  "Run once on app start, starting the humble app."
  [& args]
  (ui/start-app!
    (reset! state/*window (window)))
  (state/redraw!))
