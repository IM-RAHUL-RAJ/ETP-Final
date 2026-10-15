// Searches the production build for anything that must never reach a browser.
// Usage: npm run build && npm run scan:bundle [extra literal values to search for...]
// Pass the literal value of your own market-data key and JWT signing secret as arguments to
// catch a value pasted in under a name the patterns below would not match.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const DIST = 'dist/frontend/browser';
const patterns = [
  /x-api-key/i,
  /api[_-]key/i,
  /fauxnance/i,
  /execute-api\.[a-z0-9-]+\.amazonaws\.com/i,
  /jwt_secret/i,
  /eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{5,}/,
  ...process.argv.slice(2).map((literal) => new RegExp(literal.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
];

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* files(path);
    else yield path;
  }
}

let hits = 0;
for (const file of files(DIST)) {
  const text = readFileSync(file, 'utf8');
  for (const pattern of patterns) {
    if (pattern.test(text)) {
      hits += 1;
      console.error(`FOUND ${pattern} in ${file}`);
    }
  }
  if (file.endsWith('.map')) {
    hits += 1;
    console.error(`FOUND source map ${file}: a .map file is the source back again`);
  }
}

if (hits > 0) {
  console.error(`Bundle scan failed: ${hits} problem(s). Treat a key that reached a bundle as published: revoke it.`);
  process.exit(1);
}
console.log('Bundle scan clean: no key, secret or market-data host in the built files.');
