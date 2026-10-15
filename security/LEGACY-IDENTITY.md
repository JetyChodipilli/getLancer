# Legacy requester identity migration (V35)

Earlier profile anonymization changed `users.email` while retaining email-authorized inquiries and private grants. The original account UUID cannot be recovered reliably from those guest-style inquiry rows once that email mapping is gone. Re-running only the new privacy handler cannot repair this historical state.

`V35__legacy_requester_identity.sql` fails closed for ambiguous historical authority:

- An inquiry remains email-accessible only when a current active account using that email predates the inquiry. Other historical inquiries are quarantined and receive a reserved, non-registerable contact identity.
- The original contact is preserved in **`inquiries.legacy_client_email` for restricted operator review**, not exposed through API response projections. Inquiry IDs, descriptions, events, agreements and financial UUID references remain intact.
- Capabilities tied to quarantined historical inquiries are deleted, including consumed guest tokens.
- Historical private grants remain only when an active account predates the grant issue time. The existing issuer always sets expiry to issue time plus seven days. Ambiguous grants are revoked and must be explicitly reissued by the project owner.

This also affects legitimate historical guest inquiries created before their eventual account registration. Their old data cannot safely be auto-assigned based only on current email possession. Notify affected users through an approved operator process and verify the former identity or independent agreement evidence before restoring access. Do not treat a new holder's ability to receive email as proof of ownership of old records.

Before deployment, back up the database, estimate affected rows and schedule the migration appropriately for table size and lock duration. Use operator credentials to inspect `legacy_client_email`; do not expose it in general exports or support logs. Restore a verified inquiry's intended contact and moderation state in a reviewed transaction; clear its legacy-review marker only after recording the ownership decision. Merely selecting the existing admin "restore" moderation action does not rebind its contact identity.

For new closures, the privacy transaction rekeys inquiries and revokes email-bound tokens/grants before freeing the address. Re-registration and new inquiries/grants remain supported. The closed-identity namespace is reserved across public email inputs and OAuth resolution; pre-existing namespace collisions are preserved while closure selects a different stable identity.
