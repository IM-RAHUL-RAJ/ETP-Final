import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { RedactedLogger } from '../common/redacted-logger';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { LoginCryptoService } from './services/login-crypto.service';

describe('AuthController', () => {
  let controller: AuthController;

  const service = {
    register: jest.fn(),
    login: jest.fn(),
    refresh: jest.fn(),
    getCurrentUser: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [
        { provide: AuthService, useValue: service },
        { provide: LoginCryptoService, useValue: { decrypt: jest.fn(), getPublicKey: jest.fn() } },
        { provide: JwtService, useValue: { verify: jest.fn() } },
        { provide: ConfigService, useValue: { getOrThrow: jest.fn().mockReturnValue('test-secret') } },
        {
          provide: JwtAuthGuard,
          useValue: {
            canActivate: jest.fn().mockReturnValue(true),
          },
        },
      ],
    }).compile();

    controller = module.get<AuthController>(AuthController);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  it('register delegates to service and does not log plaintext password', async () => {
    const logSpy = jest.spyOn(RedactedLogger.prototype, 'log').mockImplementation();
    const logRedactedSpy = jest.spyOn(RedactedLogger.prototype, 'logRedacted');

    service.register.mockResolvedValue({ id: 'u1' });

    await controller.register({
      username: 'priya.menon',
      password: 'NeverLogThisPlaintext123',
      accountId: 1,
      roles: ['CUSTOMER'],
    });

    expect(service.register).toHaveBeenCalledWith(
      'priya.menon',
      'NeverLogThisPlaintext123',
      1,
      ['CUSTOMER'],
    );

    const combinedLogText = logSpy.mock.calls.map((c) => String(c[0])).join(' ');
    expect(combinedLogText).not.toContain('NeverLogThisPlaintext123');
    expect(logRedactedSpy).toHaveBeenCalled();
  });

  it('login delegates to service', async () => {
    service.login.mockResolvedValue({ accessToken: 'a', refreshToken: 'r' });

    await controller.login({ username: 'priya.menon', password: 'p@ss' });

    expect(service.login).toHaveBeenCalledWith('priya.menon', 'p@ss');
  });

  it('refresh delegates to service', async () => {
    service.refresh.mockResolvedValue({ accessToken: 'a2', refreshToken: 'r2' });

    await controller.refresh({ refreshToken: 'raw-refresh-token' });

    expect(service.refresh).toHaveBeenCalledWith('raw-refresh-token');
  });

  it('me delegates using verified user claims', async () => {
    service.getCurrentUser.mockResolvedValue({ id: 'u1' });

    await controller.me({
      sub: 'u1',
      accountId: 1,
      roles: ['CUSTOMER'],
      iat: 100,
      exp: 200,
      iss: 'auth-service',
    });

    expect(service.getCurrentUser).toHaveBeenCalledWith('u1');
  });
});
