# Mapsforge carga temas de renderizado y recursos por reflexión/ruta.
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**
# El lector GPX usa SAX del sistema.
-dontwarn org.xmlpull.**
