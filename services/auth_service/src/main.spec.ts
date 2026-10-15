jest.mock('@nestjs/core', () => ({
  NestFactory: {
    create: jest.fn().mockResolvedValue({
      listen: jest.fn(),
      get: jest.fn().mockReturnValue({ get: () => 3000 }),
      useGlobalFilters: jest.fn(),
      useGlobalPipes: jest.fn(),
      getHttpAdapter: jest.fn().mockReturnValue({
        get: jest.fn(),
        post: jest.fn(),
      }),
    }),
  },
}));

// Importing main will execute bootstrap; mock ConfigService via get to avoid undefined accesses
jest.mock('@nestjs/config', () => ({
  ConfigModule: { forRoot: () => ({}) },
  ConfigService: jest.fn().mockImplementation(() => ({ get: (k: string, def: any) => def })),
}));

// Mock Swagger module to avoid interacting with real app http adapter in unit tests
jest.mock('@nestjs/swagger', () => ({
  DocumentBuilder: jest.fn().mockImplementation(() => ({
    setTitle: () => ({ setDescription: () => ({ setVersion: () => ({ addBearerAuth: () => ({ build: () => ({}) }) }) }) }),
  })),
  SwaggerModule: {
    createDocument: jest.fn().mockReturnValue({}),
    setup: jest.fn(),
  },
  // export no-op decorators used in DTOs so importing them in tests doesn't fail
  ApiProperty: (opts?: any) => {
    return (_target: any, _key?: string) => {};
  },
  ApiPropertyOptional: (opts?: any) => {
    return (_target: any, _key?: string) => {};
  },
  ApiOperation: (_opts?: any) => (_t: any, _k?: string, _d?: any) => {},
  ApiResponse: (_opts?: any) => (_t: any, _k?: string, _d?: any) => {},
  ApiTags: (_tag?: string) => (_t: any) => {},
  ApiBody: (_opts?: any) => (_t: any, _k?: string, _d?: any) => {},
  ApiBearerAuth: (_opts?: any, _name?: string) => {
    return (_target: any, _key?: string, _descriptor?: any) => {};
  },
}));

import './main';

describe('main bootstrap', () => {
  it('calls NestFactory.create', () => {
    const { NestFactory } = require('@nestjs/core');
    expect(NestFactory.create).toHaveBeenCalled();
  });
});
