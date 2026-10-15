import {
  IsString,
  IsNotEmpty,
  MinLength,
  MaxLength,
  Matches,
  IsNumber,
  IsEmail,
  Min,
  IsOptional,
  IsArray,
  ArrayMinSize,
} from 'class-validator';
import { Transform } from 'class-transformer';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class RegisterRequestDto {
  @ApiProperty({
    description: 'Username for the user',
    example: 'priya.menon',
    minLength: 3,
    maxLength: 64,
    pattern: '^[a-zA-Z0-9._-]+$',
  })
  @IsString()
  @IsNotEmpty()
  @MinLength(3)
  @MaxLength(64)
  @Matches(/^[a-zA-Z0-9._-]+$/, {
    message:
      'username must contain only letters, numbers, dots, underscores, and hyphens',
  })
  username!: string;

  @ApiPropertyOptional({
    description:
      'User password. Minimum 12 characters. Leave it out and provide an email: a default password is generated and emailed, and the user must change it.',
    example: 'correct horse battery staple',
    minLength: 12,
    maxLength: 128,
    writeOnly: true,
  })
  @IsOptional()
  @IsString()
  @MinLength(12)
  @MaxLength(128)
  password?: string;

  @ApiPropertyOptional({
    description:
      'Where the default password is emailed. Required when no password is given. It is not stored: the username is the identity.',
    example: 'priya.menon@example.com',
  })
  @IsOptional()
  @IsEmail()
  @MaxLength(254)
  email?: string;

  @ApiProperty({
    description: 'Numeric trading account identifier.',
    example: 1,
    minimum: 1,
    type: 'integer',
    format: 'int64',
  })
  @IsNumber()
  @IsNotEmpty()
  @Min(1)
  @Transform(({ value }) => {
    if (typeof value === 'string') {
      return parseInt(value, 10);
    }
    return value;
  })
  accountId!: number;

  @ApiPropertyOptional({
    description: 'Optional roles, defaults to CUSTOMER.',
    example: ['CUSTOMER'],
    isArray: true,
  })
  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @IsString({ each: true })
  roles?: string[];
}
