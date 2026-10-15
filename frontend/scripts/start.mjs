// Starts the Angular dev server on FRONTEND_PORT (from the repository's .env), proxying as set in proxy.conf.js.
// Extra arguments are passed on to `ng serve`, e.g. `npm start -- --open`.
import { spawn } from 'node:child_process';
import { frontendRoot, loadEnv, required } from './env.mjs';

const env = loadEnv();
required(env, 'FRONTEND_PORT');

const ng = spawn('npx', ['ng', 'serve', '--port', env.FRONTEND_PORT, ...process.argv.slice(2)], {
  cwd: frontendRoot,
  stdio: 'inherit',
  shell: true,
});
ng.on('exit', (code) => process.exit(code ?? 0));
