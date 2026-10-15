import { JwtService } from '@nestjs/jwt';
import * as jwt from 'jsonwebtoken';
import { JwtTokenService } from './jwt-token.service';

describe('JwtTokenService', () => {
  const secret = 'unit-test-jwt-secret-1234567890';
  let service: JwtTokenService;

  beforeEach(() => {
    const jwtService = new JwtService({
      secret,
      signOptions: {
        algorithm: 'HS256',
        issuer: 'auth-service',
      },
    });

    service = new JwtTokenService(jwtService);
  });

  it('a token is issued with the contract claims and expiry', () => {
    const token = service.generateAccessToken('user-123', 99, ['CUSTOMER']);
    const payload = jwt.decode(token) as jwt.JwtPayload;

    expect(payload.sub).toBe('user-123');
    expect(payload.accountId).toBe(99);
    expect(payload.roles).toEqual(['CUSTOMER']);
    expect(payload.iss).toBe('auth-service');
    expect(typeof payload.iat).toBe('number');
    expect(typeof payload.exp).toBe('number');
    expect((payload.exp as number) - (payload.iat as number)).toBe(900);
  });

  it('the token signature verifies with the service key', () => {
    const token = service.generateAccessToken('user-abc', 12, ['ADMIN']);

    const verified = jwt.verify(token, secret, {
      algorithms: ['HS256'],
      issuer: 'auth-service',
    }) as jwt.JwtPayload;

    expect(verified.sub).toBe('user-abc');
    expect(verified.accountId).toBe(12);
    expect(verified.roles).toEqual(['ADMIN']);
  });

  it('no claim outside the contract set is present', () => {
    const token = service.generateAccessToken('user-extra', 77, ['CUSTOMER']);
    const payload = jwt.decode(token) as jwt.JwtPayload;

    const keys = Object.keys(payload).sort();
    expect(keys).toEqual(['accountId', 'exp', 'iat', 'iss', 'roles', 'sub']);
  });
});
