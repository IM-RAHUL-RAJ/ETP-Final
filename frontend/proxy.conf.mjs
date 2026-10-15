// Dev-server proxy. Targets come from the repository's .env (see .env.example), never from code:
// /api and /actuator go to the order service, /auth and /health to the auth service.
import { authBase, loadEnv, orderBase, required } from './scripts/env.mjs';

const env = loadEnv();
required(env, 'ORDER_SERVICE_URL', 'ORDER_SERVICE_PORT', 'AUTH_SERVICE_URL', 'AUTH_SERVICE_PORT');

const route = (target) => ({ target, secure: false, changeOrigin: true });

export default {
  '/api': route(orderBase(env)),
  '/auth': route(authBase(env)),
  '/health': route(authBase(env)),
  '/actuator': route(orderBase(env)),
};
