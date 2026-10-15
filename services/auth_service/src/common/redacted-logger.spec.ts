import { RedactedLogger } from './redacted-logger';

describe('RedactedLogger', () => {
  let logger: RedactedLogger;

  beforeEach(() => {
    logger = new RedactedLogger('RedactedLoggerSpec');
  });

  it('redacts sensitive fields by key name at any depth', () => {
    const logSpy = jest.spyOn(logger, 'log').mockImplementation();

    logger.logRedacted('[TEST]', {
      username: 'keerti',
      password: 'plain-password',
      nested: {
        refreshToken: 'raw-token',
      },
      list: [{ accessToken: 'access-token' }],
    });

    const output = logSpy.mock.calls[0][0] as string;

    expect(output).toContain('***REDACTED***');
    expect(output).not.toContain('plain-password');
    expect(output).not.toContain('raw-token');
    expect(output).not.toContain('access-token');
  });
});
