import { AuthModule } from './auth.module';

describe('AuthModule', () => {
  it('is defined', () => {
    expect(AuthModule).toBeDefined();
    expect(typeof AuthModule).toBe('function');
  });
});
