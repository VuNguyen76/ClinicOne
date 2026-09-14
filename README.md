# ClinicOne

ClinicOne is a clinic appointment and examination operations system built with
Java 17, Spring Boot, PostgreSQL and Angular.

- Run both: `npm run start` (starts backend on `:8081` and Angular dev server)
- Backend only: `npm run start:backend`
- Frontend only: `npm run start:frontend`

The clinic timezone is forced to `Asia/Ho_Chi_Minh` in code
(`TimeZoneConfig`) and via `-Duser.timezone` in `start:backend`, so every
developer machine writes `LocalTime` consistently — no manual flag needed.

The local profile uses Flyway for schema changes and Hibernate `validate`; set
database credentials in `.env` and never commit that file.
