# Google OAuth Mapping

This document describes the current Google sign-in / sign-up implementation in the DealLock Ecommerce Spring Boot project.

## Overview

The project supports Google authentication through Spring Security OAuth2. Both the login page and the register page use the same Google OAuth entrypoint.

This README covers:
- current UI entrypoints
- security configuration
- user provision and mapping logic
- relevant files
- current behavior and important notes

## UI entrypoints

Current Google buttons exist in:
- `src/main/resources/templates/login.html`
- `src/main/resources/templates/register.html`

Both pages invoke:
- `window.location.href = "/oauth2/authorization/google"`

That starts the Spring Security OAuth2 login flow for Google.

## Security configuration

Main security behavior is in:
- `src/main/java/com/deallock/backend/config/SecurityConfig.java`

Key points:
- OAuth2 login is enabled only when Google client settings are configured.
- The app checks for `spring.security.oauth2.client.registration.google.client-id`.
- If configured, Spring Security sets up:
  - login page: `/login`
  - OAuth2 login endpoint: `/oauth2/authorization/google`
  - OAuth2 success handler: redirect to `/admin` for `ROLE_ADMIN`, otherwise `/dashboard`
- Google-specific user handling is wired via `GoogleOauth2UserService`.

## Google user provisioning and mapping

The Google user mapping logic is in:
- `src/main/java/com/deallock/backend/services/GoogleOauth2UserService.java`

Current mapping flow:
1. Spring loads Google user info using `DefaultOAuth2UserService`.
2. The service reads the Google `email` attribute.
3. If email is missing, authentication fails with `invalid_user_info`.
4. The email is normalized to lowercase.
5. The service checks if the email is already present in the local user store:
   - `userRepository.findByEmail(normalizedEmail)`
6. If no existing user is found, a new `User` record is created with:
   - `email`
   - generated unique `username`
   - randomly generated encoded `password`
   - `role` set to `ROLE_USER` or `ROLE_ADMIN`
   - `enabled = true`
   - `creation` timestamp
7. If an existing user exists, the service updates missing fields:
   - sets `creation` if missing
   - sets `password` if blank
   - sets `username` if blank
   - stores Google full name if the local `fullName` is empty
8. The user entity is saved before returning authentication.
9. The returned OAuth2 principal uses the local user role and normalized attributes.

## Role mapping and admin detection

Admin detection is based on email matching:
- Hardcoded fallback admin email: `info@deallock.ng`
- Configured admin emails from: `app.admin-emails`

If the Google email matches either, the created or updated user receives `ROLE_ADMIN`.
Otherwise, the user receives `ROLE_USER`.

## User entity mapping

Relevant entity and repository files:
- `src/main/java/com/deallock/backend/entities/User.java`
- `src/main/java/com/deallock/backend/repositories/UserRepository.java`

`UserRepository` provides:
- `findByEmail(String email)`
- `findByUsername(String username)`
- `findByPhone(String phone)`

The `User` entity currently stores:
- `fullName`
- `email`
- `username`
- `password`
- `role`
- `enabled`
- profile data, phone, address, and creation timestamps

## Local login compatibility

The existing local login service is:
- `src/main/java/com/deallock/backend/services/CustomUserDetailsService.java`

It loads users by email or username and uses the local `password` field for authentication.

Because Google-created users receive a random encoded password, they are not immediately usable for local password login unless a password reset or profile password setup flow is added.

## Current behavior summary

- Google sign-up and sign-in are unified.
- New Google accounts with unknown emails are auto-registered.
- Existing local accounts with the same email are matched and reused.
- No separate Google provider ID field is stored on the `User` entity.
- Authentication relies on email as the primary key for Google users.

## Important notes

- Google OAuth is enabled only when the Google client ID is present in the environment or config.
- The callback endpoint is handled by Spring Security at `/login/oauth2/code/google`.
- The app does not currently keep explicit provider metadata such as `provider` or `providerId`.
- If a Google user logs in for the first time, the app creates a local user and assigns a random password.

## Where to find general project documentation

The main project README is at:
- `README.md`

That file covers the broader DealLock application setup, dependencies, running locally, Docker, environment variables, and admin notes.

## Recommended next step

If you want to make the Google mapping more explicit, consider adding:
- `oauthProvider` and `oauthProviderId` fields to `User`
- separate user creation for Google accounts vs local signup
- a post-OAuth profile completion step
- password reset support for OAuth-created users
