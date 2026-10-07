# Madreseyar Student Android 1.3.0

## Production-ready update

- New selected backpack adaptive launcher icon and Android splash screen.
- Secure exam mode blocks screenshots/screen recording using `FLAG_SECURE`.
- Exam countdown now respects both local duration and server `expiresAt`.
- Background/exit during an active exam is stored in an encrypted incident Outbox and synchronized to `/api/student/exam-incidents`.
- Android notification channel surfaces new server notifications and successful queued deliveries.
- Bottom navigation reduced to five primary destinations; report card and attendance moved under More.
- Jalali/Persian date formatting added for sync and user-facing dates.
- Offline storage manager shows cached exam/resource size and safely clears re-downloadable files without deleting drafts or pending submissions.
- App Update Checker added through `/api/student/app-version`.
- Signed Release GitHub Actions workflow added; signing secrets never live in the repository.
- Release version: 1.3.0 / versionCode 6.

## Backend compatibility

Recommended backend: Madreseyar 72.35-p3 or later.
