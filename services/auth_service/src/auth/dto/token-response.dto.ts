import { ApiProperty } from '@nestjs/swagger';

export class TokenResponseDto {

    @ApiProperty({
        description: 'Signed JWT access token.',
        example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
    })
    accessToken!: string;

    @ApiProperty({
        description: 'Opaque refresh token for token rotation.',
        example: '9c1f7a2e4b6d8e0a2c4e6a8c0e2a4c6e8a0c2e4a6c8e0a2c',
    })
    refreshToken!: string;

    @ApiProperty({
        description: 'Token type',
        example: 'Bearer',
    })
    tokenType!: string;

    @ApiProperty({
        description: 'Access token lifetime in seconds.',
        example: 900,
    })
    expiresIn!: number;

    @ApiProperty({
        description: 'True while the user is still on the default password emailed to them and should change it.',
        example: false,
        required: false,
    })
    mustChangePassword?: boolean;
}