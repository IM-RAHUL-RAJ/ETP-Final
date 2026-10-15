import { Injectable, UnauthorizedException } from '@nestjs/common';
import {
    constants,
    generateKeyPairSync,
    KeyObject,
    privateDecrypt,
} from 'crypto';

export interface DecryptedLogin {
    username: string;
    password: string;
}

/**
 * Lets the browser send the login credentials as one RSA-OAEP ciphertext, so they are not
 * readable in the request body in dev tools or a proxy log.
 *
 * The key pair lives in memory only and is regenerated at start-up. The client fetches the
 * public half from GET /auth/public-key. Every payload carries an issue time, so a captured
 * body cannot be replayed later. This is obscuring, not a substitute for TLS.
 */
@Injectable()
export class LoginCryptoService {
    private static readonly MAX_AGE_MS = 60_000;

    private readonly privateKey: KeyObject;
    private readonly publicKeyBase64: string;

    constructor() {
        const { publicKey, privateKey } = generateKeyPairSync('rsa', {
            modulusLength: 3072,
        });
        this.privateKey = privateKey;
        this.publicKeyBase64 = publicKey
            .export({ type: 'spki', format: 'der' })
            .toString('base64');
    }

    /** SPKI public key, base64 (DER), ready for WebCrypto importKey('spki'). */
    getPublicKey(): string {
        return this.publicKeyBase64;
    }

    decrypt(payload: string): DecryptedLogin {
        try {
            const plain = privateDecrypt(
                {
                    key: this.privateKey,
                    padding: constants.RSA_PKCS1_OAEP_PADDING,
                    oaepHash: 'sha256',
                },
                Buffer.from(payload, 'base64'),
            ).toString('utf8');

            const parsed = JSON.parse(plain) as {
                u?: unknown;
                p?: unknown;
                t?: unknown;
            };

            if (
                typeof parsed.u !== 'string' ||
                typeof parsed.p !== 'string' ||
                typeof parsed.t !== 'number' ||
                Math.abs(Date.now() - parsed.t) > LoginCryptoService.MAX_AGE_MS ||
                parsed.u.length < 3 ||
                parsed.u.length > 64 ||
                parsed.p.length < 1 ||
                parsed.p.length > 128
            ) {
                throw new Error('invalid payload');
            }

            return { username: parsed.u, password: parsed.p };
        } catch {
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            });
        }
    }
}
