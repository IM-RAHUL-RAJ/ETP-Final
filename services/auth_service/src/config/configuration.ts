export interface DatabaseSettings {
  host: string;
  port: number;
  name: string;
  schema: string;
  user: string;
  password: string;
  ssl: boolean;
  connectionString?: string;
}

export interface AuthConfig {
  jwtSecret: string;
  jwtIssuer: string;
  jwtExpiresIn: number;
}

export interface AppConfig {
  port: number;
  environment: string;
  frontendOrigin: string;
  database: DatabaseSettings;
  tradingDatabase: DatabaseSettings;
  auth: AuthConfig;
}

/**
 * Requires a non-empty environment variable.
 */
function required(name: string): string {
  const value = process.env[name];

  if (value === undefined || value.trim() === '') {
    throw new Error(
      `Missing required environment variable ${name}. Copy .env.example to .env and fill it in.`,
    );
  }

  return value;
}

function requiredInt(name: string): number {
  const value = Number.parseInt(required(name), 10);

  if (Number.isNaN(value)) {
    throw new Error(`Environment variable ${name} must be a number.`);
  }

  return value;
}

function databaseSettings(prefix = ''): DatabaseSettings {
  const get = (name: string) => required(`${prefix}${name}`);

  return {
    host: get('DB_HOST'),
    port: requiredInt(`${prefix}DB_PORT`),
    name: required(`${prefix}DB_NAME`),
    schema: required(`${prefix}DB_SCHEMA_AUTH`),
    user: required(`${prefix}DB_USER`),
    password: required(`${prefix}DB_PASSWORD`),
    ssl: process.env.DB_SSL === 'true',
  };
}

export function frontendOrigin(): string {
  return process.env.FRONTEND_URL || '*';
}

export const configuration = (): AppConfig => ({
  port: requiredInt('AUTH_SERVICE_PORT'),
  environment: process.env.NODE_ENV || 'development',
  frontendOrigin: frontendOrigin(),

  database: {
    host: required('DB_HOST'),
    port: requiredInt('DB_PORT'),
    name: required('DB_NAME'),
    schema: required('DB_SCHEMA_AUTH'),
    user: required('DB_USER'),
    password: required('DB_PASSWORD'),
    ssl:
      process.env.DB_SSL === 'true' ||
      /\.rds\.amazonaws\.com$/.test(process.env.DB_HOST || ''),
  },

  // Use the same database initially if the application requires a separate
  // trading pool. Change these values if your trading DB is separate.
  tradingDatabase: {
    host: required('DB_HOST'),
    port: requiredInt('DB_PORT'),
    name: required('DB_NAME'),
    schema: required('DB_SCHEMA_AUTH'),
    user: required('DB_USER'),
    password: required('DB_PASSWORD'),
    ssl:
      process.env.DB_SSL === 'true' ||
      /\.rds\.amazonaws\.com$/.test(process.env.DB_HOST || ''),
  },

  auth: {
    jwtSecret: required('JWT_SECRET'),
    jwtIssuer: required('JWT_ISSUER'),
    jwtExpiresIn: requiredInt('JWT_EXPIRES_IN'),
  },
});