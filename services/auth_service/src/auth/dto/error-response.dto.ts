import { ApiProperty } from '@nestjs/swagger';

export class ErrorResponseDto {
  @ApiProperty({
    description: 'Platform standard error code.',
    enum: ['AUTH-401', 'AUTH-409', 'VAL-422'],
    example: 'AUTH-401',
  })
  errorCode!: string;

  @ApiProperty({
    description: 'Error message in platform envelope.',
    example: 'Unauthorised',
  })
  message!: string;
}
