import { ExecutionContext, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import * as jwt from 'jsonwebtoken';
import { JwtAuthGuard } from './jwt-auth.guard';

describe('JwtAuthGuard', () => {
  const secret = 'guard-test-secret-1234567890';
  let guard: JwtAuthGuard;

  beforeEach(() => {
    const jwtService = new JwtService();
    const configService = {
      getOrThrow: jest.fn().mockReturnValue(secret),
    } as unknown as ConfigService;

    guard = new JwtAuthGuard(jwtService, configService);
  });

  const buildContext = (authHeader?: string): ExecutionContext => {
    const req: any = { headers: {} };
    if (authHeader !== undefined) {
      req.headers.authorization = authHeader;
    }

    return {
      switchToHttp: () => ({
        getRequest: () => req,
      }),
    } as ExecutionContext;
  };

  it('a valid token is accepted', () => {
    const token = jwt.sign(
      {
        sub: 'u-1',
        accountId: 1,
        roles: ['CUSTOMER'],
      },
      secret,
      {
        algorithm: 'HS256',
        issuer: 'auth-service',
        expiresIn: '15m',
      },
    );

    const context = buildContext(`Bearer ${token}`);
    const allowed = guard.canActivate(context);

    expect(allowed).toBe(true);
  });

  it('an expired token is refused', () => {
    const now = Math.floor(Date.now() / 1000);
    const token = jwt.sign(
      {
        sub: 'u-1',
        accountId: 1,
        roles: ['CUSTOMER'],
        iat: now - 1000,
        exp: now - 100,
      },
      secret,
      {
        algorithm: 'HS256',
        issuer: 'auth-service',
        noTimestamp: true,
      },
    );

    const context = buildContext(`Bearer ${token}`);

    expect(() => guard.canActivate(context)).toThrow(UnauthorizedException);
  });

  it('a token with wrong signature is refused before any claim is read', () => {
    const tokenWithWrongKey = jwt.sign(
      {
        sub: 'u-1',
        accountId: 1,
        roles: ['CUSTOMER'],
      },
      'different-secret',
      {
        algorithm: 'HS256',
        issuer: 'auth-service',
        expiresIn: '15m',
      },
    );

    const context: any = buildContext(`Bearer ${tokenWithWrongKey}`);

    expect(() => guard.canActivate(context)).toThrow(UnauthorizedException);
    const req = context.switchToHttp().getRequest();
    expect(req.user).toBeUndefined();
  });

  it('a malformed header is refused', () => {
    const context = buildContext('MalformedHeaderWithoutBearerPrefix');

    expect(() => guard.canActivate(context)).toThrow(UnauthorizedException);
  });
});
