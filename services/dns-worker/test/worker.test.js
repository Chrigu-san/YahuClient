// Runs with: node --test   (from services/dns-worker)
import assert from "node:assert/strict";
import { beforeEach, describe, test } from "node:test";

import worker, { publicIpv4, validName } from "../src/index.js";

// in-memory stand-ins for Workers KV and the Cloudflare DNS API
class MemoryKV {
    map = new Map();
    async get(key, type) {
        const value = this.map.get(key);
        return value === undefined ? null : type === "json" ? JSON.parse(value) : value;
    }
    async put(key, value) {
        this.map.set(key, value);
    }
    async delete(key) {
        this.map.delete(key);
    }
}

let records;
let nextId;
let apiCalls;

function fakeCloudflare() {
    records = [];
    nextId = 1;
    apiCalls = [];
    globalThis.fetch = async (url, init = {}) => {
        const u = new URL(url);
        const method = init.method || "GET";
        apiCalls.push(`${method} ${u.pathname}`);
        assert.equal(init.headers.authorization, "Bearer test-token");
        const ok = (result) => new Response(JSON.stringify({ success: true, errors: [], result }));
        if (u.pathname === "/client/v4/zones") {
            return ok([{ id: "zone1", name: u.searchParams.get("name") }]);
        }
        const m = u.pathname.match(/^\/client\/v4\/zones\/zone1\/dns_records(?:\/(\d+))?$/);
        assert.ok(m, `unexpected API call ${u.pathname}`);
        if (method === "GET") {
            return ok(records.filter((r) => r.type === u.searchParams.get("type") && r.name === u.searchParams.get("name")));
        }
        if (method === "POST") {
            const record = { id: String(nextId++), ...JSON.parse(init.body) };
            records.push(record);
            return ok(record);
        }
        const index = records.findIndex((r) => r.id === m[1]);
        if (method === "PUT") {
            records[index] = { id: m[1], ...JSON.parse(init.body) };
            return ok(records[index]);
        }
        records.splice(index, 1);
        return ok({ id: m[1] });
    };
}

let env;
beforeEach(() => {
    fakeCloudflare();
    env = { NAMES: new MemoryKV(), CF_API_TOKEN: "test-token", ZONE_NAME: "yahuclient.net" };
});

function call(path, body, ip = "77.58.1.216") {
    return worker.fetch(
        new Request(`https://api.yahuclient.net${path}`, {
            method: "POST",
            body: typeof body === "string" ? body : JSON.stringify(body),
            headers: { "cf-connecting-ip": ip },
        }),
        env,
    );
}

async function claimed(name = "chris") {
    const response = await call("/v1/claim", { name });
    assert.equal(response.status, 201);
    return (await response.json()).secret;
}

describe("names", () => {
    test("accepts normal names", () => {
        for (const name of ["chris", "freunde-survival", "abc", "a1b2c3", "x".repeat(32)]) {
            assert.ok(validName(name), name);
        }
    });
    test("rejects bad and reserved names", () => {
        for (const name of ["", "ab", "x".repeat(33), "Chris", "-chris", "chris-", "chr--is", "chr.is", "ch ris", "über", "www", "api", "relay", null, 42]) {
            assert.ok(!validName(name), String(name));
        }
    });
    test("public addresses", () => {
        for (const ip of ["77.58.1.216", "8.8.8.8", "100.128.0.1", "172.32.0.1"]) {
            assert.ok(publicIpv4(ip), ip);
        }
        for (const ip of ["10.0.0.1", "192.168.1.1", "172.16.0.1", "100.64.0.1", "127.0.0.1", "169.254.1.1", "0.0.0.0", "224.0.0.1", "1.2.3", "1.2.3.256", "01.2.3.4", "::1", ""]) {
            assert.ok(!publicIpv4(ip), ip);
        }
    });
});

describe("claim", () => {
    test("gives a secret once", async () => {
        const response = await call("/v1/claim", { name: "chris" });
        assert.equal(response.status, 201);
        const body = await response.json();
        assert.equal(body.address, "chris.yahuclient.net");
        assert.match(body.secret, /^[0-9a-f]{64}$/);
        // only the hash is stored
        assert.ok(!env.NAMES.map.get("chris").includes(body.secret));
    });
    test("a taken name stays taken", async () => {
        await claimed("chris");
        const again = await call("/v1/claim", { name: "chris" });
        assert.equal(again.status, 409);
        assert.deepEqual(await again.json(), { error: "taken" });
    });
    test("rejects invalid names and broken bodies", async () => {
        assert.equal((await call("/v1/claim", { name: "www" })).status, 400);
        assert.equal((await call("/v1/claim", { name: "A B" })).status, 400);
        assert.equal((await call("/v1/claim", "not json")).status, 400);
    });
});

describe("update", () => {
    test("points the name at the caller", async () => {
        const secret = await claimed();
        const response = await call("/v1/update", { name: "chris", secret, port: 25565 });
        assert.equal(response.status, 200);
        assert.deepEqual(await response.json(), { address: "chris.yahuclient.net", ip: "77.58.1.216", port: 25565 });
        assert.equal(records.length, 1);
        assert.equal(records[0].type, "A");
        assert.equal(records[0].name, "chris.yahuclient.net");
        assert.equal(records[0].content, "77.58.1.216");
        assert.equal(records[0].proxied, false); // Minecraft can't go through Cloudflare's proxy
    });
    test("adds an SRV record for other ports and removes it again", async () => {
        const secret = await claimed();
        await call("/v1/update", { name: "chris", secret, port: 25570 });
        const srv = records.find((r) => r.type === "SRV");
        assert.equal(srv.name, "_minecraft._tcp.chris.yahuclient.net");
        assert.deepEqual(srv.data, { priority: 0, weight: 5, port: 25570, target: "chris.yahuclient.net" });

        await call("/v1/update", { name: "chris", secret, port: 25565 });
        assert.equal(records.filter((r) => r.type === "SRV").length, 0);
        assert.equal(records.filter((r) => r.type === "A").length, 1);
    });
    test("follows a new IP without duplicating records", async () => {
        const secret = await claimed();
        await call("/v1/update", { name: "chris", secret }, "77.58.1.216");
        await call("/v1/update", { name: "chris", secret }, "85.1.2.3");
        assert.equal(records.length, 1);
        assert.equal(records[0].content, "85.1.2.3");
    });
    test("wrong secret or unknown name is refused", async () => {
        await claimed();
        assert.equal((await call("/v1/update", { name: "chris", secret: "nope" })).status, 403);
        assert.equal((await call("/v1/update", { name: "other", secret: "nope" })).status, 403);
        assert.equal(records.length, 0);
    });
    test("can't point at somebody else's IP", async () => {
        const secret = await claimed();
        const response = await call("/v1/update", { name: "chris", secret, ip: "8.8.8.8" }, "77.58.1.216");
        assert.equal(response.status, 403);
        assert.equal(records.length, 0);
    });
    test("over IPv6 the router's public IPv4 is used", async () => {
        const secret = await claimed();
        const ok = await call("/v1/update", { name: "chris", secret, ip: "77.58.1.216" }, "2a02:1210::1");
        assert.equal(ok.status, 200);
        assert.equal(records[0].content, "77.58.1.216");
        const priv = await call("/v1/update", { name: "chris", secret, ip: "192.168.1.5" }, "2a02:1210::1");
        assert.equal(priv.status, 400);
    });
    test("rejects bad ports", async () => {
        const secret = await claimed();
        for (const port of [0, 70000, 25565.5, "abc"]) {
            assert.equal((await call("/v1/update", { name: "chris", secret, port })).status, 400, String(port));
        }
    });
    test("repeated identical updates don't hit the DNS API", async () => {
        const secret = await claimed();
        await call("/v1/update", { name: "chris", secret });
        const before = apiCalls.length;
        const again = await call("/v1/update", { name: "chris", secret });
        assert.equal((await again.json()).unchanged, true);
        assert.equal(apiCalls.length, before);
    });
});

describe("release", () => {
    test("frees the name and removes its records", async () => {
        const secret = await claimed();
        await call("/v1/update", { name: "chris", secret, port: 25570 });
        assert.equal((await call("/v1/release", { name: "chris", secret })).status, 200);
        assert.equal(records.length, 0);
        assert.equal((await call("/v1/claim", { name: "chris" })).status, 201);
    });
    test("needs the secret", async () => {
        await claimed();
        assert.equal((await call("/v1/release", { name: "chris", secret: "nope" })).status, 403);
    });
});

describe("routing", () => {
    test("unknown paths and methods", async () => {
        assert.equal((await call("/v1/nope", {})).status, 404);
        const get = await worker.fetch(new Request("https://api.yahuclient.net/v1/claim"), env);
        assert.equal(get.status, 405);
        const root = await worker.fetch(new Request("https://api.yahuclient.net/"), env);
        assert.equal(root.status, 200);
    });
    test("DNS errors become 502", async () => {
        const secret = await claimed();
        globalThis.fetch = async () => new Response(JSON.stringify({ success: false, errors: [{ code: 10000, message: "Authentication error" }] }));
        const original = console.error;
        console.error = () => {};
        try {
            assert.equal((await call("/v1/update", { name: "chris", secret })).status, 502);
        } finally {
            console.error = original;
        }
    });
});
