## Architecture rules
- Controllers call services only, never repositories
- Repositories contrain SQL/data access only, no business logic.
- Pure logic (splitters, prompt builders, trimmers) lives in 'logic' packages and must not depend on Spring beans or the database