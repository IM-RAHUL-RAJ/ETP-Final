// Reads the platform configuration for the frontend tooling.
//
// Values come from, in this order: the process environment, the repository's .env, and finally
// .env.example (so a fresh clone builds with the documented defaults). Nothing is written in code.
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const repoRoot = resolve(frontendRoot, '..');

function parse(file) {
  const values = {};
  for (const raw of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq < 1) continue;
    values[line.slice(0, eq).trim()] = line.slice(eq + 1);
  }
  return values;
}

export function loadEnv() {
  const files = ['.env.example', '.env'].map((name) => resolve(repoRoot, name)).filter(existsSync);
  const fromFiles = files.reduce((all, file) => ({ ...all, ...parse(file) }), {});
  return { ...fromFiles, ...process.env };
}

export function required(env, ...names) {
  const missing = names.filter((name) => !env[name]);
  if (missing.length) {
    throw new Error(`Missing ${missing.join(', ')}. Copy .env.example to .env at the repository root.`);
  }
}

export const authBase = (env) => `${env.AUTH_SERVICE_URL}:${env.AUTH_SERVICE_PORT}`;
export const orderBase = (env) => `${env.ORDER_SERVICE_URL}:${env.ORDER_SERVICE_PORT}`;
