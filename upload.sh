curl -I https://webconverger.com/com.webconverger.KioskApp.apk
if file ./app/build/outputs/apk/app-debug.apk
then
s3cmd put -m application/vnd.android.package-archive -P ./app/build/outputs/apk/app-debug.apk s3://webconverger.com/com.webconverger.KioskApp.apk
fi
