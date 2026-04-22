# Frontend Integration Notes (Backend Changes)

Last updated: 2026-04-22

This document is for the frontend developer. It describes:

- New/changed routes that affect navigation (Marketplace)
- New Marketplace "Locked items" + waitlist API
- New fields added to deal payloads for "locked until" / availability dates
- API auth behavior change: `/api/**` now returns `401/403` (no more redirect-to-login)

## 1) Marketplace routing fix

### What was broken

Some pages link to `GET /pages/marketplace.html` (legacy static URL).

That legacy URL previously forwarded to a non-existent static file and resulted in a broken Marketplace navigation.

### Which Marketplace page is the real one?

The canonical Marketplace route is:

- `GET /marketplace` → Thymeleaf template `src/main/resources/templates/marketplace.html` (this is the page you should work on)

The legacy route is:

- `GET /pages/marketplace.html` → now redirects to `/marketplace` (compat only)

### What to do in frontend

- Prefer linking to `GET /marketplace` (canonical).
- Legacy `GET /pages/marketplace.html` now redirects to `/marketplace`, so old links should work.

## 2) Locked deals on marketplace + waitlist

Goal:

- When a deal becomes `secured`, it should appear on the marketplace as a **LOCKED ITEM**.
- Users can join a waitlist for that locked item.
- Each locked item has a "locked until" date (availability date).

### New endpoints

#### List locked deals (public)

`GET /api/marketplace/locked-deals`

Response: JSON array

Each item contains (fields you can render):

- `id` (number) — deal id
- `title` (string)
- `value` (number | null)
- `size` (string | null)
- `securedAt` (ISO instant | null)
- `lockedUntil` (ISO instant | null) — when the locked period ends (availability date)
- `isLocked` (boolean)
- `waitlistCount` (number)
- `imageUrl` (string) — URL to fetch the item photo

#### Locked deal photo (public)

`GET /api/marketplace/locked-deals/{id}/photo`

Returns bytes:

- `securedItemPhoto` if present, otherwise falls back to `itemPhoto`.

#### Join waitlist (requires login)

`POST /api/marketplace/locked-deals/{id}/waitlist`

Auth:

- Requires a logged-in user (cookie session or remember-me)

Responses:

- `200 OK` with `{ message, waitlistCount }` on success
- `200 OK` with `{ message: "Already on waitlist", waitlistCount }` if already joined
- `401 Unauthorized` with `{ message: "Login required" }` if not logged in
- `404 Not Found` if the locked item does not exist
- `409 Conflict` with `{ message: "This item is now available" }` if `lockedUntil` has passed

### UI needed (frontend)

Backend does NOT create UI for this yet. Please build:

- A "Locked items" section in the marketplace page
- A "Join waitlist" button per locked item:
  - Call `POST /api/marketplace/locked-deals/{id}/waitlist`
  - If `401`, redirect the user to `/login`
- Render the availability date using `lockedUntil`

## 3) Deal payloads now include lock/availability fields

These fields were added so the user dashboard (and admin dashboard APIs) can show:

- whether a deal is currently locked
- the date it becomes available (locked-until)

### User deals API

`GET /api/deals`

Added fields per deal:

- `securedAt`
- `lockedUntil`
- `isLocked`

### Server-rendered dashboard variable

The user dashboard also injects a JS variable:

`window.__DEALLOCK_DEALS__`

The VM now includes:

- `securedAt`
- `lockedUntil`
- `allowMarketplaceListing`

Note: the frontend JS can use `lockedUntil` to display the “available date” for locked deals.

## 4) Deal creation: marketplace listing preference

Deal creation already supports a "listing" preference from the form:

- `listing=yes` (default) → allow listing
- `listing=no` → do not list

Backend now also accepts:

- `allowMarketplaceListing=true|false` (optional)

Frontend can keep using `listing` for now.

## 5) IMPORTANT: API auth behavior changed (fixes delete->login issue)

Previously, some API calls would get **302 redirect to `/login`** when the session expired.
That breaks JS code (it expects JSON, but receives HTML).

Now:

- Any unauthenticated request to `/api/**` returns **401** (no redirects)
- Any forbidden request to `/api/**` returns **403**

### What to do in frontend

For any fetch to `/api/**`:

- If `status === 401`: redirect to `/login`
- If `status === 403`: show an authorization error

This specifically impacts "delete deal" / "cancel deal" actions and any other authenticated API calls.

## 6) Weeks selection + attach-file icon (frontend tasks)

Backend already supports:

- `weeks=1` or `weeks=2`
- `weeks=custom&customWeeks=N`

Frontend still needs:

- Replace the weeks `<input type="number">` with a select (1, 2, custom) + conditional custom input
- Add an attach/clip icon to file inputs (payment proof / item photo), purely UI

## 7) Configuration for lock duration (backend)

`app.marketplace.lock-days` controls the locked period duration.

Default:

- `7` days

File:

- `src/main/resources/application.yaml`
