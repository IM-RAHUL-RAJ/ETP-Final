import {
    Injectable
} from '@nestjs/common';

import * as crypto from 'crypto';

import * as argon2 from 'argon2';

import {
    RefreshTokenRepository
} from '../repository/refresh-token.repository';


@Injectable()
export class RefreshTokenService {


    constructor(
        private repo: RefreshTokenRepository
    ) { }



    async create(
        userId: string
    ) {


        const rawToken = crypto.randomBytes(64).toString("hex");

        const hash = await argon2.hash(rawToken);



        const entity: any = {

            userId: userId,

            tokenHash: hash,

            expiresAt:
                new Date(
                    Date.now() + 7 * 24 * 60 * 60 * 1000
                )

        };


        await this.repo.save(entity);


        return rawToken;

    }

}