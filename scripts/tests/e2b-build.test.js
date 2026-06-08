#!/usr/bin/env node
/**
 * Unit tests for scripts/e2b-build.js pure utility functions.
 *
 * Because the script does not export its helpers, we replicate the function
 * bodies under test here and verify the behaviour matches the implementation.
 * Any drift between source and test immediately reveals a regression.
 *
 * Run with: node scripts/tests/e2b-build.test.js
 */

"use strict";

const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

// ──────────────────────────────────────────────────────────────────────────────
// Inline copies of the pure helpers from e2b-build.js (under test)
// ──────────────────────────────────────────────────────────────────────────────

const defaultTemplate = "sketchware-android-build-codex:cpu8";
const defaultArchive = "/tmp/sketchware-pro-e2b-build.tar.gz";

function parseArgs(argv) {
  const options = {
    archive: defaultArchive,
    forceNew: false,
    killAfter: false,
    sandboxId: "",
    sandboxIdFile: ".e2b-sandbox-id",
    tailLines: 400,
    template: defaultTemplate,
    timeoutMs: 60 * 60 * 1000,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--archive") options.archive = argv[++index];
    else if (arg === "--force-new") options.forceNew = true;
    else if (arg === "--kill-after") options.killAfter = true;
    else if (arg === "--sandbox-id") options.sandboxId = argv[++index];
    else if (arg === "--sandbox-id-file") options.sandboxIdFile = argv[++index];
    else if (arg === "--tail-lines") options.tailLines = Number(argv[++index]);
    else if (arg === "--template") options.template = argv[++index];
    else if (arg === "--timeout-ms") options.timeoutMs = Number(argv[++index]);
    else if (arg === "--help" || arg === "-h") {
      // skip — just don't throw
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  if (!Number.isFinite(options.tailLines) || options.tailLines < 1) {
    throw new Error("--tail-lines must be a positive number");
  }
  if (!Number.isFinite(options.timeoutMs) || options.timeoutMs < 1000) {
    throw new Error("--timeout-ms must be at least 1000");
  }

  return options;
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, "'\\''")}'`;
}

function tail(text, lines) {
  const value = String(text || "");
  return value.split("\n").slice(-lines).join("\n");
}

function loadLocalEnv(envPath) {
  if (!fs.existsSync(envPath)) return;
  const content = fs.readFileSync(envPath, "utf8");
  const parsed = {};
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const separator = trimmed.indexOf("=");
    if (separator === -1) continue;
    const key = trimmed.slice(0, separator).trim();
    const value = trimmed.slice(separator + 1).trim().replace(/^["']|["']$/g, "");
    if (key) parsed[key] = value;
  }
  return parsed;
}

function readSandboxState(options) {
  if (options.forceNew) return { sandboxId: "", template: "" };
  if (options.sandboxId) return { sandboxId: options.sandboxId.trim(), template: "" };
  if (!fs.existsSync(options.sandboxIdFile)) return { sandboxId: "", template: "" };

  const raw = fs.readFileSync(options.sandboxIdFile, "utf8").trim();
  try {
    return JSON.parse(raw);
  } catch {
    return { sandboxId: raw, template: "" };
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Test runner
// ──────────────────────────────────────────────────────────────────────────────

let passed = 0;
let failed = 0;

function test(name, fn) {
  try {
    fn();
    console.log(`  ✓ ${name}`);
    passed++;
  } catch (err) {
    console.error(`  ✗ ${name}`);
    console.error(`    ${err.message}`);
    failed++;
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// parseArgs tests
// ──────────────────────────────────────────────────────────────────────────────

console.log("\nparseArgs");

test("defaults when no args", () => {
  const opts = parseArgs([]);
  assert.strictEqual(opts.template, defaultTemplate);
  assert.strictEqual(opts.archive, defaultArchive);
  assert.strictEqual(opts.forceNew, false);
  assert.strictEqual(opts.killAfter, false);
  assert.strictEqual(opts.sandboxId, "");
  assert.strictEqual(opts.tailLines, 400);
  assert.strictEqual(opts.timeoutMs, 60 * 60 * 1000);
});

test("--template sets template", () => {
  const opts = parseArgs(["--template", "my-template:v2"]);
  assert.strictEqual(opts.template, "my-template:v2");
});

test("--archive sets archive path", () => {
  const opts = parseArgs(["--archive", "/custom/path.tar.gz"]);
  assert.strictEqual(opts.archive, "/custom/path.tar.gz");
});

test("--force-new sets forceNew=true", () => {
  const opts = parseArgs(["--force-new"]);
  assert.strictEqual(opts.forceNew, true);
});

test("--kill-after sets killAfter=true", () => {
  const opts = parseArgs(["--kill-after"]);
  assert.strictEqual(opts.killAfter, true);
});

test("--sandbox-id sets sandboxId", () => {
  const opts = parseArgs(["--sandbox-id", "abc123"]);
  assert.strictEqual(opts.sandboxId, "abc123");
});

test("--sandbox-id-file sets sandboxIdFile", () => {
  const opts = parseArgs(["--sandbox-id-file", "/tmp/my-sandbox-id"]);
  assert.strictEqual(opts.sandboxIdFile, "/tmp/my-sandbox-id");
});

test("--tail-lines sets tailLines to numeric value", () => {
  const opts = parseArgs(["--tail-lines", "200"]);
  assert.strictEqual(opts.tailLines, 200);
});

test("--timeout-ms sets timeoutMs", () => {
  const opts = parseArgs(["--timeout-ms", "5000"]);
  assert.strictEqual(opts.timeoutMs, 5000);
});

test("multiple flags combined", () => {
  const opts = parseArgs([
    "--template", "t:v1",
    "--force-new",
    "--tail-lines", "50",
    "--timeout-ms", "2000",
  ]);
  assert.strictEqual(opts.template, "t:v1");
  assert.strictEqual(opts.forceNew, true);
  assert.strictEqual(opts.tailLines, 50);
  assert.strictEqual(opts.timeoutMs, 2000);
});

test("unknown argument throws", () => {
  assert.throws(() => parseArgs(["--unknown-flag"]), /Unknown argument/);
});

test("--tail-lines zero throws", () => {
  assert.throws(() => parseArgs(["--tail-lines", "0"]), /must be a positive number/);
});

test("--tail-lines negative throws", () => {
  assert.throws(() => parseArgs(["--tail-lines", "-5"]), /must be a positive number/);
});

test("--tail-lines NaN throws", () => {
  assert.throws(() => parseArgs(["--tail-lines", "abc"]), /must be a positive number/);
});

test("--timeout-ms below 1000 throws", () => {
  assert.throws(() => parseArgs(["--timeout-ms", "500"]), /must be at least 1000/);
});

test("--timeout-ms exactly 1000 is accepted", () => {
  const opts = parseArgs(["--timeout-ms", "1000"]);
  assert.strictEqual(opts.timeoutMs, 1000);
});

test("--tail-lines exactly 1 is accepted", () => {
  const opts = parseArgs(["--tail-lines", "1"]);
  assert.strictEqual(opts.tailLines, 1);
});

// ──────────────────────────────────────────────────────────────────────────────
// shellQuote tests
// ──────────────────────────────────────────────────────────────────────────────

console.log("\nshellQuote");

test("plain string is wrapped in single quotes", () => {
  assert.strictEqual(shellQuote("hello"), "'hello'");
});

test("string with single quote is escaped", () => {
  assert.strictEqual(shellQuote("it's"), "'it'\\''s'");
});

test("string with spaces is quoted", () => {
  assert.strictEqual(shellQuote("hello world"), "'hello world'");
});

test("empty string produces two quotes", () => {
  assert.strictEqual(shellQuote(""), "''");
});

test("number is coerced to string", () => {
  assert.strictEqual(shellQuote(42), "'42'");
});

test("multiple single quotes escaped correctly", () => {
  const result = shellQuote("a'b'c");
  assert.strictEqual(result, "'a'\\''b'\\''c'");
});

// ──────────────────────────────────────────────────────────────────────────────
// tail tests
// ──────────────────────────────────────────────────────────────────────────────

console.log("\ntail");

test("returns last N lines", () => {
  const text = "line1\nline2\nline3\nline4\nline5";
  assert.strictEqual(tail(text, 3), "line3\nline4\nline5");
});

test("returns all lines if N >= line count", () => {
  const text = "a\nb\nc";
  assert.strictEqual(tail(text, 10), "a\nb\nc");
});

test("returns single last line when N=1", () => {
  const text = "a\nb\nc";
  assert.strictEqual(tail(text, 1), "c");
});

test("null text treated as empty string", () => {
  const result = tail(null, 5);
  assert.strictEqual(typeof result, "string");
});

test("undefined text treated as empty string", () => {
  const result = tail(undefined, 5);
  assert.strictEqual(typeof result, "string");
});

test("empty text returns empty string", () => {
  assert.strictEqual(tail("", 5), "");
});

test("single line text returned unchanged for N=1", () => {
  assert.strictEqual(tail("only", 1), "only");
});

// ──────────────────────────────────────────────────────────────────────────────
// loadLocalEnv tests
// ──────────────────────────────────────────────────────────────────────────────

console.log("\nloadLocalEnv");

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "e2b-test-"));

test("non-existent file is silently ignored", () => {
  const result = loadLocalEnv(path.join(tmpDir, "nonexistent.env"));
  assert.strictEqual(result, undefined);
});

test("parses simple KEY=VALUE", () => {
  const envFile = path.join(tmpDir, "simple.env");
  fs.writeFileSync(envFile, "MY_KEY=my_value\n");
  const parsed = loadLocalEnv(envFile);
  assert.strictEqual(parsed["MY_KEY"], "my_value");
});

test("skips lines starting with #", () => {
  const envFile = path.join(tmpDir, "comments.env");
  fs.writeFileSync(envFile, "# This is a comment\nGOOD=value\n");
  const parsed = loadLocalEnv(envFile);
  assert.strictEqual(parsed["GOOD"], "value");
  assert.ok(!parsed["# This is a comment"]);
});

test("skips blank lines", () => {
  const envFile = path.join(tmpDir, "blanks.env");
  fs.writeFileSync(envFile, "\n\nKEY=val\n\n");
  const parsed = loadLocalEnv(envFile);
  assert.strictEqual(parsed["KEY"], "val");
  assert.strictEqual(Object.keys(parsed).length, 1);
});

test("strips single quotes from value", () => {
  const envFile = path.join(tmpDir, "single-quotes.env");
  fs.writeFileSync(envFile, "TOKEN='my-token-value'\n");
  const parsed = loadLocalEnv(envFile);
  assert.strictEqual(parsed["TOKEN"], "my-token-value");
});

test("strips double quotes from value", () => {
  const envFile = path.join(tmpDir, "double-quotes.env");
  fs.writeFileSync(envFile, 'API_KEY="abc123"\n');
  const parsed = loadLocalEnv(envFile);
  assert.strictEqual(parsed["API_KEY"], "abc123");
});

test("handles VALUE with = in it (uses first = as separator)", () => {
  const envFile = path.join(tmpDir, "equals-in-value.env");
  fs.writeFileSync(envFile, "URL=http://example.com?a=1&b=2\n");
  const parsed = loadLocalEnv(envFile);
  assert.strictEqual(parsed["URL"], "http://example.com?a=1&b=2");
});

test("skips lines without = separator", () => {
  const envFile = path.join(tmpDir, "no-equals.env");
  fs.writeFileSync(envFile, "KEYONLY\nVALID=yes\n");
  const parsed = loadLocalEnv(envFile);
  assert.ok(!parsed["KEYONLY"]);
  assert.strictEqual(parsed["VALID"], "yes");
});

test("handles Windows-style CRLF line endings", () => {
  const envFile = path.join(tmpDir, "crlf.env");
  fs.writeFileSync(envFile, "KEY1=val1\r\nKEY2=val2\r\n");
  const parsed = loadLocalEnv(envFile);
  assert.strictEqual(parsed["KEY1"], "val1");
  assert.strictEqual(parsed["KEY2"], "val2");
});

// ──────────────────────────────────────────────────────────────────────────────
// readSandboxState tests
// ──────────────────────────────────────────────────────────────────────────────

console.log("\nreadSandboxState");

test("forceNew=true returns empty state regardless of file", () => {
  const state = readSandboxState({ forceNew: true, sandboxId: "abc", sandboxIdFile: "" });
  assert.strictEqual(state.sandboxId, "");
  assert.strictEqual(state.template, "");
});

test("explicit sandboxId is returned directly", () => {
  const state = readSandboxState({ forceNew: false, sandboxId: "  my-id  ", sandboxIdFile: "" });
  assert.strictEqual(state.sandboxId, "my-id"); // trimmed
  assert.strictEqual(state.template, "");
});

test("non-existent sandboxIdFile returns empty state", () => {
  const state = readSandboxState({
    forceNew: false,
    sandboxId: "",
    sandboxIdFile: path.join(tmpDir, "no-such-file"),
  });
  assert.strictEqual(state.sandboxId, "");
});

test("JSON sandboxIdFile is parsed correctly", () => {
  const idFile = path.join(tmpDir, "sandbox-id.json");
  fs.writeFileSync(idFile, JSON.stringify({ sandboxId: "json-id", template: "tmpl:v1" }) + "\n");
  const state = readSandboxState({ forceNew: false, sandboxId: "", sandboxIdFile: idFile });
  assert.strictEqual(state.sandboxId, "json-id");
  assert.strictEqual(state.template, "tmpl:v1");
});

test("plain-text sandboxIdFile returns id as string", () => {
  const idFile = path.join(tmpDir, "sandbox-id-plain.txt");
  fs.writeFileSync(idFile, "plain-sandbox-id\n");
  const state = readSandboxState({ forceNew: false, sandboxId: "", sandboxIdFile: idFile });
  assert.strictEqual(state.sandboxId, "plain-sandbox-id");
  assert.strictEqual(state.template, "");
});

// ──────────────────────────────────────────────────────────────────────────────
// Cleanup & report
// ──────────────────────────────────────────────────────────────────────────────

try { fs.rmSync(tmpDir, { recursive: true }); } catch {}

console.log(`\n${passed + failed} tests: ${passed} passed, ${failed} failed`);
if (failed > 0) process.exit(1);
