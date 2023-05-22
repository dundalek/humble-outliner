(ns humble-outliner.main
  "The main app namespace.
  Responsible for initializing the window and app state when the app starts."
  (:require
   [humble-outliner.state :as state]
   [humble-outliner.views :as views]
   [io.github.humbleui.ui :as ui]))

(defn window []
  (ui/window
    ;; Ideally, we would pass :bg-color option since window does canvas/clear.
    ;; But it does not seem to work to grab the theme from context via top-level ui/dynamic.
    ;; Therefore there is another canvas/clear in the `app` component that sets the background.
    {:title "Outliner"}
    state/*app))

;; reset current app state on eval of this ns
(reset! state/*app views/app)

(defn -main
  "Run once on app start, starting the humble app."
  [& args]
  (ui/start-app!
    (reset! state/*window (window)))
  (state/redraw!))
