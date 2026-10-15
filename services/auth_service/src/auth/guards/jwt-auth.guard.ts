import {
    CanActivate,
    ExecutionContext,
    Injectable,
    UnauthorizedException,
} from '@nestjs/common';

import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { Request } from 'express';

import { JwtPayload } from '../types/jwt-payload';

@Injectable()
export class JwtAuthGuard implements CanActivate {

    constructor(
        private readonly jwtService: JwtService,
        private readonly configService: ConfigService,
    ) {}

    canActivate(
        context: ExecutionContext,
    ): boolean {

        const request =
            context.switchToHttp().getRequest<Request>();

        const authorization =
            request.headers.authorization;

        if (!authorization) {
            throw this.unauthorized();
        }


        const parts =
            authorization.split(' ');

        if (
            parts.length !== 2 ||
            parts[0] !== 'Bearer' ||
            !parts[1]
        ) {
            throw this.unauthorized();
        }

        const token = parts[1];

        try {

            const payload =
                this.jwtService.verify<JwtPayload>(
                    token,
                    {
                        secret:
                            this.configService.getOrThrow<string>(
                                'auth.jwtSecret',
                            ),

                        algorithms: ['HS256'],
                    },
                );

            request.user = payload;

            return true;

        } catch {
            throw this.unauthorized();
        }
    }

    private unauthorized(): UnauthorizedException {

        return new UnauthorizedException({
            errorCode: 'AUTH-401',
            message: 'Unauthorised',
        });
    }
}