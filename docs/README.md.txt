# FITT Docs

This repo starts with docs on purpose. The goal is to make decisions before code so the system stays focused and maintainable.

How to use this docs folder
Read in this order:
1) docs/blueprint/S0-blueprint.md
2) docs/architecture/C4-context-and-container.md
3) docs/adr/ (as decisions are made)

What each folder means
- docs/blueprint
  The project justification and v1 boundaries. This is the what and why.

- docs/architecture
  Lightweight architecture communication. I use C4 System Context and Container views to describe the system shape.
- docs/adr
  Decision log for architecturally significant decisions. ADRs keep “why we chose this” attached to the repo so decisions do not get lost. 

- docs/security
  Threat modeling notes and a baseline security checklist aligned to OWASP ASVS.

- docs/api
  API-first contract sketches. The web client and future iOS client should rely on stable contracts.

- docs/ops
  How we work. Definition of Done, testing approach, basic observability expectations.

Rule for this repo
If a decision changes the system shape, write an ADR before changing code.
