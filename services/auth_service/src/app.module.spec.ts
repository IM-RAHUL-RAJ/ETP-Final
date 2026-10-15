import { AppModule } from './app.module';

describe('AppModule', () => {
  it('is defined as a module class', () => {
    expect(AppModule).toBeDefined();
    expect(typeof AppModule).toBe('function');
  });
});
