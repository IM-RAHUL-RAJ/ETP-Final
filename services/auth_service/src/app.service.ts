import { Injectable } from '@nestjs/common';

@Injectable()
export class AppService {
  getServiceInfo() {
    return {
      service: 'auth-service',
      version: '1.0.0',
      description: 'Enterprise Trading Platform Auth Service',
    };
  }
}

