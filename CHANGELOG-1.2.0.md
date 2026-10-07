# Madreseyar Student Android 1.2.0

## Offline homework delivery
- Added encrypted `HomeworkOutboxStore`.
- Homework can be finalized while offline.
- Queued homework is sent automatically when connectivity returns.
- Draft text is retained until the server confirms delivery.
- Permanent 4xx validation failures stop background replay while preserving the editable draft.

## Delivery reliability
- Each queued homework submission receives a stable submission key and sends it as `Idempotency-Key`.
- Before replaying the Outbox, the app downloads the dashboard and reconciles already-delivered homework by homework ID + submitted text.
- This prevents a retry from generating duplicate teacher notifications/audit events when the original request reached Backend 72.35-p1 but the HTTP response was lost.
- WorkManager now uses exponential backoff and retries while pending exam/homework uploads remain.

## UI / safety
- Homework cards show `در صف ارسال` while awaiting network delivery.
- Home dashboard shows the number of queued uploads.
- Logout is blocked while an exam response or homework submission is still pending, preventing local data loss.

## Build
- Version code: 5
- Version name: 1.2.0
- GitHub Actions artifact: `Madreseyar-Student-APK-1.2.0-OfflineFirst`
