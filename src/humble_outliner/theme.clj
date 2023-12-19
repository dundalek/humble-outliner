(ns humble-outliner.theme
  (:require
   [io.github.humbleui.paint :as paint]
   [io.github.humbleui.ui :as ui]))

(defn make-theme [opts]
  (assoc opts :cap-height 12))

(defn make-light []
  (let [background-fill (paint/fill 0xFFFFFFFF)]
    (make-theme {:fill-text (paint/fill 0xFF000000)
                 ::background-fill background-fill
                 ::bullet-fill (paint/fill 0xFFCDCCCA)
                 ::indentline-fill (paint/fill 0xFFEFEDEB)
                 :hui.button/bg background-fill
                 :hui.button/bg-hovered (paint/fill 0xFFE9E9E9)
                 :hui.button/bg-active (paint/fill 0xFFF0F0F0)})))

(defn make-dark []
  (let [background-fill (paint/fill 0xFF002B36)]
    (make-theme {:fill-text (paint/fill 0xFF93A1A1)
                 ::background-fill background-fill
                 ::bullet-fill (paint/fill 0xFF5A878B)
                 ::indentline-fill (paint/fill 0xFF0B4A5A)
                 :hui.button/bg background-fill
                 :hui.button/bg-hovered (paint/fill 0xFF003C48)
                 :hui.button/bg-active (paint/fill 0xFF003742)})))

(def *themes
  (delay {:light (make-light)
          :dark (make-dark)}))

(def default-theme :light)

(defn with-theme
  ([comp] (with-theme default-theme comp))
  ([theme comp]
   (ui/default-theme (@*themes theme) comp)))

(defn next-theme [current-theme]
  (if (= current-theme :light)
    :dark
    :light))
