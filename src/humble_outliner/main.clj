(set! *warn-on-reflection* true)
(ns humble-outliner.main
  "The main app namespace.
  Responsible for initializing the window and app state when the app starts."
  (:gen-class)
  (:require
   [humble-outliner.state :as state]
   [humble-outliner.views :as views]
   [io.github.humbleui.app :as app]
   [io.github.humbleui.ui :as ui]))

(defn window []
  (ui/window
    ;; Ideally, we would pass :bg-color option since window does canvas/clear.
    ;; But it does not seem to work to grab the theme from context via top-level ui/dynamic.
    ;; Therefore there is another canvas/clear in the `app` component that sets the background.
    {:title "Outliner"}
    state/*app))

;; reset current app state on eval of this ns
#_(reset! state/*app views/app)

;; Replacement for `ui/start-app!` that does not start a separate thread.
;; Workaround for a window not showing when compiled with Graal native image on macOS.
(defmacro start-app! [& body]
  `(app/start
     (fn []
       ~@body)))

(defn -main
  "Run once on app start, starting the humble app."
  [& args]
  (reset! state/*app (views/app))
  (start-app!
   (reset! state/*window (window)))
  (state/redraw!))
