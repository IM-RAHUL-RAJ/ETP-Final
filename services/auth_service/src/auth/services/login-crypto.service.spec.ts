import { UnauthorizedException } from '@nestjs/common';
import { webcrypto } from 'crypto';
import { LoginCryptoService } from './login-crypto.service';

// Encrypts the way the browser does: WebCrypto RSA-OAEP with SHA-256 over the SPKI public key.
async function encryptLikeBrowser(publicKeyBase64: string, body: unknown): Promise<string> {
    const der = Buffer.from(publicKeyBase64, 'base64');
    const key = await webcrypto.subtle.importKey(
        'spki',
        der,
        { name: 'RSA-OAEP', hash: 'SHA-256' },
        false,
        ['encrypt'],
    );
    const cipher = await webcrypto.subtle.encrypt(
        { name: 'RSA-OAEP' },
        key,
        new TextEncoder().encode(JSON.stringify(body)),
    );
    return Buffer.from(cipher).toString('base64');
}

describe('LoginCryptoService', () => {
    let service: LoginCryptoService;

    beforeAll(() => {
        service = new LoginCryptoService();
    });

    it('decrypts a payload produced by the browser', async () => {
        const payload = await encryptLikeBrowser(service.getPublicKey(), {
            u: 'priya.menon',
            p: 'correct horse battery staple',
            t: Date.now(),
        });

        expect(service.decrypt(payload)).toEqual({
            username: 'priya.menon',
            password: 'correct horse battery staple',
        });
    });

    it('rejects a payload that is too old (replay)', async () => {
        const payload = await encryptLikeBrowser(service.getPublicKey(), {
            u: 'priya.menon',
            p: 'correct horse battery staple',
            t: Date.now() - 5 * 60_000,
        });

        expect(() => service.decrypt(payload)).toThrow(UnauthorizedException);
    });

    it('rejects garbage and wrong shapes with the generic 401', async () => {
        expect(() => service.decrypt('not-base64-cipher')).toThrow(UnauthorizedException);

        const wrongShape = await encryptLikeBrowser(service.getPublicKey(), { u: 1, p: 2, t: 'x' });
        expect(() => service.decrypt(wrongShape)).toThrow(UnauthorizedException);
    });

    it('rejects a payload encrypted for a different key', async () => {
        const other = new LoginCryptoService();
        const payload = await encryptLikeBrowser(other.getPublicKey(), {
            u: 'priya.menon',
            p: 'correct horse battery staple',
            t: Date.now(),
        });

        expect(() => service.decrypt(payload)).toThrow(UnauthorizedException);
    });
});
