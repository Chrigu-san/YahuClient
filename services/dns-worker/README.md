# Yahu address service

A Cloudflare Worker at `https://api.yahuclient.net` that gives Yahu Client servers fixed names like `chris.yahuclient.net`.
The launcher (`launcher/servers/AddressService.cpp`) uses it; players never see it.

## API

All endpoints take and return JSON via `POST`.

| Endpoint | Body | Answer |
|---|---|---|
| `/v1/claim` | `{ name }` | `201 { name, address, secret }`, `409` if taken, `400` if the name is invalid or reserved |
| `/v1/update` | `{ name, secret, port, ip? }` | `200 { address, ip, port }`, `403` with a wrong secret or someone else's IP, `400` for a bad port or a non-public IP |
| `/v1/release` | `{ name, secret }` | `200`, removes the DNS records and frees the name |

- Names: 3 to 32 characters, `a-z`, `0-9` and single inner dashes. Some names are reserved (`www`, `api`, `relay`, ...); the same list is in `AddressService.cpp`.
- `update` points the name at the **caller's** IPv4 (`CF-Connecting-IP`). Over IPv6 it takes the `ip` from the body (what the router reported), but only if it is a public address.
- `A` record `<name>.yahuclient.net`, plus `SRV _minecraft._tcp.<name>` when the port isn't 25565, so friends never type a port. Records are DNS-only (no Cloudflare proxy; it can't carry Minecraft) with a 60 s TTL.
- KV `NAMES` stores per name: the SHA-256 of the secret, last IP and port. Secrets themselves are never stored.

## Tests

```bash
cd services/dns-worker
node --test
```

The tests replace Workers KV and the Cloudflare API with in-memory fakes.

## Deploying (once)

1. `npx wrangler login` (opens the browser, Cloudflare account that owns `yahuclient.net`)
2. `npx wrangler kv namespace create NAMES` and put the printed `id` into `wrangler.toml`
3. `npx wrangler secret put CF_API_TOKEN`: paste an API token made from the "Edit zone DNS" template, limited to `yahuclient.net`
4. `npx wrangler deploy`: also creates the custom domain `api.yahuclient.net`

Check it with `curl https://api.yahuclient.net/`.
