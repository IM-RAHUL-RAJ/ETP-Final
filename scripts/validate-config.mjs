#!/usr/bin/env node
// Validates the platform configuration before a build or deployment:  node scripts/validate-config.mjs
//
//   1. .env and .env.example list exactly the same variables
//   2. every variable the code, compose file and scripts read is listed in .env.example
//   3. every value from the deployment PDF is present (ports, hosts, database, Kafka) and agrees with
//      config/application.yaml
//   4. no required value is empty or still a placeholder
//   5. no hardcoded host, IP address, port URL or credential is left in the source code
//
// Exits with a non-zero status when anything fails, so it can run in CI.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const problems = [];
const fail = (message) => problems.push(message);
const read = (file) => readFileSync(resolve(root, file), 'utf8');

function parseEnv(file) {
  const values = {};
  for (const raw of read(file).split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq > 0) values[line.slice(0, eq).trim()] = line.slice(eq + 1);
  }
  return values;
}

// ---- the deployment PDF ------------------------------------------------------------------------
const PDF = {
  FRONTEND_URL: 'http://localhost', FRONTEND_PORT: '4200',
  AUTH_SERVICE_URL: 'http://localhost', AUTH_SERVICE_PORT: '3000',
  ORDER_SERVICE_URL: 'http://localhost', ORDER_SERVICE_PORT: '8081',
  EXECUTOR_SERVICE_URL: 'http://localhost', EXECUTOR_SERVICE_PORT: '8082',
  DB_HOST: 'localhost', DB_PORT: '5432', DB_NAME: 'trading_system_db', DB_USER: 'postgres',
  KAFKA_HOST: 'kafka', KAFKA_PORT: '29092',
};

// Secrets and optional integrations that may legitimately be empty in .env.example.
const MAY_BE_EMPTY = new Set(['FAUXNANCE_API_KEY', 'SMTP_HOST', 'SMTP_USER', 'SMTP_PASS', 'SMTP_FROM', 'GEMINI_API_KEY', 'SONAR_TOKEN', 'ADMIN_PASSWORD']);

const example = parseEnv('.env.example');
const hasEnv = existsSync(resolve(root, '.env'));
const actual = hasEnv ? parseEnv('.env') : null;
if (!hasEnv) fail('.env is missing: copy .env.example to .env at the repository root');

// 1. both files list the same variables
if (actual) {
  for (const key of Object.keys(example)) if (!(key in actual)) fail(`.env is missing ${key} (listed in .env.example)`);
  for (const key of Object.keys(actual)) if (!(key in example)) fail(`.env.example is missing ${key} (set in .env)`);
}

// 2. every variable that is read is listed
const tracked = execFileSync('git', ['ls-files'], { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).split('\n').filter(Boolean);
const isTest = (f) => /(\.spec\.ts|Test\.java|IT\.java|\/src\/test\/|\/tests\/|\/e2e\/|\/generated\/)/.test(f);
const referenced = new Map();
const note = (name, file) => { if (!referenced.has(name)) referenced.set(name, file); };

for (const file of tracked) {
  if (isTest(file) || !existsSync(resolve(root, file))) continue;
  const text = /\.(properties|yml|yaml|ts|mjs|js|sh|py|java)$/.test(file) || file.endsWith('Dockerfile')
    ? read(file) : null;
  if (text === null) continue;
  const code = text.split(/\r?\n/).filter((l) => !l.trim().startsWith('#')).join('\n');
  if (file.endsWith('application.properties')) for (const m of code.matchAll(/\$\{([A-Z][A-Z0-9_]+)/g)) note(m[1], file);
  if (file === 'docker-compose.yml') for (const m of code.matchAll(/\$\{([A-Z][A-Z0-9_]+)/g)) note(m[1], file);
  if (file === 'services/auth_service/src/config/configuration.ts' || file === 'services/auth_service/scripts/create-admin.ts') {
    for (const m of text.matchAll(/(?:required|requiredInt)\('([A-Z][A-Z0-9_]+)'\)/g)) note(m[1], file);
    for (const m of text.matchAll(/process\.env\.([A-Z][A-Z0-9_]+)/g)) if (m[1] !== 'NODE_ENV') note(m[1], file);
  }
  if (file.startsWith('services/auth_service/src/auth/services/mail.service.ts')) {
    for (const m of text.matchAll(/config\.get<[a-z]+>\('([A-Z][A-Z0-9_]+)'\)/g)) note(m[1], file);
  }
  if (file.startsWith('frontend/scripts/') || file === 'frontend/proxy.conf.mjs') {
    for (const m of text.matchAll(/(?:required\(env, |env\.)'?([A-Z][A-Z0-9_]+)/g)) if (/_/.test(m[1])) note(m[1], file);
  }
}
for (const [name, file] of referenced) {
  if (!(name in example)) fail(`${name} is read in ${file} but not listed in .env.example`);
}

// 3. deployment PDF values
for (const [key, expected] of Object.entries(PDF)) {
  if (example[key] !== expected) fail(`.env.example: ${key} should be ${expected} (deployment PDF), found ${example[key] ?? 'nothing'}`);
  if (actual && actual[key] !== expected) fail(`.env: ${key} should be ${expected} (deployment PDF), found ${actual[key] ?? 'nothing'}`);
}
const yaml = read('config/application.yaml');
const yamlHas = (block, key, expected) => {
  const section = yaml.match(new RegExp(`^${block}:\\n((?:[ #].*\\n?)*)`, 'm'));
  const found = section && section[1].match(new RegExp(`^\\s+${key}:\\s*([^\\s#]+)`, 'm'));
  if (!found || found[1] !== expected) fail(`config/application.yaml: ${block}.${key} should be ${expected}, found ${found ? found[1] : 'nothing'}`);
};
const svc = (name, urlKey, portKey) => {
  const section = yaml.match(new RegExp(`^  ${name}:\\n((?:    .*\\n)+)`, 'm'));
  const url = section && section[1].match(/^\s+url:\s*(\S+)/m);
  const port = section && section[1].match(/^\s+port:\s*(\d+)/m);
  if (!url || url[1] !== PDF[urlKey]) fail(`config/application.yaml: services.${name}.url should be ${PDF[urlKey]}`);
  if (!port || port[1] !== PDF[portKey]) fail(`config/application.yaml: services.${name}.port should be ${PDF[portKey]}`);
};
yamlHas('frontend', 'url', PDF.FRONTEND_URL);
yamlHas('frontend', 'port', PDF.FRONTEND_PORT);
svc('auth', 'AUTH_SERVICE_URL', 'AUTH_SERVICE_PORT');
svc('order', 'ORDER_SERVICE_URL', 'ORDER_SERVICE_PORT');
svc('executor', 'EXECUTOR_SERVICE_URL', 'EXECUTOR_SERVICE_PORT');
yamlHas('database', 'host', PDF.DB_HOST);
yamlHas('database', 'port', PDF.DB_PORT);
yamlHas('database', 'name', PDF.DB_NAME);
yamlHas('database', 'user', PDF.DB_USER);
yamlHas('kafka', 'host', PDF.KAFKA_HOST);
yamlHas('kafka', 'port', PDF.KAFKA_PORT);

// 4. no empty values / placeholders
const PLACEHOLDER = /^(PASTE|CHANGEME|CHANGE_ME|TODO|TBD|<.*>|your[-_ ]|xxx)/i;
for (const [label, values] of [['.env.example', example], ['.env', actual]]) {
  if (!values) continue;
  for (const [key, value] of Object.entries(values)) {
    if (value === '' && !MAY_BE_EMPTY.has(key)) fail(`${label}: ${key} is empty`);
    if (value && PLACEHOLDER.test(value)) fail(`${label}: ${key} is still a placeholder (${value})`);
  }
}
if (example.JWT_SECRET && example.JWT_SECRET.length < 32) fail('.env.example: JWT_SECRET must be at least 32 characters');
if (actual?.JWT_SECRET && actual.JWT_SECRET.length < 32) fail('.env: JWT_SECRET must be at least 32 characters');

// 5. nothing hardcoded in the source
const SOURCE = /^(services\/[^/]+\/(src\/main|src)\/|services\/[^/]+\/scripts\/|frontend\/src\/(app|environments)\/|frontend\/scripts\/|frontend\/proxy|ETL_Layer\/src\/|infrastructure\/|databases\/PostgreSQL\/scripts\/|databases\/postgreSQL\/scripts\/)/;
const HARDCODED = [
  [/https?:\/\/(localhost|127\.0\.0\.1|\d{1,3}(\.\d{1,3}){3})/, 'a hardcoded local/IP URL'],
  [/\b(?:\d{1,3}\.){3}\d{1,3}:\d{2,5}\b/, 'a hardcoded IP:port'],
  [/\b(localhost|kafka|postgres):\d{4,5}\b/, 'a hardcoded host:port'],
  [/(fnx_[A-Za-z0-9]{8,}|AIza[0-9A-Za-z_-]{20,}|\bAQ\.[A-Za-z0-9_-]{20,}|lctafmtezmfvoopx)/, 'a committed secret'],
  [/springboot_db/, 'the old database name'],
];
const ALLOW = [/environments\/environment(\.prod)?\.ts$/, /\.spec\.ts$/, /\/generated\//, /README\.md$/, /\.md$/, /\.svg$/];
for (const file of tracked) {
  if (!SOURCE.test(file) || isTest(file) || ALLOW.some((re) => re.test(file)) || !existsSync(resolve(root, file))) continue;
  if (!/\.(java|ts|mjs|js|py|sh|properties|xml|json|yml)$/.test(file) && !file.endsWith('Dockerfile')) continue;
  const lines = read(file).split(/\r?\n/);
  lines.forEach((line, i) => {
    const code = line.trim();
    if (code.startsWith('//') || code.startsWith('#') || code.startsWith('*') || code.startsWith('/*')) return;
    for (const [re, what] of HARDCODED) if (re.test(line)) fail(`${file}:${i + 1} contains ${what}`);
  });
}

if (problems.length) {
  console.error(`Configuration check FAILED (${problems.length}):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log(`Configuration check passed: ${Object.keys(example).length} variables, ${referenced.size} referenced in code, deployment PDF values verified.`);
