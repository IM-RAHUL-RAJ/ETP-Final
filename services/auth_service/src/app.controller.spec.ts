import { Test, TestingModule } from '@nestjs/testing';
import { AppController } from './app.controller';
import { AppService } from './app.service';

describe('AppController', () => {
  let appController: AppController;

  beforeEach(async () => {
    const app: TestingModule = await Test.createTestingModule({
      controllers: [AppController],
      providers: [AppService],
    }).compile();

    appController = app.get<AppController>(AppController);
  });

  it('should return service info', () => {
    expect(appController.getServiceInfo()).toEqual({
      service: 'auth-service',
      version: '1.0.0',
      description: 'Enterprise Trading Platform Auth Service',
    });
  });
});

