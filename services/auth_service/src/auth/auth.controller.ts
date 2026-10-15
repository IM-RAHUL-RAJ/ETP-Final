import {
    Controller,
    Post,
    Body,
    UseGuards,
    Get,
    HttpCode,
    HttpStatus,
    UnauthorizedException
} from '@nestjs/common';
import {
    ApiBearerAuth,
    ApiBody,
    ApiOperation,
    ApiResponse,
    ApiTags,
} from '@nestjs/swagger';


import {
    AuthService
} from './auth.service';

import { RedactedLogger } from '../common/redacted-logger';
import { LoginCryptoService } from './services/login-crypto.service';
import { ChangePasswordRequestDto } from './dto/change-password-request.dto';


import {
    LoginRequestDto
} from './dto/login-request.dto';
import { RegisterRequestDto } from './dto/register-request.dto';
import { RefreshRequestDto } from './dto/refresh-request.dto';
import { JwtPayload } from './types/jwt-payload';
import { CurrentUser } from './decorator/current-user.decorator';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { TokenResponseDto } from './dto/token-response.dto';
import { UserResponseDto } from './dto/user-response.dto';
import { ErrorResponseDto } from './dto/error-response.dto';



@Controller("auth")
@ApiTags('Auth')
export class AuthController {


    private readonly logger =
        new RedactedLogger(AuthController.name);


    constructor(
        private service: AuthService,
        private loginCrypto: LoginCryptoService
    ) { }

    @Post("register")
    @HttpCode(HttpStatus.CREATED)
    @ApiOperation({
        operationId: 'register',
        summary: 'Register a user',
        description:
            'Creates a user and links it to an existing trading account. With no password (and an email), a default one is generated and emailed, and the user must change it on first login. Registration does not return tokens.',
    })
    @ApiBody({ type: RegisterRequestDto })
    @ApiResponse({
        status: 201,
        description: 'User created.',
        type: UserResponseDto,
    })
    @ApiResponse({
        status: 409,
        description: 'Username already taken.',
        type: ErrorResponseDto,
        schema: {
            example: {
                errorCode: 'AUTH-409',
                message: 'Registration failed',
            },
        },
    })
    @ApiResponse({
        status: 422,
        description: 'Validation failure.',
        type: ErrorResponseDto,
        schema: {
            example: {
                errorCode: 'VAL-422',
                message: 'Invalid input',
            },
        },
    })
    register(
        @Body() dto: RegisterRequestDto
    ) {
        this.logger.log(
            `[AUTH_EVENT] register_request_received username=${dto.username} accountId=${dto.accountId}`
        );

        // Do not log the password; log a redacted confirmation instead
        this.logger.logRedacted(`[AUTH_EVENT] register_request_received_hashed_password`, {
            username: dto.username,
            password: dto.password,
        });

        return this.service.register(
            dto.username,
            dto.password,
            dto.accountId,
            dto.roles,
            dto.email
        );
    }

    @Post("login")
    @HttpCode(HttpStatus.OK)
    @ApiOperation({
        operationId: 'login',
        summary: 'Log in and receive tokens',
        description:
            'Verifies credentials and returns a signed access token and opaque refresh token. Authentication failures return AUTH-401.',
    })
    @ApiBody({ type: LoginRequestDto })
    @ApiResponse({
        status: 200,
        description: 'Authenticated.',
        type: TokenResponseDto,
    })
    @ApiResponse({
        status: 401,
        description: 'Authentication failed.',
        type: ErrorResponseDto,
        schema: {
            example: {
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            },
        },
    })
    @ApiResponse({
        status: 422,
        description: 'Validation failure.',
        type: ErrorResponseDto,
        schema: {
            example: {
                errorCode: 'VAL-422',
                message: 'Invalid input',
            },
        },
    })
    login(
        @Body() dto: LoginRequestDto
    ) {

        const { username, password } = dto.payload
            ? this.loginCrypto.decrypt(dto.payload)
            : { username: dto.username, password: dto.password };

        this.logger.log(
            `[AUTH_EVENT] login_request_received username=${username}`
        );

        return this.service.login(
            username,
            password
        );

    }

    @Get('public-key')
    @ApiOperation({
        operationId: 'getLoginPublicKey',
        summary: 'Public key for encrypting login credentials',
        description:
            'Returns the base64 SPKI RSA public key the browser uses to encrypt the login payload. Regenerated whenever the service starts.',
    })
    @ApiResponse({
        status: 200,
        description: 'Public key.',
        schema: { example: { publicKey: 'MIIBIjANBgkq...' } },
    })
    publicKey() {
        return { publicKey: this.loginCrypto.getPublicKey() };
    }

    @Post('refresh')
    @HttpCode(HttpStatus.OK)
    @ApiOperation({
        operationId: 'refresh',
        summary: 'Exchange refresh token for new token pair',
        description:
            'Consumes the presented refresh token and returns a new access token and a new refresh token.',
    })
    @ApiBody({ type: RefreshRequestDto })
    @ApiResponse({
        status: 200,
        description: 'Token pair refreshed.',
        type: TokenResponseDto,
    })
    @ApiResponse({
        status: 401,
        description: 'Refresh token invalid, expired, revoked, or already consumed.',
        type: ErrorResponseDto,
        schema: {
            example: {
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            },
        },
    })
    @ApiResponse({
        status: 422,
        description: 'Validation failure.',
        type: ErrorResponseDto,
        schema: {
            example: {
                errorCode: 'VAL-422',
                message: 'Invalid input',
            },
        },
    })
    refresh(
        @Body() dto: RefreshRequestDto
    ) {
        this.logger.log(
            `[AUTH_EVENT] refresh_request_received`
        );

        return this.service.refresh(
            dto.refreshToken
        );
    }

    @Post('change-password')
    @UseGuards(JwtAuthGuard)
    @ApiBearerAuth('bearerAuth')
    @HttpCode(HttpStatus.NO_CONTENT)
    @ApiOperation({
        operationId: 'changePassword',
        summary: 'Change the signed-in user\'s password',
        description:
            'Verifies the current password, sets the new one (12 to 128 characters, different from the current), clears the must-change flag and signs out other refresh tokens.',
    })
    @ApiResponse({ status: 204, description: 'Password changed.' })
    @ApiResponse({ status: 400, description: 'Current password wrong, or new password unchanged.', type: ErrorResponseDto })
    async changePassword(
        @CurrentUser() user: JwtPayload,
        @Body() dto: ChangePasswordRequestDto,
    ): Promise<void> {
        this.logger.log(`[AUTH_EVENT] change_password_request_received userId=${user.sub}`);
        await this.service.changePassword(user.sub, dto.currentPassword, dto.newPassword);
    }

    @Get('me')
    @UseGuards(JwtAuthGuard)
    @ApiTags('Profile')
    @ApiBearerAuth('bearerAuth')
    @ApiOperation({
        operationId: 'getCurrentUser',
        summary: 'Get authenticated user profile',
        description:
            'Protected endpoint that returns the current authenticated user derived from the verified access token.',
    })
    @ApiResponse({
        status: 200,
        description: 'Authenticated user profile.',
        type: UserResponseDto,
    })
    @ApiResponse({
        status: 401,
        description: 'Missing, malformed, expired, or invalid token.',
        type: ErrorResponseDto,
        schema: {
            example: {
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            },
        },
    })
    me(
        @CurrentUser() user: JwtPayload,
    ) {
        this.logger.log(
            `[AUTH_EVENT] me_request_received userId=${user.sub}`
        );

        return this.service.getCurrentUser(user.sub);
    }

    @Get('session/validate')
    @UseGuards(JwtAuthGuard)
    @ApiBearerAuth('bearerAuth')
    @ApiOperation({
        operationId: 'validateSession',
        summary: 'Validate if current session is active',
        description:
            'Protected endpoint that validates if the sessionId in the token matches the active session for this user. Used to detect when session was invalidated on another device.',
    })
    @ApiResponse({
        status: 200,
        description: 'Session is valid.',
        schema: {
            example: {
                isValid: true,
            },
        },
    })
    @ApiResponse({
        status: 401,
        description: 'Session is invalid (was logged out from another device).',
        type: ErrorResponseDto,
        schema: {
            example: {
                errorCode: 'AUTH-401',
                message: 'Session invalidated',
            },
        },
    })
    validateSession(
        @CurrentUser() user: JwtPayload,
    ) {
        this.logger.debug(
            `[AUTH_EVENT] session_validate_request userId=${user.sub} sessionId=${user.sessionId}`
        );

        const isValid = this.service.isSessionValid(user.sub, user.sessionId);
        
        if (!isValid) {
            this.logger.warn(
                `[AUTH_EVENT] session_invalid userId=${user.sub} sessionId=${user.sessionId}`
            );
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Session invalidated',
            });
        }

        return { isValid: true };
    }

}