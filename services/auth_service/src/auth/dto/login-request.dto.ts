import {
  IsNotEmpty,
  IsOptional,
  IsString,
  ValidateIf,
  MaxLength,
  MinLength
} from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';


export class LoginRequestDto {

  @ApiProperty({
    description: 'Username',
    example: 'priya.menon',
    minLength: 3,
    maxLength: 64,
  })
  @ValidateIf((o: LoginRequestDto) => !o.payload)
  @IsString()
  @IsNotEmpty()
  @MinLength(3)
  @MaxLength(64)
  username!: string;


  @ApiProperty({
    description: 'Password',
    example: 'correct horse battery staple',
    minLength: 12,
    maxLength: 128,
    writeOnly: true,
  })
  @ValidateIf((o: LoginRequestDto) => !o.payload)
  @IsString()
  @IsNotEmpty()
  @MinLength(12)
  @MaxLength(128)
  password!: string;


  @ApiProperty({
    description:
      'Optional. Base64 RSA-OAEP (SHA-256) ciphertext of {"u","p","t"} encrypted with the key from GET /auth/public-key. When present, username and password are not sent in clear.',
    required: false,
    writeOnly: true,
  })
  @IsOptional()
  @IsString()
  @MaxLength(1024)
  payload?: string;
}