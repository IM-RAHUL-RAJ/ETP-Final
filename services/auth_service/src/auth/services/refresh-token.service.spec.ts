import * as argon2 from 'argon2';
import { RefreshTokenService } from './refresh-token.service';

describe('RefreshTokenService', () => {
  const repo = {
    save: jest.fn(),
  };

  let service: RefreshTokenService;

  beforeEach(() => {
    jest.clearAllMocks();
    service = new RefreshTokenService(repo as any);
  });

  it('stores a hash of the refresh token rather than the raw token', async () => {
    repo.save.mockResolvedValue(undefined);

    const rawToken = await service.create('user-1');

    expect(repo.save).toHaveBeenCalledTimes(1);
    const saved = repo.save.mock.calls[0][0];

    expect(saved.userId).toBe('user-1');
    expect(saved.tokenHash).toBeDefined();
    expect(saved.tokenHash).not.toBe(rawToken);
    expect(saved.expiresAt).toBeInstanceOf(Date);

    const verifies = await argon2.verify(saved.tokenHash, rawToken);
    expect(verifies).toBe(true);
  });
});
