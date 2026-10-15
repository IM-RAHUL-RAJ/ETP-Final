import { Module } from '@nestjs/common';
import { join } from 'path';

import {
    ConfigModule,
    ConfigService,
} from '@nestjs/config';
import { configuration } from './config/configuration';

import {
    TypeOrmModule,
} from '@nestjs/typeorm';

import { AuthModule } from './auth/auth.module';
import { HealthController } from './health/health.controller';
import { HealthModule } from './health/health.module';

import { AuthUser } from './auth/entity/auth-user.entity';
import { RefreshToken } from './auth/entity/refresh-token.entity';


@Module({

    imports: [

        ConfigModule.forRoot({
            isGlobal: true,
            // The repository's .env (three folders up from src/ and dist/) is the one source of truth, so it comes
            // first and wins; a .env in this folder can only add variables. A variable already in the process
            // environment wins over both.
            envFilePath: [join(__dirname, '..', '..', '..', '.env'), '.env'],
            load: [configuration],
        }),


        TypeOrmModule.forRootAsync({

            imports: [
                ConfigModule,
            ],

            inject: [
                ConfigService,
            ],

            useFactory: (
                config: ConfigService,
            ) => ({

                type: 'postgres',

                host:
                    config.get<string>(
                        'database.host',
                    ),

                port:
                    config.get<number>(
                        'database.port',
                    ),

                username:
                    config.get<string>(
                        'database.user',
                    ),

                password:
                    config.get<string>(
                        'database.password',
                    ),

                database:
                    config.get<string>(
                        'database.database',
                    ),

                // One database, two schemas: the auth service works only in its own.
                schema:
                    config.get<string>(
                        'database.schema',
                    ),

                extra: {
                    options: `-c search_path=${config.get<string>('database.schema')},public`,
                },

                entities: [
                    AuthUser,
                    RefreshToken,
                ],

                synchronize: false,
            }),
        }),


        AuthModule,
        HealthModule,
    ],
    controllers: [HealthController],
})
export class AppModule {}