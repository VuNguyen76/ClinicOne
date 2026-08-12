# ClinicOne

ClinicOne is a clinic appointment and examination operations system built with
Java 17, Spring Boot, PostgreSQL and Angular.

- Backend: `mvn spring-boot:run`
- Frontend: `cd frontend && npm start`
The local profile uses Flyway for schema changes and Hibernate `validate`; set
database credentials in `.env` and never commit that file.
