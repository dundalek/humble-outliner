(ns ^{:clojure.tools.namespace.repl/load false}
 humble-outliner.state
  "This namespace holds global state that will be mutated during the duration
  of our application running.

  We separate it into its own namespace for two reasons:

  1. It could be used throughout the rest of our app, and we want to avoid
     circular dependencies occurring, so we isolate it to its own ns.

  2. We want to be able to tell clojure.tools.namespace to not reload this ns
     on refresh (if we use c.t.n), otherwise we will lose the reference we
     passed to `io.github.humbleui.ui/window` in app start, which will remove
     our ability to redraw it."
  (:require
   [humble-outliner.model :as model]
   [humble-outliner.theme :as theme]
   [io.github.humbleui.window :as window]))

(def default-db
  (let [entities
        {1 {:text "hello"}
         2 {:text "world"}
         3 {:text "abc"}
         4 {:text "cdf" :parent 3}}
        #_(->> (range 50)
               (map (fn [i]
                      [i {:text (str "Item " i)}]))
               (into {}))
        next-id (inc (reduce max (keys entities)))
        order (vec (sort (keys entities)))]
    (-> {:entities entities
         :next-id next-id
         :focused-id (first order)
         :input-states {}
         :theme theme/default-theme}
        (update :entities model/recalculate-entities-order order))))

(def *db
  (atom default-db))

(defn dispatch! [action]
  (swap! *db action)
  ;; return true for convenience to be used in event handlers to stop bubbling
  true)

(comment
  (reset! *db default-db))

(def *window
  "State of the main window. Gets set on app startup."
  (atom nil))

(def *app
  "Current state of what's drawn in the main app window.
  Gets set any time we want to draw something new."
  (atom nil))

(defn redraw!
  "Redraws the window with the current app state."
  []
  ;; we redraw only when window state has been set.
  ;; this lets us call the function on ns eval and will only
  ;; redraw if the window has already been created in either
  ;; user/-main or the app -main
  (some-> *window deref window/request-frame))
