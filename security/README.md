# Security

Security documentation and verified remediation evidence for getLancer.

- [Audit and remaining limits](AUDIT-2026-10-15.md)
- [API access controls](API-ACCESS.md)
- [Deployment and secret handling](DEPLOYMENT.md)
- [Legacy requester identity migration](LEGACY-IDENTITY.md)
- [Payment dispute inbox and recovery](PAYMENT-DISPUTES.md)
- [Education source retention budget](EDUCATION-STORAGE.md)
- [Threat model](THREAT-MODEL.md)
- [Earlier runtime, browser and capacity evidence](../docs/audits/2026-10-13-local-audit.md)

These documents distinguish implemented controls, tested behavior and unverified deployment assumptions. They do not certify that the application has no vulnerabilities or can serve millions of concurrent clients.

Report suspected vulnerabilities privately to the repository maintainer through an available private reporting channel. Do not post credentials, customer data or exploit details in public issues. Use synthetic data and local test environments; do not test real payments or production traffic without separate authorization.
