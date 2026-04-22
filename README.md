# DealLock Ecommerce Fullstack Spring Boot

DealLock is a Spring Boot web application for managing secured ecommerce transactions. It combines server-rendered pages with frontend JavaScript to handle user registration, OTP verification, account activation, login, deal creation, deal tracking, admin approvals, payment proof uploads, and notifications.

## What the project does

- User signup with OTP verification by email or phone
- Email-based account activation after signup
- Login, logout, remember-me, and password reset flow
- User dashboard for creating and tracking deals
- Deal lifecycle management from submission to delivery confirmation
- Admin console for approving, rejecting, securing, and closing deals
- In-app notifications plus email and SMS/WhatsApp notifications
- Profile image upload and binary file storage for deal photos and payment proofs

## Tech stack

- Java 25
- Spring Boot 4
- Spring MVC
- Spring Security
- Spring Data JPA
- Thymeleaf
- MySQL
- Flyway
- Docker and Docker Compose
- Termii API for SMS and WhatsApp messaging
- SMTP for email delivery

## Main modules

- `src/main/java/com/deallock/backend/controllers`: MVC pages and REST endpoints
- `src/main/java/com/deallock/backend/services`: mail, SMS, notifications, auth events, cleanup jobs
- `src/main/java/com/deallock/backend/entities`: JPA entities such as `User`, `Deal`, `OtpCode`, and `Notification`
- `src/main/java/com/deallock/backend/repositories`: Spring Data repositories
- `src/main/resources/templates`: Thymeleaf pages
- `src/main/resources/static/frontend`: CSS, JavaScript, and static assets
- `src/main/resources/db/migration`: Flyway SQL migrations used in production profile

## Key flows

### Authentication and onboarding

- `POST /api/send-otp`: sends OTP to email or phone
- `POST /api/verify-otp`: verifies OTP before signup
- `POST /api/signup`: creates a disabled user account and sends activation link
- `GET /activate`: enables account from activation token
- `/login`, `/forgot-password`, `/reset-password`: standard auth recovery flow

### Deals

- `GET /dashboard`: user dashboard
- `GET /api/deals`: list current user's deals
- `POST /api/deals`: create a new deal with pricing breakdown
- `POST /api/deals/{id}/payment-proof`: upload first payment receipt
- `POST /api/deals/{id}/balance-payment-proof`: upload balance receipt
- `POST /api/deals/{id}/confirm-delivery`: confirm item delivery
- `POST /api/deals/{id}/feedback`: submit final feedback

### Admin

- `GET /admin`: admin console
- Admin can approve, reject, confirm payment, mark not received, secure a deal, confirm balance payment, initiate delivery, confirm delivery, and delete deals

## Profiles and configuration

This project currently uses two important runtime modes:

- Default local mode: `application.yaml`
  - Flyway is disabled
  - Hibernate uses `ddl-auto=update`
  - Local MySQL defaults are provided
- Production mode: `application-prod.yaml`
  - Flyway is enabled
  - SMTP and Termii settings are expected through environment variables
  - Cookie and proxy settings are stricter

## Frontend integration notes

If you're working on the frontend (Marketplace + dashboards), see:

- `docs/frontend-integration.md`

## Prerequisites

- JDK 25 installed
- MySQL 8+
- Docker Desktop if you want the containerized setup

## Run locally without Docker

### 1. Start MySQL

Create a database and user that match the defaults in `application.yaml`, or override them with environment variables.

Example local defaults:

- Database: `
- Username: `
- Password: `

### 2. Optinal environment variables

You only need SMTP and Termii values if you want real email or SMS/WhatsApp delivery.

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/deallock?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:SPRING_DATASOURCE_USERNAME="deallock"
$env:SPRING_DATASOURCE_PASSWORD="deallock_pass"
```

### 3. Start the application

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

### 4. Open the app

Visit `http://localhost:8080`

## Run with Docker Compose

The compose setup starts:

- `db`: MySQL 8.4
- `app`: the Spring Boot application

Start everything:

```powershell
docker compose up --build
```

Stop everything:

```powershell
docker compose down
```

Notes:

- Docker Compose starts the app with `SPRING_PROFILES_ACTIVE=prod`
- In this mode, Flyway migrations are enabled
- You can provide env values through your shell or a `.env` file beside `docker-compose.yml`

Example `.env` values:

```env
MYSQL_DATABASE=
MYSQL_USER=
MYSQL_PASSWORD=
MYSQL_ROOT_PASSWORD=

SMTP_HOST=
SMTP_PORT=
SMTP_USER=
SMTP_PASS=
SMTP_FROM=

TERMII_API_KEY=
TERMII_SENDER_ID=
TERMII_SMS_CHANNEL=
TERMII_WHATSAPP_SENDER=
TERMII_BASE_URL=

APP_BASE_URL=http://localhost:8080
APP_ADMIN_PHONES=
```

## Environment variables

### Database

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `DB_USER`
- `DB_PASS`

### Email

- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USER`
- `SMTP_PASS`
- `SMTP_FROM`

### SMS and WhatsApp

- `TERMII_BASE_URL`
- `TERMII_API_KEY`
- `TERMII_SENDER_ID`
- `TERMII_SMS_CHANNEL`
- `TERMII_WHATSAPP_SENDER`

### App-level settings

- `APP_BASE_URL`
- `APP_ADMIN_PHONES`

## Admin account setup

New registrations are created with the role `ROLE_USER`. There is no automatic admin seed in the current codebase, so to promote a user you need to update the `users` table manually.

Example SQL:

```sql
UPDATE users
SET role = 'ROLE_ADMIN'
WHERE email = 'admin@example.com';
```

After that, log in again and the user will be redirected to `/admin`.

## File upload limits

- Profile image upload: 500 KB in the UI flow
- Deal item photo: 2 MB
- Payment proof: 2 MB
- Secured item photo: 2 MB
- Balance payment proof: 2 MB

## Development notes

- The app stores several images and proofs as `LONGBLOB` values in MySQL
- Local development currently relies on Hibernate schema updates
- Production profile uses Flyway migrations from `src/main/resources/db/migration`
- Phone OTP and admin SMS alerts require valid Termii credentials
- Real email delivery requires valid SMTP credentials

## Test command

```powershell
.\mvnw.cmd test
```

## Useful entry points in the codebase

- `src/main/java/com/deallock/backend/controllers/AuthApiController.java`
- `src/main/java/com/deallock/backend/controllers/DealApiController.java`
- `src/main/java/com/deallock/backend/controllers/AdminController.java`
- `src/main/java/com/deallock/backend/config/SecurityConfig.java`
- `src/main/resources/templates/dashboard.html`
- `src/main/resources/static/frontend/signup.js`
