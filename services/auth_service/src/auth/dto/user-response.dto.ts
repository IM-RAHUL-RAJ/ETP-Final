import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class UserResponseDto {
  @ApiProperty({
    description: 'User identifier (UUID), used as JWT sub claim.',
    example: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    format: 'uuid',
  })
  id!: string;

  @ApiProperty({
    description: 'Username',
    example: 'priya.menon',
  })
  username!: string;

  @ApiProperty({
    description: 'Numeric trading account identifier.',
    example: 1,
    type: 'integer',
    format: 'int64',
  })
  accountId!: number;

  @ApiProperty({
    description: 'Authorisation roles',
    example: ['CUSTOMER'],
    isArray: true,
  })
  roles!: string[];

  @ApiPropertyOptional({
    description: 'User creation timestamp in ISO-8601 format.',
    example: '2026-10-05T08:00:00Z',
    format: 'date-time',
  })
  createdOn?: string;
}
