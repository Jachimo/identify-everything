#!/usr/bin/env node
/**
 * Unit tests for GUID / URL parsing logic used by the mobile app.
 *
 * These are plain Node.js — no Jest, no transpilation, no React Native.
 * They test the same pure functions that live in mobile/src/sync/guid.ts,
 * verifying the contract between the QR label generator, the mobile scan
 * screen, and the server.
 *
 * Run:
 *   node tests/test_guid_utils.js
 */

"use strict";

// ── Mirror of mobile/src/sync/guid.ts ──────────────────────────────────────
// (Pure logic, no Expo imports — safe to run in Node)

const BASE26_CHARS = "abcdefghijklmnop0123456789";
const GUID_PATTERN = /^[a-p0-9]{4}_[a-p0-9]{4}_[a-p0-9]{4}_[a-p0-9]{4}$/;
const GUID_NOSEP   = /^[a-p0-9]{16}$/;
const URL_GUID_RE  = /\/objects\/v1\/([a-p0-9_]{16,19})$/i;

function isValidGuid(guid) {
  if (!guid) return false;
  return GUID_PATTERN.test(guid) || GUID_NOSEP.test(guid);
}

function normalizeGuid(guid) {
  if (!guid) return null;
  const stripped = guid.replace(/_/g, "");
  if (stripped.length !== 16) return null;
  return [
    stripped.slice(0,  4),
    stripped.slice(4,  8),
    stripped.slice(8,  12),
    stripped.slice(12, 16),
  ].join("_");
}

function extractGuidFromUrl(url) {
  const match = URL_GUID_RE.exec(url);
  if (!match) return null;
  return normalizeGuid(match[1]);
}

function buildItemUrl(domain, guid) {
  return `https://${domain}/objects/v1/${guid}`;
}

function generateGuid() {
  const chars = BASE26_CHARS.split("");
  const block = () =>
    Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
  return [block(), block(), block(), block()].join("_");
}

// ── Minimal test runner ────────────────────────────────────────────────────

let passed = 0;
let failed = 0;
const failures = [];

function test(name, fn) {
  try {
    fn();
    console.log(`  ✓  ${name}`);
    passed++;
  } catch (e) {
    console.error(`  ✗  ${name}`);
    console.error(`     ${e.message}`);
    failures.push({ name, error: e.message });
    failed++;
  }
}

function assertEqual(actual, expected, msg) {
  if (actual !== expected) {
    throw new Error(
      (msg ? msg + ": " : "") +
      `expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`
    );
  }
}

function assertTrue(value, msg) {
  if (!value) throw new Error(msg || `Expected truthy, got ${JSON.stringify(value)}`);
}

function assertNull(value, msg) {
  if (value !== null) throw new Error(msg || `Expected null, got ${JSON.stringify(value)}`);
}

// ── Test suites ────────────────────────────────────────────────────────────

console.log("\nGUID character set");

test("BASE26 has exactly 26 characters", () => {
  assertEqual(BASE26_CHARS.length, 26);
});

test("BASE26 uses [a-p] (16 letters) and [0-9] (10 digits)", () => {
  const letters = BASE26_CHARS.slice(0, 16);
  const digits  = BASE26_CHARS.slice(16);
  assertEqual(letters, "abcdefghijklmnop");
  assertEqual(digits,  "0123456789");
});

test("BASE26 excludes [q-z]", () => {
  for (const ch of "qrstuvwxyz") {
    assertTrue(!BASE26_CHARS.includes(ch), `Char '${ch}' should not be in BASE26`);
  }
});

// ─────────────────────────────────────────────────────────────────────────

console.log("\nGUID validation — isValidGuid()");

test("accepts canonical 4-4-4-4 form with underscores", () => {
  assertTrue(isValidGuid("abcd_1234_efgh_5678"));
});

test("accepts 16-char form without underscores", () => {
  assertTrue(isValidGuid("abcd1234efgh5678"));
});

test("accepts all-letter GUID", () => {
  assertTrue(isValidGuid("aaaa_bbbb_cccc_dddd"));
});

test("accepts all-digit GUID", () => {
  assertTrue(isValidGuid("0000_1111_2222_3333"));
});

test("rejects GUID with invalid letter [q]", () => {
  assertTrue(!isValidGuid("qbcd_1234_efgh_5678"));
});

test("rejects GUID with invalid letter [z]", () => {
  assertTrue(!isValidGuid("abcd_1234_efgz_5678"));
});

test("rejects GUID that is too short", () => {
  assertTrue(!isValidGuid("abc_123_efg_567"));
});

test("rejects GUID that is too long", () => {
  assertTrue(!isValidGuid("abcde_1234_efgh_5678"));
});

test("rejects empty string", () => {
  assertTrue(!isValidGuid(""));
});

test("rejects null", () => {
  assertTrue(!isValidGuid(null));
});

// ─────────────────────────────────────────────────────────────────────────

console.log("\nGUID normalisation — normalizeGuid()");

test("canonical form is returned unchanged", () => {
  assertEqual(normalizeGuid("abcd_1234_efgh_5678"), "abcd_1234_efgh_5678");
});

test("16-char form is re-formatted with underscores", () => {
  assertEqual(normalizeGuid("abcd1234efgh5678"), "abcd_1234_efgh_5678");
});

test("non-standard separator placement is accepted and re-normalised", () => {
  // normalizeGuid strips ALL underscores then reformats as 4-4-4-4.
  // Some label printers may encode the same 16-char GUID with different
  // separator positions — this must round-trip to the canonical form.
  assertEqual(normalizeGuid("ab_cd12_34ef_gh56_78"), "abcd_1234_efgh_5678");
});

test("null input yields null", () => {
  assertNull(normalizeGuid(null));
});

test("empty string yields null", () => {
  assertNull(normalizeGuid(""));
});

test("17-char string yields null (wrong length)", () => {
  assertNull(normalizeGuid("abcd1234efgh56789"));
});

test("normalized GUID passes isValidGuid", () => {
  const guid = normalizeGuid("abcd1234efgh5678");
  assertTrue(isValidGuid(guid));
});

// ─────────────────────────────────────────────────────────────────────────

console.log("\nURL extraction — extractGuidFromUrl()");

test("extracts GUID from canonical URL with underscores", () => {
  const url  = "https://mylabels.example.com/objects/v1/abcd_1234_efgh_5678";
  const guid = extractGuidFromUrl(url);
  assertEqual(guid, "abcd_1234_efgh_5678");
});

test("extracts and normalizes GUID from URL without underscores", () => {
  const url  = "https://example.com/objects/v1/abcd1234efgh5678";
  const guid = extractGuidFromUrl(url);
  assertEqual(guid, "abcd_1234_efgh_5678");
});

test("works with different domains", () => {
  const url  = "https://tags.mycompany.io/objects/v1/pppp_0000_aaaa_9999";
  const guid = extractGuidFromUrl(url);
  assertEqual(guid, "pppp_0000_aaaa_9999");
});

test("returns null for non-matching URL", () => {
  assertNull(extractGuidFromUrl("https://example.com/wrong/path/abc123"));
});

test("returns null for empty path", () => {
  assertNull(extractGuidFromUrl("https://example.com/objects/v1/"));
});

test("returns null for plain text (non-URL QR content)", () => {
  assertNull(extractGuidFromUrl("just some text on a label"));
});

test("returns null for empty string", () => {
  assertNull(extractGuidFromUrl(""));
});

// ─────────────────────────────────────────────────────────────────────────

console.log("\nURL building — buildItemUrl()");

test("builds correct URL from domain and GUID", () => {
  const url = buildItemUrl("mylabels.example.com", "abcd_1234_efgh_5678");
  assertEqual(url, "https://mylabels.example.com/objects/v1/abcd_1234_efgh_5678");
});

test("roundtrip: buildItemUrl → extractGuidFromUrl recovers the GUID", () => {
  const guid = "abcd_1234_efgh_5678";
  const url  = buildItemUrl("example.com", guid);
  assertEqual(extractGuidFromUrl(url), guid);
});

// ─────────────────────────────────────────────────────────────────────────

console.log("\nGUID generation — generateGuid()");

test("generates a valid GUID", () => {
  const guid = generateGuid();
  assertTrue(isValidGuid(guid), `Generated GUID '${guid}' is not valid`);
});

test("generates unique GUIDs", () => {
  const set = new Set(Array.from({ length: 1000 }, generateGuid));
  assertEqual(set.size, 1000, "Expected 1000 unique GUIDs");
});

test("all generated characters are in BASE26", () => {
  for (let i = 0; i < 200; i++) {
    const guid = generateGuid();
    for (const ch of guid.replace(/_/g, "")) {
      assertTrue(
        BASE26_CHARS.includes(ch),
        `Generated char '${ch}' not in BASE26 (guid: ${guid})`
      );
    }
  }
});

test("generated GUID can be extracted from a built URL", () => {
  const guid = generateGuid();
  const url  = buildItemUrl("labels.example.com", guid);
  assertEqual(extractGuidFromUrl(url), guid);
});

// ── Summary ────────────────────────────────────────────────────────────────

console.log(`\n${"─".repeat(50)}`);
if (failed === 0) {
  console.log(`✓  All ${passed} tests passed.\n`);
  process.exit(0);
} else {
  console.error(`\n✗  ${failed} test(s) failed, ${passed} passed.\n`);
  for (const { name, error } of failures) {
    console.error(`   FAIL: ${name}`);
    console.error(`         ${error}`);
  }
  console.log();
  process.exit(1);
}
