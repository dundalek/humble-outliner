(ns humble-outliner.views
  (:require
   [humble-outliner.demo :as demo]
   [humble-outliner.events :as events]
   [humble-outliner.model :as model]
   [humble-outliner.state :as state :refer [dispatch!]]
   [humble-outliner.theme :as theme]
   [io.github.humbleui.cursor :as cursor]
   [io.github.humbleui.ui :as ui]
   [io.github.humbleui.ui.clip :as clip]
   [io.github.humbleui.ui.focusable :as focusable]
   [io.github.humbleui.ui.listeners :as listeners]
   [io.github.humbleui.ui.with-cursor :as with-cursor]))

;; Components are couple to state directly, in practice we would likely use
;; some kind of subscriptions. Would it make sense to make the `*db` available
;; through context?

(defn text-field [{:keys [id focused *state]}]
  (let [opts {:focused focused
              :on-focus (fn [] ; no parameters for on-focus
                          (dispatch! (events/item-input-focused id)))
              :on-change (fn [{:keys [text]}]
                           (dispatch! (events/item-input-changed id text)))}
        keymap {:enter #(dispatch! (events/item-enter-pressed id (:from @*state)))
                :up #(dispatch! (events/focus-before id))
                :down #(dispatch! (events/focus-after id))}]
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
                (do (dispatch! (events/item-beginning-backspace-pressed id))
                    true)

                (and (= :tab (:key e))
                     (:shift (:modifiers e)))
                (dispatch! (events/item-outdented id))

                (= :tab (:key e))
                (dispatch! (events/item-indented id))

                (and (= :up (:key e))
                     (= #{:shift :alt} (:modifiers e)))
                (dispatch! (events/item-move-up id))

                (and (= :down (:key e))
                     (= #{:shift :alt} (:modifiers e)))
                (dispatch! (events/item-move-down id)))))

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
                 *state (cursor/cursor-in state/*db [:input-states id])
                 _ (swap! *state assoc :text text)]
    (ui/row
      (if (or focused (seq text))
        (dot)
        dot-spacer)
      (ui/gap 12 0)
      (ui/width 500
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
      (ui/button #(dispatch! (events/theme-toggled))
        (ui/label "Switch theme")))
    (ui/padding 6
      (ui/button #(dispatch! (events/clear-items))
        (ui/label "Clear items")))
    (ui/padding 6
      (ui/button (fn []
                   ;; Good enough for demo, but potentially bad things waiting
                   ;; to happen as the handlers will run on the driving thread.
                   ;; Ideally, the events would be processed on the main UI thread.
                   (.start (Thread. demo/play-demo!)))
        (ui/label "Play Demo")))))

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
