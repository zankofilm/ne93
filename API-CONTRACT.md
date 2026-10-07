# Student Android API Contract — v1.3.0 Production Ready

Server base: `https://scooljavanrood.ir/app`

## Authentication and dashboard

- `POST /api/student/login`
- `POST /api/student/change-password`
- `GET /api/student/dashboard`
- `POST /api/student/profile`
- `POST /api/student/logout`

The dashboard response is cached locally and is expected to include at least:
`student`, `todaySchedule`, `weeklySchedule`, `homework`, `exams`, `notifications`, `attendance`, `report`, `termReports.semester1`, `termReports.semester2`, and `resources`.

## Homework delivery and online-only actions

- `POST /api/student/homework/submit`
- `POST /api/student/online-class/join`

Homework text is cached locally. From Android 1.2.0, final homework delivery uses an encrypted local Outbox. When the device is offline, the submission is queued and WorkManager retries it after connectivity returns. The client sends a stable `Idempotency-Key` header for the queued submission; the server should treat that key idempotently when supported. Joining an online class remains online-only.

## Offline exam flow

- `GET /api/student/exams/:examId/download`
- `POST /api/student/exams/:examId/submit`
- `POST /api/student/exams/:examId/submit-mixed`
- `POST /api/student/exams/:examId/submit-descriptive`

The download response must provide the signed/authorized exam package fields used by the native app, including attempt/grant data and a package version/signature. Finalized responses are stored locally when offline and retried with the same `submissionKey` to keep delivery idempotent.

## Client behavior

- Successful login triggers a full sync.
- App launch with a valid session shows local cache immediately, then syncs when online.
- Manual dashboard refresh triggers a full sync.
- Eligible exam packages are downloaded automatically during sync when the server marks them startable.
- Common HTTPS learning resources are prefetched for offline use where supported.
- Final exam and homework delivery can be queued locally while offline and are retried when connectivity returns.

## Android 1.3.0 additions

- `GET /api/student/app-version?versionCode=6` — latest Android version, minimum supported version, optional HTTPS APK URL and release notes.
- `POST /api/student/exam-incidents` — receives encrypted-Outbox replay of background/exit events recorded during an active exam.

The APK URL is configured server-side through `MADRESEYAR_STUDENT_APK_URL`; clients must not invent or hard-code an update location.
