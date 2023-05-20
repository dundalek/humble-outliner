(ns humble-outliner.theme
  (:require
   [io.github.humbleui.paint :as paint]
   [io.github.humbleui.ui :as ui]))

(defn make-theme [opts]
  (assoc opts :cap-height 12))

(def light
  (make-theme {:fill-text (paint/fill 0xFF000000)
               ::background-fill (paint/fill 0xFFFFFFFF)
               ::bullet-fill (paint/fill 0xFFCDCCCA)}))

(def dark
  (make-theme {:fill-text (paint/fill 0xFF93A1A1)
               ::background-fill (paint/fill 0xFF002B36)
               ::bullet-fill (paint/fill 0xFF5A878B)}))

(def themes
  {:light light
   :dark dark})

(def default-theme :light)

(defn with-theme
  ([comp] (with-theme default-theme comp))
  ([theme comp]
   (ui/default-theme (themes theme) comp)))

(defn next-theme [current-theme]
  (if (= current-theme :light)
    :dark
    :light))
