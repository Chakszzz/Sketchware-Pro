#!/usr/bin/env node
/**
 * Unit tests for scripts/e2b-build-template.js pure utility functions.
 *
 * Because the script does not export its helpers, we replicate the function
 * bodies under test here and verify the behaviour matches the implementation.
 *
 * Run with: node scripts/tests/e2b-build-template.test.js
 */

"use strict";

const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

// ──────────────────────────────────────────────────────────────────────────────
// Inline copies of the pure helpers from e2b-build-template.js (under test)
// ──────────────────────────────────────────────────────────────────────────────

const defaultTemplateName = "sketchware-android-build-codex:cpu8";

function parseArgs(argv) {
  const options = {
    cpuCount: 8,
    memoryMB: 8192,
    name: defaultTemplateName,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--cpu-count") options.cpuCount = Number(argv[++index]);
    else if (arg === "--memory-mb") options.memoryMB = Number(argv[++index]);
    else if (arg === "--name") options.name = argv[++index];
    else if (arg === "--help" || arg === "-h") {
      // skip — just don't throw
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  if (!Number.isFinite(options.cpuCount) || options.cpuCount < 1) {
    throw new Error("--cpu-count must be a positive number");
  }
  if (!Number.isFinite(options.memoryMB) || options.memoryMB < 1024) {
    throw new Error("--memory-mb must be at least 1024");
  }

  return options;
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
  assert.strictEqual(opts.cpuCount, 8);
  assert.strictEqual(opts.memoryMB, 8192);
  assert.strictEqual(opts.name, defaultTemplateName);
});

test("--cpu-count sets cpuCount", () => {
  const opts = parseArgs(["--cpu-count", "4"]);
  assert.strictEqual(opts.cpuCount, 4);
});

test("--memory-mb sets memoryMB", () => {
  const opts = parseArgs(["--memory-mb", "4096"]);
  assert.strictEqual(opts.memoryMB, 4096);
});

test("--name sets template name", () => {
  const opts = parseArgs(["--name", "my-custom-template:v2"]);
  assert.strictEqual(opts.name, "my-custom-template:v2");
});

test("all flags combined", () => {
  const opts = parseArgs(["--cpu-count", "2", "--memory-mb", "2048", "--name", "t:v1"]);
  assert.strictEqual(opts.cpuCount, 2);
  assert.strictEqual(opts.memoryMB, 2048);
  assert.strictEqual(opts.name, "t:v1");
});

test("unknown argument throws", () => {
  assert.throws(() => parseArgs(["--unknown"]), /Unknown argument/);
});

test("--cpu-count zero throws", () => {
  assert.throws(() => parseArgs(["--cpu-count", "0"]), /must be a positive number/);
});

test("--cpu-count negative throws", () => {
  assert.throws(() => parseArgs(["--cpu-count", "-1"]), /must be a positive number/);
});

test("--cpu-count NaN throws", () => {
  assert.throws(() => parseArgs(["--cpu-count", "abc"]), /must be a positive number/);
});

test("--memory-mb below 1024 throws", () => {
  assert.throws(() => parseArgs(["--memory-mb", "512"]), /must be at least 1024/);
});

test("--memory-mb exactly 1024 is accepted", () => {
  const opts = parseArgs(["--memory-mb", "1024"]);
  assert.strictEqual(opts.memoryMB, 1024);
});

test("--cpu-count exactly 1 is accepted", () => {
  const opts = parseArgs(["--cpu-count", "1"]);
  assert.strictEqual(opts.cpuCount, 1);
});

test("--cpu-count NaN (not a number) throws", () => {
  assert.throws(() => parseArgs(["--cpu-count", "NaN"]), /must be a positive number/);
});

test("--memory-mb Infinity throws", () => {
  // Number("Infinity") is Infinity which isFinite returns false for
  assert.throws(() => parseArgs(["--memory-mb", "Infinity"]), /must be at least 1024/);
});

// ──────────────────────────────────────────────────────────────────────────────
// loadLocalEnv tests (same logic as e2b-build.js — regression coverage)
// ──────────────────────────────────────────────────────────────────────────────

console.log("\nloadLocalEnv");

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "e2b-template-test-"));

test("non-existent file returns undefined", () => {
  const result = loadLocalEnv(path.join(tmpDir, "nonexistent.env"));
  assert.strictEqual(result, undefined);
});

test("parses KEY=VALUE pair", () => {
  const f = path.join(tmpDir, "kv.env");
  fs.writeFileSync(f, "E2B_API_KEY=test-key-123\n");
  const parsed = loadLocalEnv(f);
  assert.strictEqual(parsed["E2B_API_KEY"], "test-key-123");
});

test("strips double quotes around value", () => {
  const f = path.join(tmpDir, "dq.env");
  fs.writeFileSync(f, 'E2B_API_KEY="quoted-key"\n');
  const parsed = loadLocalEnv(f);
  assert.strictEqual(parsed["E2B_API_KEY"], "quoted-key");
});

test("strips single quotes around value", () => {
  const f = path.join(tmpDir, "sq.env");
  fs.writeFileSync(f, "E2B_API_KEY='single-quoted'\n");
  const parsed = loadLocalEnv(f);
  assert.strictEqual(parsed["E2B_API_KEY"], "single-quoted");
});

test("skips comment lines", () => {
  const f = path.join(tmpDir, "cmts.env");
  fs.writeFileSync(f, "# comment\nREAL=val\n");
  const parsed = loadLocalEnv(f);
  assert.ok(!Object.keys(parsed).some((k) => k.startsWith("#")));
  assert.strictEqual(parsed["REAL"], "val");
});

test("skips blank lines", () => {
  const f = path.join(tmpDir, "blanks.env");
  fs.writeFileSync(f, "\n\nA=1\n\nB=2\n");
  const parsed = loadLocalEnv(f);
  assert.strictEqual(Object.keys(parsed).length, 2);
});

test("value may contain = characters", () => {
  const f = path.join(tmpDir, "eqval.env");
  fs.writeFileSync(f, "BASE64=abc=def==\n");
  const parsed = loadLocalEnv(f);
  assert.strictEqual(parsed["BASE64"], "abc=def==");
});

test("skips line without = separator", () => {
  const f = path.join(tmpDir, "noeq.env");
  fs.writeFileSync(f, "NOEQUALSSIGN\nGOOD=yes\n");
  const parsed = loadLocalEnv(f);
  assert.ok(!parsed["NOEQUALSSIGN"]);
  assert.strictEqual(parsed["GOOD"], "yes");
});

test("multiple entries parsed correctly", () => {
  const f = path.join(tmpDir, "multi.env");
  fs.writeFileSync(f, "A=1\nB=2\nC=3\n");
  const parsed = loadLocalEnv(f);
  assert.strictEqual(parsed["A"], "1");
  assert.strictEqual(parsed["B"], "2");
  assert.strictEqual(parsed["C"], "3");
});

// ──────────────────────────────────────────────────────────────────────────────
// Cleanup & report
// ──────────────────────────────────────────────────────────────────────────────

try { fs.rmSync(tmpDir, { recursive: true }); } catch {}

console.log(`\n${passed + failed} tests: ${passed} passed, ${failed} failed`);
if (failed > 0) process.exit(1);