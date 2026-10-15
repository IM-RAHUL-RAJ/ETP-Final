import {
  IsString,
  IsNotEmpty,
} from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class RefreshRequestDto {
  @ApiProperty({
    description: 'Refresh token issued by login or previous refresh.',
    example: '9c1f7a2e4b6d8e0a2c4e6a8c0e2a4c6e8a0c2e4a6c8e0a2c',
  })
  @IsString()
  @IsNotEmpty()
  refreshToken!: string;
}
