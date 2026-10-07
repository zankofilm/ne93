#!/usr/bin/env bash
set -euo pipefail
for f in settings.gradle.kts build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml .github/workflows/build-apk.yml; do
  test -f "$f" || { echo "Missing required file: $f"; exit 1; }
done
grep -q 'compileSdk = 35' app/build.gradle.kts
grep -q 'versionName = "1.4.0"' app/build.gradle.kts || { echo 'FAIL: expected versionName 1.4.0'; exit 1; }
grep -q 'https://scooljavanrood.ir/app' app/build.gradle.kts
grep -q 'assembleDebug' .github/workflows/build-apk.yml
grep -q 'upload-artifact@v4' .github/workflows/build-apk.yml

grep -q 'OfflineExamStore' app/src/main/java/ir/madreseyar/student/StudentRepository.kt
grep -q 'HomeworkOutboxStore' app/src/main/java/ir/madreseyar/student/StudentRepository.kt
grep -q 'syncAll(downloadContent=true)' app/src/main/java/ir/madreseyar/student/StudentViewModel.kt
grep -q 'autoSync(showLoading = local == null)' app/src/main/java/ir/madreseyar/student/StudentViewModel.kt
grep -q 'onAppForeground' app/src/main/java/ir/madreseyar/student/MainActivity.kt
grep -q 'بروزرسانی اطلاعات' app/src/main/java/ir/madreseyar/student/Screens.kt
grep -q 'downloadExam' app/src/main/java/ir/madreseyar/student/ApiClient.kt
grep -q 'status="queued"' app/src/main/java/ir/madreseyar/student/StudentRepository.kt
grep -q 'flushPendingHomeworkSubmissions' app/src/main/java/ir/madreseyar/student/StudentRepository.kt
grep -q 'reconcileHomeworkOutbox' app/src/main/java/ir/madreseyar/student/StudentRepository.kt
grep -q 'Idempotency-Key' app/src/main/java/ir/madreseyar/student/ApiClient.kt
grep -q 'schedulePeriodic' app/src/main/java/ir/madreseyar/student/SyncWorker.kt
grep -q 'setRequiredNetworkType(NetworkType.CONNECTED)' app/src/main/java/ir/madreseyar/student/SyncWorker.kt
grep -q 'BackoffPolicy.EXPONENTIAL' app/src/main/java/ir/madreseyar/student/SyncWorker.kt
grep -q 'saveSession(result.token, result.student.id)' app/src/main/java/ir/madreseyar/student/StudentRepository.kt
grep -q 'هنوز در صف ارسال است' app/src/main/java/ir/madreseyar/student/StudentRepository.kt
grep -q 'pendingHomeworkIds' app/src/main/java/ir/madreseyar/student/StudentViewModel.kt


grep -q 'installSplashScreen' app/src/main/java/ir/madreseyar/student/MainActivity.kt
grep -q 'FLAG_SECURE' app/src/main/java/ir/madreseyar/student/MainActivity.kt
grep -q 'expiresAt' app/src/main/java/ir/madreseyar/student/OfflineExamScreen.kt
grep -q 'PersianDate.dateTime' app/src/main/java/ir/madreseyar/student/Screens.kt
grep -q 'ExamIncidentStore' app/src/main/java/ir/madreseyar/student/StudentRepository.kt
grep -q 'app-version' app/src/main/java/ir/madreseyar/student/ApiClient.kt
grep -q 'حافظه آفلاین' app/src/main/java/ir/madreseyar/student/Screens.kt
grep -q 'mipmap/ic_launcher' app/src/main/AndroidManifest.xml

echo 'GitHub APK package sanity check: PASS'

# Compose painterResource must not load adaptive launcher icons from mipmap-anydpi-v26.
if grep -R --include='*.kt' -n 'painterResource(R.mipmap.ic_launcher)' app/src/main/java; then
  echo 'FAIL: adaptive launcher icon cannot be loaded with Compose painterResource; use drawable/vector or raster asset'
  exit 1
fi
grep -q 'painterResource(R.drawable.ic_launcher)' app/src/main/java/ir/madreseyar/student/Screens.kt || { echo 'FAIL: safe drawable launcher resource missing from Compose UI'; exit 1; }
