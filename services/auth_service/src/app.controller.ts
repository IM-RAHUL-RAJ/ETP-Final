import { Controller, Get, UseGuards } from '@nestjs/common';
import { ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { AppService } from './app.service';
import { JwtPayload } from './auth/types/jwt-payload';
import { CurrentUser } from './auth/decorator/current-user.decorator';
import { JwtAuthGuard } from './auth/guards/jwt-auth.guard';

@ApiTags('Root')
@Controller()
export class AppController {
  constructor(private readonly appService: AppService) {}

  @Get()
  @ApiOperation({ summary: 'Service information' })
  @ApiResponse({ status: 200, description: 'Service information' })
  getServiceInfo() {
    return this.appService.getServiceInfo();
  }

}
