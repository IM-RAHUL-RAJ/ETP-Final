import { BadRequestException, ServiceUnavailableException } from '@nestjs/common';
import * as argon2 from 'argon2';
import { AuthService, MUST_CHANGE_PASSWORD_ROLE } from './auth.service';
import { AuthUser } from './entity/auth-user.entity';

describe('AuthService default password and change password', () => {
    let userRepo: any;
    let refreshTokenRepo: any;
    let mail: any;
    let service: AuthService;

    beforeEach(() => {
        userRepo = {
            existsByUsername: jest.fn().mockResolvedValue(false),
            create: jest.fn().mockImplementation(async (user: AuthUser) => ({ ...user, id: 'u-1' })),
            deleteById: jest.fn().mockResolvedValue(undefined),
            findById: jest.fn(),
        };
        refreshTokenRepo = { revokeAllUserTokens: jest.fn().mockResolvedValue(undefined) };
        mail = { isConfigured: jest.fn().mockReturnValue(true), sendWelcome: jest.fn().mockResolvedValue(undefined) };
        service = new AuthService(userRepo, {} as any, {} as any, refreshTokenRepo, {} as any, mail);
    });

    describe('register without a password', () => {
        it('generates a default password, emails it, flags the user, and never returns it', async () => {
            const result = await service.register('priya.menon', undefined, 7, undefined, 'priya.menon@example.com');

            expect(mail.sendWelcome).toHaveBeenCalledTimes(1);
            const [to, , username, password] = mail.sendWelcome.mock.calls[0];
            expect(to).toBe('priya.menon@example.com');
            expect(username).toBe('priya.menon');
            expect(password).toHaveLength(16);
            expect(password).toMatch(/[A-Z]/);
            expect(password).toMatch(/[a-z]/);
            expect(password).toMatch(/[0-9]/);
            expect(password).toMatch(/[!@#$%^&*?]/);

            const saved: AuthUser = userRepo.create.mock.calls[0][0];
            expect(saved.roles).toEqual(['CUSTOMER', MUST_CHANGE_PASSWORD_ROLE]);
            expect(JSON.stringify(saved)).not.toContain('priya.menon@example.com');
            expect(await argon2.verify(saved.passwordHash, password)).toBe(true);

            expect(result.emailSent).toBe(true);
            expect(JSON.stringify(result)).not.toContain(password);
        });

        it('needs an email', async () => {
            await expect(service.register('priya.menon', undefined, 7)).rejects.toBeInstanceOf(BadRequestException);
            expect(userRepo.create).not.toHaveBeenCalled();
        });

        it('refuses up front, creating nothing, when email is not configured', async () => {
            mail.isConfigured.mockReturnValue(false);

            await expect(service.register('priya.menon', undefined, 7, undefined, 'p@example.com')).rejects.toBeInstanceOf(
                ServiceUnavailableException,
            );
            expect(userRepo.create).not.toHaveBeenCalled();
        });

        it('removes the user again when the email cannot be sent', async () => {
            mail.sendWelcome.mockRejectedValue(new Error('smtp down'));

            await expect(service.register('priya.menon', undefined, 7, undefined, 'p@example.com')).rejects.toBeInstanceOf(
                ServiceUnavailableException,
            );
            expect(userRepo.deleteById).toHaveBeenCalledWith('u-1');
        });
    });

    it('register with an explicit password sends no email and sets no flag', async () => {
        const result = await service.register('admin.user', 'a-long-enough-password', 1, ['ADMIN']);

        expect(mail.sendWelcome).not.toHaveBeenCalled();
        expect(userRepo.create.mock.calls[0][0].roles).toEqual(['ADMIN']);
        expect(result.emailSent).toBe(false);
    });

    describe('changePassword', () => {
        let user: AuthUser;

        beforeEach(async () => {
            user = Object.assign(new AuthUser(), {
                id: 'u-1',
                username: 'priya.menon',
                passwordHash: await argon2.hash('Temp-Password-123'),
                roles: ['CUSTOMER', MUST_CHANGE_PASSWORD_ROLE],
            });
            userRepo.findById.mockResolvedValue(user);
        });

        it('replaces the password, clears the flag and signs out refresh tokens', async () => {
            await service.changePassword('u-1', 'Temp-Password-123', 'A-brand-new-password-9');

            const saved: AuthUser = userRepo.create.mock.calls[0][0];
            expect(saved.roles).toEqual(['CUSTOMER']);
            expect(await argon2.verify(saved.passwordHash, 'A-brand-new-password-9')).toBe(true);
            expect(refreshTokenRepo.revokeAllUserTokens).toHaveBeenCalledWith('u-1');
        });

        it('rejects a wrong current password with a 400, not a 401', async () => {
            await expect(service.changePassword('u-1', 'not-the-password', 'A-brand-new-password-9')).rejects.toBeInstanceOf(
                BadRequestException,
            );
            expect(userRepo.create).not.toHaveBeenCalled();
        });

        it('rejects a new password equal to the current one', async () => {
            await expect(service.changePassword('u-1', 'Temp-Password-123', 'Temp-Password-123')).rejects.toBeInstanceOf(
                BadRequestException,
            );
        });
    });
});
