@if "%release%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell

start cmd /k ".\gradlew assembleRelease & zipalign -p -f -v 4 .\app\build\outputs\apk\me\release\app-me-release-unsigned.apk .\app\build\outputs\apk\me\release\4k.apk & apksigner sign --ks C:\Intel\program\miui.jks --ks-pass pass:123456 --out .\me.memoryboost.apk  .\app\build\outputs\apk\me\release\4k.apk & del .\app\build\outputs\apk\me\release\app-me-release-unsigned.apk & del .\app\build\outputs\apk\me\release\4k.apk & zipalign -p -f -v 4 .\app\build\outputs\apk\mi\release\app-mi-release-unsigned.apk .\app\build\outputs\apk\mi\release\4k.apk & apksigner sign --ks C:\Intel\program\miui.jks --ks-pass pass:123456 --out .\mi.memoryboost.apk  .\app\build\outputs\apk\mi\release\4k.apk & del .\app\build\outputs\apk\mi\release\app-mi-release-unsigned.apk & del .\app\build\outputs\apk\mi\release\4k.apk"