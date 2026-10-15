import { configuration, frontendOrigin } from './configuration';

describe('configuration', () => {
  const originalEnv = process.env;

  const complete = {
    AUTH_SERVICE_PORT: '4000',
    FRONTEND_URL: 'http://web.example',
    FRONTEND_PORT: '4300',
    DB_HOST: 'postgres-db',
    DB_PORT: '5433',
    DB_NAME: 'test_db',
    DB_SCHEMA_AUTH: 'auth',
    DB_USER: 'test_user',
    DB_PASSWORD: 'test_password',
    JWT_SECRET: 'custom-secret-key-12345678901234567890',
    JWT_ISSUER: 'custom-issuer',
    JWT_EXPIRES_IN: '600',
  };

  beforeEach(() => {
    jest.resetModules();
    process.env = { ...originalEnv, ...complete };
  });

  afterAll(() => {
    process.env = originalEnv;
  });

  it('should load configuration from environment variables at runtime', () => {
    const config = configuration();
    expect(config.port).toBe(4000);
    expect(config.frontendOrigin).toBe('http://web.example:4300');
    expect(config.database.host).toBe('postgres-db');
    expect(config.database.port).toBe(5433);
    expect(config.database.database).toBe('test_db');
    expect(config.database.schema).toBe('auth');
    expect(config.database.user).toBe('test_user');
    expect(config.database.password).toBe('test_password');
    expect(config.auth.jwtSecret).toBe('custom-secret-key-12345678901234567890');
    expect(config.auth.jwtIssuer).toBe('custom-issuer');
    expect(config.auth.jwtExpiresIn).toBe(600);
  });

  it.each(Object.keys(complete))('refuses to start without %s instead of falling back to a default', (name) => {
    delete process.env[name];
    expect(() => configuration()).toThrow(name);
  });

  it('rejects a port that is not a number', () => {
    process.env.DB_PORT = 'abc';
    expect(() => configuration()).toThrow('DB_PORT');
  });

  it('builds the frontend origin from FRONTEND_URL and FRONTEND_PORT', () => {
    expect(frontendOrigin()).toBe('http://web.example:4300');
  });
});
