import { Module } from '@nestjs/common';

import {
    TypeOrmModule
} from '@nestjs/typeorm';

import {
    JwtModule
} from '@nestjs/jwt';

import {
    ConfigModule,
    ConfigService
} from '@nestjs/config';


import { AuthUserRepository }
    from './repository/auth-user.repository';

import { RefreshTokenRepository }
    from './repository/refresh-token.repository';

import { JwtTokenService }
    from './services/jwt-token.service';

import { RefreshTokenService }
    from './services/refresh-token.service';


import { AuthController }
    from './auth.controller';

import { AuthService }
    from './auth.service';


import { AuthUser }
    from './entity/auth-user.entity';

import { RefreshToken }
    from './entity/refresh-token.entity';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { SessionGateway } from './gateways/session.gateway';
import { MailService } from './services/mail.service';
import { LoginCryptoService } from './services/login-crypto.service';



@Module({

    imports: [


        TypeOrmModule.forFeature([
            AuthUser,
            RefreshToken
        ]),



        JwtModule.registerAsync({

            imports: [
                ConfigModule
            ],


            inject: [
                ConfigService
            ],


            useFactory: (config: ConfigService) => ({

                secret:
                    config.get<string>('auth.jwtSecret'),


                signOptions: {
                    algorithm: 'HS256',
                    issuer: 'auth-service',
                    expiresIn: '15m'
                }

            })


        })

    ],



    controllers: [

        AuthController

    ],



    providers: [

        AuthService,

        AuthUserRepository,

        RefreshTokenRepository,

        JwtTokenService,

        RefreshTokenService,

        JwtAuthGuard,

        SessionGateway,

        LoginCryptoService,

        MailService

    ]

})

export class AuthModule { }