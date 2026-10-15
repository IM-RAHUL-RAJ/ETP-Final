import { Test, TestingModule } from '@nestjs/testing';
import { ConflictException, UnauthorizedException } from '@nestjs/common';
import * as argon2 from 'argon2';
import * as crypto from 'crypto';
import { AuthService } from './auth.service';
import { AuthUserRepository } from './repository/auth-user.repository';
import { JwtTokenService } from './services/jwt-token.service';
import { RefreshTokenService } from './services/refresh-token.service';
import { RefreshTokenRepository } from './repository/refresh-token.repository';

describe('AuthService', () => {
  let service: AuthService;

  const userRepo = {
    findByUsername: jest.fn(),
    existsByUsername: jest.fn(),
    create: jest.fn(),
    findById: jest.fn(),
  };

  const jwtService = {
    generateAccessToken: jest.fn(),
  };

  const refreshService = {
    create: jest.fn(),
  };

  const refreshTokenRepo = {
    verifyAndFindToken: jest.fn(),
    revokeAllUserTokens: jest.fn(),
    markAsConsumed: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        { provide: AuthUserRepository, useValue: userRepo },
        { provide: JwtTokenService, useValue: jwtService },
        { provide: RefreshTokenService, useValue: refreshService },
        { provide: RefreshTokenRepository, useValue: refreshTokenRepo },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe('password hashing and verification', () => {
    it('correct password verifies', async () => {
      const password = 'CorrectHorseBatteryStaple123';
      const passwordHash = await argon2.hash(password, {
        type: argon2.argon2id,
        memoryCost: 65536,
        timeCost: 3,
        parallelism: 4,
      });

      userRepo.findByUsername.mockResolvedValue({
        id: 'u-1',
        username: 'priya.menon',
        passwordHash,
        accountId: 1,
        roles: ['CUSTOMER'],
      });
      jwtService.generateAccessToken.mockReturnValue('access-token-1');
      refreshService.create.mockResolvedValue('refresh-token-1');

      const result = await service.login('priya.menon', password);

      expect(result).toEqual({
        accessToken: 'access-token-1',
        refreshToken: 'refresh-token-1',
        tokenType: 'Bearer',
        expiresIn: 900,
      });
    });

    it('incorrect password fails verification', async () => {
      const passwordHash = await argon2.hash('CorrectHorseBatteryStaple123', {
        type: argon2.argon2id,
        memoryCost: 65536,
        timeCost: 3,
        parallelism: 4,
      });

      userRepo.findByUsername.mockResolvedValue({
        id: 'u-1',
        username: 'priya.menon',
        passwordHash,
        accountId: 1,
        roles: ['CUSTOMER'],
      });

      await expect(service.login('priya.menon', 'wrong-password')).rejects.toBeInstanceOf(
        UnauthorizedException,
      );
    });

    it('passwords are stored as argon2id and plaintext is never stored', async () => {
      userRepo.existsByUsername.mockResolvedValue(false);
      userRepo.create.mockImplementation(async (entity) => ({
        id: 'u-2',
        ...entity,
      }));

      const plainPassword = 'PlaintextPassword123!';

      await service.register('keerti.user', plainPassword, 42, ['CUSTOMER']);

      expect(userRepo.create).toHaveBeenCalledTimes(1);
      const savedEntity = userRepo.create.mock.calls[0][0];
      expect(savedEntity.passwordHash).toMatch(/^\$argon2id\$/);
      expect(savedEntity.passwordHash).not.toBe(plainPassword);
    });

    it('a general-purpose digest such as MD5 or SHA is not used for password storage', async () => {
      const createHashSpy = jest.spyOn(crypto, 'createHash');

      userRepo.existsByUsername.mockResolvedValue(false);
      userRepo.create.mockImplementation(async (entity) => ({
        id: 'u-3',
        ...entity,
      }));

      await service.register('hash.policy.user', 'SafePassword123!', 7, ['CUSTOMER']);

      expect(createHashSpy).not.toHaveBeenCalled();
      const savedEntity = userRepo.create.mock.calls[0][0];
      expect(savedEntity.passwordHash).toMatch(/^\$argon2id\$/);
    });

    it('unknown user and wrong password return identical status and body', async () => {
      // Ensure argon2.verify is used and returns false to simulate failure
      const verifySpy = jest.spyOn(argon2, 'verify').mockResolvedValue(false);

      // Unknown user case
      userRepo.findByUsername.mockResolvedValueOnce(null);
      let unknownResp: any;
      try {
        await service.login('no.such.user', 'somepassword');
      } catch (err: any) {
        expect(err).toBeInstanceOf(UnauthorizedException);
        unknownResp = err.getResponse();
      }

      // Wrong-password case (user exists but verify fails)
      const passwordHash = await argon2.hash('RightPassword123!', {
        type: argon2.argon2id,
        memoryCost: 65536,
        timeCost: 3,
        parallelism: 4,
      });
      userRepo.findByUsername.mockResolvedValueOnce({
        id: 'u-1',
        username: 'existing',
        passwordHash,
        accountId: 1,
        roles: ['CUSTOMER'],
      });

      let wrongResp: any;
      try {
        await service.login('existing', 'wrong-password');
      } catch (err: any) {
        expect(err).toBeInstanceOf(UnauthorizedException);
        wrongResp = err.getResponse();
      }

      expect(unknownResp).toEqual(wrongResp);
      // argon2.verify should have been called for both attempts
      expect(verifySpy).toHaveBeenCalledTimes(2);
    });

    it('unknown-user path calls argon2.verify with DUMMY_HASH', async () => {
      const verifySpy = jest.spyOn(argon2, 'verify').mockResolvedValue(false);

      userRepo.findByUsername.mockResolvedValueOnce(null);

      try {
        await service.login('no.user', 'pw');
      } catch (_err) {
        // ignore
      }

      expect(verifySpy).toHaveBeenCalledTimes(1);
      const firstArg = verifySpy.mock.calls[0][0];
      // service has a private DUMMY_HASH we can access for comparison
      const dummy = (service as any).DUMMY_HASH as string;
      expect(firstArg).toBe(dummy);
    });

    it('throttles after 5 failed attempts and stops calling argon2.verify on the 6th', async () => {
      const verifySpy = jest.spyOn(argon2, 'verify').mockResolvedValue(false);

      const passwordHash = await argon2.hash('RightPassword123!', {
        type: argon2.argon2id,
        memoryCost: 65536,
        timeCost: 3,
        parallelism: 4,
      });

      userRepo.findByUsername.mockResolvedValue({
        id: 'u-throttle',
        username: 'throttle.user',
        passwordHash,
        accountId: 2,
        roles: ['CUSTOMER'],
      });

      // Run 5 failed attempts
      for (let i = 0; i < 5; i++) {
        await expect(service.login('throttle.user', 'bad')).rejects.toBeInstanceOf(
          UnauthorizedException,
        );
      }

      // On the 6th attempt we should be throttled before calling argon2.verify
      // Spy the logger.warn to confirm throttle log event
      const warnSpy = jest.spyOn((service as any).logger, 'warn').mockImplementation();

      await expect(service.login('throttle.user', 'bad')).rejects.toBeInstanceOf(
        UnauthorizedException,
      );

      // Verify argon2.verify was called exactly 5 times (not on the throttled attempt)
      expect(verifySpy).toHaveBeenCalledTimes(5);
      expect(warnSpy).toHaveBeenCalled();
      const calledWith = warnSpy.mock.calls.find((c: any) => String(c[0]).includes('login_rejected_throttled'));
      expect(calledWith).toBeDefined();
    });

    it('duplicate username is rejected', async () => {
      userRepo.existsByUsername.mockResolvedValue(true);

      await expect(service.register('existing', 'SafePassword123!', 9)).rejects.toBeInstanceOf(
        ConflictException,
      );
    });
  });

  describe('refresh token rotation and replay handling', () => {
    beforeEach(() => {
      userRepo.findById.mockResolvedValue({
        id: 'u-refresh',
        username: 'refresh.user',
        accountId: 11,
        roles: ['CUSTOMER'],
      });
      jwtService.generateAccessToken.mockReturnValue('access-after-refresh');
      refreshService.create.mockReturnValue('new-refresh-token');
      refreshTokenRepo.markAsConsumed.mockResolvedValue(undefined);
      refreshTokenRepo.revokeAllUserTokens.mockResolvedValue(undefined);
    });

    it('refresh returns a new access token and a new refresh token', async () => {
      refreshTokenRepo.verifyAndFindToken.mockResolvedValue({
        id: 'rt-1',
        userId: 'u-refresh',
        consumedAt: null,
        revokedAt: null,
        expiresAt: new Date(Date.now() + 60_000),
      });

      const result = await service.refresh('incoming-refresh-token');

      expect(refreshTokenRepo.markAsConsumed).toHaveBeenCalledWith('rt-1');
      expect(refreshService.create).toHaveBeenCalledWith('u-refresh');
      expect(result).toEqual({
        accessToken: 'access-after-refresh',
        refreshToken: 'new-refresh-token',
        tokenType: 'Bearer',
        expiresIn: 900,
      });
    });

    it('the newly issued refresh token works', async () => {
      refreshTokenRepo.verifyAndFindToken
        .mockResolvedValueOnce({
          id: 'rt-1',
          userId: 'u-refresh',
          consumedAt: null,
          revokedAt: null,
          expiresAt: new Date(Date.now() + 60_000),
        })
        .mockResolvedValueOnce({
          id: 'rt-2',
          userId: 'u-refresh',
          consumedAt: null,
          revokedAt: null,
          expiresAt: new Date(Date.now() + 60_000),
        });

      refreshService.create
        .mockReturnValueOnce('rotated-refresh-token-1')
        .mockReturnValueOnce('rotated-refresh-token-2');

      const first = await service.refresh('initial-token');
      const second = await service.refresh(first.refreshToken);

      expect(first.refreshToken).toBe('rotated-refresh-token-1');
      expect(second.refreshToken).toBe('rotated-refresh-token-2');
      expect(refreshTokenRepo.verifyAndFindToken).toHaveBeenNthCalledWith(1, 'initial-token');
      expect(refreshTokenRepo.verifyAndFindToken).toHaveBeenNthCalledWith(
        2,
        'rotated-refresh-token-1',
      );
    });

    it('the declared revocation behaviour holds for a token that has already been exchanged', async () => {
      refreshTokenRepo.verifyAndFindToken.mockResolvedValue({
        id: 'rt-used',
        userId: 'u-refresh',
        consumedAt: new Date(),
        revokedAt: null,
        expiresAt: new Date(Date.now() + 60_000),
      });

      await expect(service.refresh('replayed-token')).rejects.toBeInstanceOf(UnauthorizedException);
      expect(refreshTokenRepo.revokeAllUserTokens).toHaveBeenCalledWith('u-refresh');
      expect(refreshTokenRepo.markAsConsumed).not.toHaveBeenCalled();
    });
  });
});
