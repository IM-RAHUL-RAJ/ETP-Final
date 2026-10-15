export const InjectRepository = () => {
  // no-op decorator for tests
  return () => {
    return undefined;
  };
};

export const TypeOrmModule = {
  forFeature: () => ({
    module: class MockTypeOrmFeatureModule {},
    providers: [],
    exports: [],
  }),
  // minimal forRoot / forRootAsync mocks used by AppModule imports in tests
  forRoot: () => ({ module: class MockTypeOrmRootModule {} }),
  forRootAsync: () => ({ module: class MockTypeOrmRootAsyncModule {} }),
};
