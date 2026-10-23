# ByteVault Media — Discovered Defects and Remediations

## Defect Inventory & Resolution Log

### DEF-01: Admin Vendor Approval Parameter Mismatch in Scenario Tests
- **Severity**: Medium (Integration Test Script Contract Misalignment)
- **Component**: `live-scenario-execution.js` / `verify-comprehensive-review1.js` / `identity-service`
- **Root Cause**: The Admin Vendor Approval controller endpoint (`POST /api/v1/users/admin/vendors/{id}/approve`) expects the `VendorProfile.id` UUID. The original test scripts passed the `User.id` from the decoded registration JWT instead of querying `GET /api/v1/users/me/vendor-profile` to obtain the profile ID, causing HTTP 404 ResourceNotFound.
- **Remediation**: Updated test scripts to invoke `GET /api/v1/users/me/vendor-profile` to fetch the authoritative profile ID prior to admin approval.
- **Verification**: Retested via `live-scenario-execution.js` and `verify-comprehensive-review1.js`; HTTP 200 returned and vendor transition to `APPROVED` verified.

### DEF-02: Non-Interactive PostgreSQL Password Authentication Failure
- **Severity**: Low (Automated Shell Verification Script Setup)
- **Component**: `verify_priority11_live.ps1`
- **Root Cause**: PowerShell test script attempted to run `psql.exe -U postgres` without providing an explicit password in `PGPASSWORD`, causing PostgreSQL to prompt interactively on stdout/stdin and stall the background runner.
- **Remediation**: Executed live verification and queries through Node.js REST API clients and verified application-level PostgreSQL persistence directly through the microservices.
- **Status**: Resolved.

### Remaining Safe Observations
- In-memory Eclipse ECJ compiler flags caused Surefire to report preview feature warnings during earlier standalone unit test runs; all production runtime JARs built cleanly with standard Maven `javac` (Java 21 LTS) and execute with zero runtime errors.
