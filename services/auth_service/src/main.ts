import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { ValidationPipe } from '@nestjs/common';
import { AppModule } from './app.module';
import { GlobalExceptionFilter } from './auth/filters/global-exception.filter';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const configService = app.get(ConfigService);
  // The web app's origin (FRONTEND_URL:FRONTEND_PORT) is the only browser origin allowed.
  const frontendOrigin = configService.get<string>('frontendOrigin');
  app.enableCors({ origin: frontendOrigin, credentials: true });

  if ('enableCors' in app) {
    app.enableCors({
      origin: frontendOrigin,
      methods: ['GET', 'HEAD', 'PUT', 'PATCH', 'POST', 'DELETE', 'OPTIONS'],
      allowedHeaders: ['Content-Type', 'Authorization'],
    });
  }

  app.useGlobalFilters(new GlobalExceptionFilter());

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  const port = configService.get<number>('port');

  // OpenAPI Swagger Documentation
  const swaggerConfig = new DocumentBuilder()
    .setTitle('Auth service')
    .setDescription(
      'Registration, login, token refresh and current-user lookup for the Enterprise Trading Platform.',
    )
    .setVersion('1.0.0')
    .addBearerAuth(
      {
        type: 'http',
        scheme: 'bearer',
        bearerFormat: 'JWT',
        description: 'An access token issued by /auth/login or /auth/refresh.',
      },
      'bearerAuth',
    )
    .build();

  const document = SwaggerModule.createDocument(app, swaggerConfig);
  SwaggerModule.setup('docs', app, document);

  // Serve raw JSON at /docs/json as required by contract
  app.getHttpAdapter().get('/docs/json', (_req: any, res: any) => {
    res.setHeader('Content-Type', 'application/json');
    res.send(document);
  });

  await app.listen(port);
  console.log(`Auth service running on port ${port}`);
}

bootstrap();
