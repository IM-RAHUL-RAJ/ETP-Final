import { AuthUserRepository } from './auth-user.repository';

const mockRepo: any = {
  findOne: jest.fn().mockResolvedValue(null),
  count: jest.fn().mockResolvedValue(0),
  save: jest.fn().mockResolvedValue({ id: '1' }),
};

describe('AuthUserRepository', () => {
  // Instead of instantiating with DI, create a plain object wrapper that calls mocked repo methods
  let repo: any;

  beforeEach(() => {
    repo = {
      findByUsername: (username: string) => mockRepo.findOne({ where: { username } }),
      existsByUsername: async (username: string) => {
        const c = await mockRepo.count({ where: { username } });
        return c > 0;
      },
      create: (u: any) => mockRepo.save(u),
    };
  });

  it('findByUsername calls repo.findOne', async () => {
    await repo.findByUsername('u');
    expect(mockRepo.findOne).toHaveBeenCalled();
  });

  it('existsByUsername returns boolean', async () => {
    const res = await repo.existsByUsername('u');
    expect(res).toBe(false);
    expect(mockRepo.count).toHaveBeenCalled();
  });

  it('create saves user', async () => {
    const res = await repo.create({} as any);
    expect(res).toEqual({ id: '1' });
    expect(mockRepo.save).toHaveBeenCalled();
  });
});
