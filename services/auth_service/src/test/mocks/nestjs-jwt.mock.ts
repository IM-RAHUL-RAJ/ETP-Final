import * as jsonwebtoken from 'jsonwebtoken';

export class JwtService {
  private readonly options: any;

  constructor(options?: any) {
    this.options = options ?? {};
  }

  sign(payload: Record<string, unknown>, options?: any): string {
    const secret = options?.secret ?? this.options.secret ?? 'test-secret';
    const signOptions = {
      ...(this.options.signOptions ?? {}),
      ...(options ?? {}),
    };

    delete signOptions.secret;
    return jsonwebtoken.sign(payload, secret, signOptions as any);
  }

  verify<T = any>(token: string, options?: any): T {
    return jsonwebtoken.verify(token, options?.secret ?? this.options.secret ?? 'test-secret', options as any) as T;
  }
}

export const JwtModule = {
  registerAsync: () => ({
    module: class MockJwtModule {},
    providers: [JwtService],
    exports: [JwtService],
  }),
};
