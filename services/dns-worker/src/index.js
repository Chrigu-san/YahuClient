// Yahu Client address service: gives every player's server a name like chris.yahuclient.net.
//
// The launcher claims a name once and gets a secret back. Whenever its server starts and the router port is open,
// it sends an update and the name is pointed at the player's public IPv4 (A record, plus an SRV record when the
// server doesn't use Minecraft's default port). The Cloudflare API token never leaves this worker.
//
// Bindings: NAMES (KV namespace), CF_API_TOKEN (secret, "Edit zone DNS" for the zone), ZONE_NAME (var).

const DEFAULT_PORT = 25565;
const UPDATE_COOLDOWN_MS = 20 * 1000;
const RECORD_TTL = 60;

// names that must stay free for our own use
const RESERVED = new Set([
    "www", "api", "mail", "smtp", "imap", "pop", "ftp", "ns", "ns1", "ns2", "relay", "play", "mc", "server", "servers",
    "admin", "root", "support", "help", "status", "download", "downloads", "dev", "test", "staging", "yahu", "yahuclient",
    "shop", "store", "auth", "login", "cdn", "static", "assets", "docs", "blog", "news", "discord",
]);

export function validName(name) {
    // a single DNS label: 3 to 32 characters, lowercase letters, digits and inner dashes
    return typeof name === "string" && /^[a-z0-9](?:[a-z0-9-]{1,30})[a-z0-9]$/.test(name) && !name.includes("--") && !RESERVED.has(name);
}

export function validIpv4(ip) {
    const parts = typeof ip === "string" ? ip.split(".") : [];
    return parts.length === 4 && parts.every((p) => /^\d{1,3}$/.test(p) && Number(p) <= 255 && String(Number(p)) === p);
}

// Addresses a friend could never reach: private ranges, CGNAT, loopback, link-local, multicast and reserved.
export function publicIpv4(ip) {
    if (!validIpv4(ip)) {
        return false;
    }
    const [a, b] = ip.split(".").map(Number);
    return !(
        a === 0 || a === 10 || a === 127 || a >= 224 ||
        (a === 100 && b >= 64 && b <= 127) ||
        (a === 169 && b === 254) ||
        (a === 172 && b >= 16 && b <= 31) ||
        (a === 192 && b === 168)
    );
}

async function sha256(text) {
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomSecret() {
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function json(status, body) {
    return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

// ---- Cloudflare DNS API

async function cf(env, path, init = {}) {
    const response = await fetch(`https://api.cloudflare.com/client/v4${path}`, {
        ...init,
        headers: { authorization: `Bearer ${env.CF_API_TOKEN}`, "content-type": "application/json", ...(init.headers || {}) },
    });
    const data = await response.json();
    if (!data.success) {
        throw new Error(`Cloudflare API ${path}: ${JSON.stringify(data.errors)}`);
    }
    return data.result;
}

let zoneIdCache = null;
async function zoneId(env) {
    if (!zoneIdCache) {
        const zones = await cf(env, `/zones?name=${encodeURIComponent(env.ZONE_NAME)}`);
        if (!zones.length) {
            throw new Error(`zone ${env.ZONE_NAME} not found`);
        }
        zoneIdCache = zones[0].id;
    }
    return zoneIdCache;
}

// Creates, updates or (with content null) deletes the single record of this type and name.
async function setRecord(env, type, name, content) {
    const zone = await zoneId(env);
    const existing = await cf(env, `/zones/${zone}/dns_records?type=${type}&name=${encodeURIComponent(name)}`);
    if (content === null) {
        for (const record of existing) {
            await cf(env, `/zones/${zone}/dns_records/${record.id}`, { method: "DELETE" });
        }
        return;
    }
    const body = JSON.stringify({ type, name, ttl: RECORD_TTL, proxied: false, comment: "Yahu Client server", ...content });
    if (existing.length) {
        await cf(env, `/zones/${zone}/dns_records/${existing[0].id}`, { method: "PUT", body });
        for (const extra of existing.slice(1)) {
            await cf(env, `/zones/${zone}/dns_records/${extra.id}`, { method: "DELETE" });
        }
    } else {
        await cf(env, `/zones/${zone}/dns_records`, { method: "POST", body });
    }
}

async function pointName(env, name, ip, port) {
    const host = `${name}.${env.ZONE_NAME}`;
    await setRecord(env, "A", host, { content: ip });
    // Minecraft looks up _minecraft._tcp.<host> first, so friends never type a port
    await setRecord(env, "SRV", `_minecraft._tcp.${host}`,
        port === DEFAULT_PORT ? null : { data: { priority: 0, weight: 5, port, target: host } });
}

// ---- endpoints

async function readBody(request) {
    try {
        return await request.json();
    } catch {
        return null;
    }
}

async function claim(request, env) {
    const body = await readBody(request);
    const name = body?.name;
    if (!validName(name)) {
        return json(400, { error: "invalid_name" });
    }
    if (await env.NAMES.get(name)) {
        return json(409, { error: "taken" });
    }
    const secret = randomSecret();
    await env.NAMES.put(name, JSON.stringify({ hash: await sha256(secret), created: Date.now(), updated: 0 }));
    return json(201, { name, address: `${name}.${env.ZONE_NAME}`, secret });
}

async function authorized(env, name, secret) {
    if (!validName(name) || typeof secret !== "string") {
        return null;
    }
    const stored = await env.NAMES.get(name, "json");
    if (!stored || stored.hash !== (await sha256(secret))) {
        return null;
    }
    return stored;
}

async function update(request, env) {
    const body = await readBody(request);
    const stored = await authorized(env, body?.name, body?.secret);
    if (!stored) {
        return json(403, { error: "forbidden" });
    }
    const port = Number(body.port ?? DEFAULT_PORT);
    if (!Number.isInteger(port) || port < 1 || port > 65535) {
        return json(400, { error: "invalid_port" });
    }
    // The name may only point at the caller's own address. Over IPv6 the IPv4 can't be seen, so the one the router
    // reported is taken, but only if it is a public address.
    const caller = request.headers.get("cf-connecting-ip") || "";
    let ip = body.ip;
    if (validIpv4(caller)) {
        if (ip && ip !== caller) {
            return json(403, { error: "ip_mismatch" });
        }
        ip = caller;
    }
    if (!publicIpv4(ip)) {
        return json(400, { error: "invalid_ip" });
    }
    if (Date.now() - (stored.updated || 0) < UPDATE_COOLDOWN_MS && stored.ip === ip && stored.port === port) {
        return json(200, { address: `${body.name}.${env.ZONE_NAME}`, ip, port, unchanged: true });
    }
    await pointName(env, body.name, ip, port);
    await env.NAMES.put(body.name, JSON.stringify({ ...stored, ip, port, updated: Date.now() }));
    return json(200, { address: `${body.name}.${env.ZONE_NAME}`, ip, port });
}

async function release(request, env) {
    const body = await readBody(request);
    const stored = await authorized(env, body?.name, body?.secret);
    if (!stored) {
        return json(403, { error: "forbidden" });
    }
    const host = `${body.name}.${env.ZONE_NAME}`;
    await setRecord(env, "A", host, null);
    await setRecord(env, "SRV", `_minecraft._tcp.${host}`, null);
    await env.NAMES.delete(body.name);
    return json(200, { released: body.name });
}

export default {
    async fetch(request, env) {
        const { pathname } = new URL(request.url);
        if (request.method === "GET" && pathname === "/") {
            return json(200, { service: "Yahu Client addresses", zone: env.ZONE_NAME });
        }
        if (request.method !== "POST") {
            return json(405, { error: "method_not_allowed" });
        }
        try {
            switch (pathname) {
                case "/v1/claim":
                    return await claim(request, env);
                case "/v1/update":
                    return await update(request, env);
                case "/v1/release":
                    return await release(request, env);
                default:
                    return json(404, { error: "not_found" });
            }
        } catch (error) {
            console.error(error);
            return json(502, { error: "dns_failed" });
        }
    },
};
