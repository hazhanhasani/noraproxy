# NoraProxy — Architecture

## MVP data flow

Reseller HTTPS subscription → explicit customer import → bounded fetch → supported URI parser → on-device TCP probes → reliability/latency scoring → one reachable node per location → copy or share selected URI → v2rayNG/V2Box.

No customer subscription is uploaded to NoraProxy servers. The current version is an independent **connection companion**, not an app that establishes a VPN tunnel.

## Local selection policy

- Max 250 nodes; 12 concurrent TCP connection attempts with 1,800 ms timeout.
- First sample per node; two extra samples for the top eight responding candidates.
- Reachable candidates always outrank failed candidates.
- Scoring uses connection success ratio, median observed connect delay, and measured jitter.
- Group by recognized region from the node name and show only the best reachable candidate per group.
- In unrecognized regions, profiles are grouped as **Other**. The selected node retains all original URI parameters.

TCP reachability does not verify the proxy protocol, TLS/Reality, tunnel routing, throughput or working credentials. A future version should replace this signal with a real authenticated proxy check.

## Multi-tenant SaaS milestones

**Not implemented in v0.1.0:** central seller accounts, RBAC, API, automated provisioning, custom APKs, or reseller analytics.

Proposed entities:

    tenants(id, slug, display_name, status)
    reseller_members(id, tenant_id, role)
    customer_subscriptions(id, tenant_id, customer_id, encrypted_ref)
    onboarding_invites(id, tenant_id, token_hash, expires_at, redeemed_at)
    policy_groups(id, tenant_id, region, entitlement)

A future redemption endpoint should exchange a short-lived, single-use opaque token for a scoped subscription URL. It must authorize all operations by server-side tenant ownership and never allow one reseller to read another reseller's customers or credentials.

## Native VPN milestone

Use a verified Xray/v2rayNG-compatible runtime rather than a homegrown networking tunnel. Add:

- VpnRuntime — lifecycle-safe Android VpnService + foreground connection handling
- HealthCheck — authenticated proxy HTTP RTT, failures, and jitter
- NetworkCache — per physical network route history with measured timestamps
- FailoverPolicy — hysteresis to prevent reconnect loops and cooldown after failures
- SubscriptionSync — ETag, bounded refresh, per-tenant isolation and safe updates

Review GPL-3.0 derivative obligations if incorporating v2rayNG sources. Keep credentials out of logs, telemetry, crash reports and analytics.

## Security acceptance criteria

- Never commit tokens, signing keys, or seller subscription URLs.
- Client does not follow HTTP redirects for subscription requests.
- HTTPS certificate validation remains enabled; no trustAllCerts bypasses.
- At-rest secrets use Android Keystore; backups disabled.
- Config export is explicit and user-triggered, since share sheets/clipboard expose data to other apps.
- Validate DNS rebinding protection and hostname resolution safeguards before adding any server-side URL fetching.
- Run production tests on Wi-Fi, mobile data, blocked networks and expired profiles.
