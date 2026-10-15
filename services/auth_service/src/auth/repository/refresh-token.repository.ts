import { Injectable } from '@nestjs/common';

import {
    InjectRepository
} from '@nestjs/typeorm';

import {
    Repository,
    IsNull
} from 'typeorm';

import {
    RefreshToken
} from '../entity/refresh-token.entity';

import * as argon2 from 'argon2';



@Injectable()
export class RefreshTokenRepository {


    constructor(
        @InjectRepository(RefreshToken)
        private repo: Repository<RefreshToken>
    ) { }



    save(token: RefreshToken) {

        return this.repo.save(token);

    }

    async findByTokenHash(tokenHash: string): Promise<RefreshToken | null> {
        return this.repo.findOne({
            where: { tokenHash }
        });
    }

    async findAllValidByUserId(userId: string): Promise<RefreshToken[]> {
        // Find all non-revoked tokens for a user
        return this.repo.find({
            where: {
                userId,
                revokedAt: IsNull()
            }
        });
    }

    async findByUserId(userId: string): Promise<RefreshToken[]> {
        return this.repo.find({
            where: { userId }
        });
    }

    async verifyAndFindToken(rawToken: string): Promise<RefreshToken | null> {
        // Get all valid tokens (not revoked)
        const tokens = await this.repo.find({
            where: {
                revokedAt: IsNull()
            }
        });
        
        for (const token of tokens) {
            try {
                // Verify the raw token against the stored hash
                const isValid = await argon2.verify(token.tokenHash, rawToken);
                if (isValid) {
                    return token;
                }
            } catch (error) {
                // If verification fails, continue to next token
                continue;
            }
        }
        
        return null;
    }

    async revokeAllUserTokens(userId: string): Promise<void> {
        await this.repo.update(
            { userId },
            { revokedAt: new Date() }
        );
    }

    async markAsConsumed(id: string): Promise<void> {
        await this.repo.update(
            { id },
            { consumedAt: new Date() }
        );
    }

}