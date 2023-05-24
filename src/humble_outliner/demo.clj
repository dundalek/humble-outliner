(ns humble-outliner.demo
  (:require
   [humble-outliner.events :as events]
   [humble-outliner.state :as state]
   [io.github.humbleui.core :as hcore]))

;; Pumping events for UI demo showcase.
;; Taking a lot of shortcuts and hardcoding, but seems that with more effort
;; it could also be viable to use an approach like this to drive integration tests.

(defn dispatch-event! [event]
  (let [window @state/*window
        ctx {:window window
             :scale 1.0 #_(window/scale window)
             :mouse-pos (hcore/ipoint 0 0)}]
    (hcore/event @state/*app ctx event)))

(defn press-key!
  ([key] (press-key! key #{}))
  ([key modifiers]
   ;; Note: not a full proper event, omitting :key-name, :key-types
   ;; Also modifiers should be pressed before and released after
   (let [event {:event :key
                :key key
                :modifiers modifiers
                :location :default}]
     (dispatch-event! (assoc event :pressed? true))
     (dispatch-event! (assoc event :pressed? false)))))

(defn input-text! [text]
  (dispatch-event! {:event :text-input
                    :text text
                    :replacement-start -1
                    :replacement-end -1}))

(def demo-steps
  [[input-text! "Outliner features"]
   [press-key! :enter]
   [press-key! :tab]
   [input-text! "up and down arrows to move between items"]
   [press-key! :enter]
   [press-key! :enter]
   [press-key! :enter]
   [press-key! :up]
   [press-key! :up]
   [input-text! "enter to add new items"]
   [press-key! :enter]
   [press-key! :tab]
   [press-key! :enter]
   [input-text! "abc"]
   [press-key! :enter]
   [input-text! "xyz"]
   [press-key! :enter]
   [press-key! :enter]
   [press-key! :tab #{:shift}]
   [input-text! "backspace to delete/join items"]
   [press-key! :up]
   [press-key! :backspace]
   [press-key! :home]
   [press-key! :backspace]
   [press-key! :home]
   [press-key! :backspace]
   [press-key! :down]
   [press-key! :end]
   [press-key! :enter]
   [input-text! "showcase dark theme"]
   [press-key! :enter]
   ;; cheating by dispatching event directly instead of mouse event on the button
   [state/dispatch! (events/theme-toggled)]
   [input-text! "tab to indent items"]
   [press-key! :tab]
   [press-key! :enter]
   [press-key! :tab]
   [input-text! "shift-tab to unindent"]
   [press-key! :tab #{:shift}]
   [press-key! :tab #{:shift}]
   [press-key! :end]
   [press-key! :enter]
   [input-text! "moving item"]
   [press-key! :enter]
   [press-key! :tab]
   [input-text! "alt+shift+up/down to move item up/down"]
   [press-key! :up #{:shift :alt}]
   [press-key! :up #{:shift :alt}]
   [press-key! :up #{:shift :alt}]
   [press-key! :down #{:shift :alt}]
   [press-key! :down #{:shift :alt}]
   [press-key! :down #{:shift :alt}]
   [press-key! :down]
   [press-key! :down]
   [input-text! "That's all for now!"]])

(defn play-demo! []
  (let [delay-ms 600]
    (doseq [[f & args] demo-steps]
      (apply f args)
      (Thread/sleep delay-ms))))
