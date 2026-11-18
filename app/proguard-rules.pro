# Keep app entry points referenced only from the manifest (R8 handles most of this already).
-keep class app.pocketta.widget.** { *; }
-keep class app.pocketta.work.** { *; }
-keep class app.pocketta.notify.** { *; }
-keep class app.pocketta.data.** { *; }
-dontwarn org.jetbrains.annotations.**
