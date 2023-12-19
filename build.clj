(ns build
  (:require
   [clojure.tools.build.api :as b]
   [babashka.process :refer [shell]])
  (:import [io.github.humbleui.jwm Platform]))

(def target-path "target")
(def class-dir (str target-path "/classes"))

(defn ->main [_]
  "outliner")

(defn ->uber-file [params]
  (str target-path "/" (->main params) ".jar"))

(defn ->executable-file [params]
  (str target-path "/" (->main params)))

(def resources-pattern
  (str
   ;; uber jar contains native libs for all platforms, let's pickup only those for target platform
   ;; this can be further improved by also picking only those for target architecture like x86_64 and arm64
   (condp = Platform/CURRENT
     Platform/MACOS ".*\\.dylib"
     Platform/WINDOWS ".*\\.dll"
     Platform/X11 ".*\\.so")
   "|.*jwm.version"
   "|.*skija.version"
   ;; default humble theme bundles a ttf font
   "|.*\\.ttf"))

(def graalvm-home (System/getenv "GRAALVM_HOME"))
(def native-image-bin (str graalvm-home "/bin/native-image"))

(def basis (b/create-basis {:project "deps.edn"}))

;; == Build API ==

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [params]
  (clean params)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file (->uber-file params)
           :basis basis
           :main 'humble-outliner.main}))

(defn native [params]
  (uber params)
  (shell
   native-image-bin
   ;; Usual practice to initialize Clojure classes at build time by default
   "--initialize-at-build-time"
   "-J-Dclojure.compiler.direct-linking=true"

   ;; Initialize problematic JWM classes at run time
   "--initialize-at-run-time=io.github.humbleui.jwm.impl.RefCounted$_FinalizerHolder"
   "--initialize-at-run-time=io.github.humbleui.jwm.impl.Managed"

   ;; Skija loads native library statically by default which gives me linker errors when running
   ;; Therefore passing flag to load libs dynamically and initialize its classes at runtime
   "-Dskija.staticLoad=false"
   "--initialize-at-run-time=io.github.humbleui.skija.impl.Cleanable"
   "--initialize-at-run-time=io.github.humbleui.skija.impl.RefCnt$_FinalizerHolder"
   "--initialize-at-run-time=io.github.humbleui.skija"

   ;; Dealing with native bindings using JNI
   "-H:+JNI"
   ;; ConfigurationFileDirectories allows conveniently to specify whole configuration directory which can be pointed to auto tracer agent output. But it seems that the reflect-config causes the compilation to hang in analysis stage.
   ;; When there is no reflection we only need the jni config, which we can specify using JNIConfigurationFiles and ignore the other configs.
   ; "-H:ConfigurationFileDirectories=traced-config"
   ; "-H:ReflectionConfigurationFiles=traced-config/reflect-config.json"
   "-H:JNIConfigurationFiles=traced-config/jni-config.json"
   (str "-H:IncludeResources=" resources-pattern)

   ;; Some extra reporting for debugging purposes
   "-H:+ReportExceptionStackTraces"
   "--report-unsupported-elements-at-runtime"
   "--native-image-info"
   "--verbose"
   "-Dskija.logLevel=DEBUG"

   "--no-fallback"
   "-jar"
   (->uber-file params)
   (->executable-file params)))
