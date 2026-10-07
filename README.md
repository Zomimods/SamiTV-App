# SAMI Live TV — المشروع المدموج (الأجزاء 1–7)

هذا المشروع هو دمج الأجزاء 1 إلى 7 بالترتيب (الأحدث يستبدل الأقدم).
الإصدارات مطابقة للبرمبت: AGP 8.5.2 وKotlin 2.0.20 وSDK 34 وminSdk 26 وCompose BOM 2024.09.03 وMedia3 1.4.1.
لم يُبنَ هنا (لا يوجد Android SDK ولا شبكة)، والتحقق الحقيقي هو أول تشغيل لـ GitHub Actions.

## التثبيت من الهاتف (Termux)
1. فك الضغط:  unzip SamiLiveTV_Merged.zip -d SamiLiveTV && cd SamiLiveTV
2. git init && git add . && git commit -m "init"
3. أنشئ مستودعاً فارغاً على GitHub ثم:
   git remote add origin <رابط المستودع> && git branch -M main && git push -u origin main
4. GitHub > Actions > "Android CI" يبني APK تلقائياً (debug). نزّله من Artifacts: sami-live-tv-debug-apk.
5. ثبّت الـ APK على الهاتف (اسمح بالتثبيت من مصدر غير معروف).

## Firebase (اختياري)
بدون google-services.json يعمل التطبيق محلياً (مفضلة وسجل فقط). للتفعيل: انظر firebase/FIREBASE_SETUP.md
ثم أضف السر GOOGLE_SERVICES_JSON_BASE64 (نتيجة: base64 -w0 google-services.json).

## نسخة موقّعة (Release)
انظر RELEASE.md (أسرار SAMI_KEYSTORE_BASE64 وغيرها) ثم: git tag v1.0.0 && git push origin v1.0.0

## تحديث: مطابقة V20
الواجهة أُعيدت لتطابق موقع V20 (الألوان، الهيرو، الدليل، البطاقات، المشغّل، التعليقات، المساعد الذكي).
الحساب والمفضلة والسجل والتعليقات تعمل عبر نفس مشروع Firebase (plrcet) بنفس مسارات الموقع، بدون google-services.json.
