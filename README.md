# FITT

FITT is a full-stack fitness application built with Spring Boot and Angular.

The project is designed as a professional, portfolio-ready application with an emphasis on clean structure, clear separation of concerns, and real world development practices. 
It is built intentionally and incrementally.

---

## Tech Stack

Backend: Java, Spring Boot, Spring Data JPA
Frontend: Angular, TypeScript
Tooling: Maven, Node.js, npm, Git

---

## Repository Structure

This repository is organized as a monorepo.

The backend directory contains the Spring Boot application.  
The frontend directory contains the Angular application.

Each side of the stack is developed independently while sharing a single repository.

---

## Current Status

The project is under active development.

The backend application is bootstrapped and running. Core architecture and package structure are established, and domain modeling is in progress.

---

## Running the Project Locally

Backend:

From the `backend` directory, run:

`mvn spring-boot:run`

The backend starts on port 8080.

Frontend:

From the `frontend` directory, install dependencies and start the dev server:

`npm install`  
`ng serve`

The frontend starts on port 4200.

---

## Documentation

Project direction, development phases, and architectural decisions are documented in `roadmap.md`.

---

## Notes

This project is built with an emphasis on understanding design decisions and development workflow, not just producing features.
