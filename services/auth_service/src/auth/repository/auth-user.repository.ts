import { Injectable } from '@nestjs/common';
import {
  InjectRepository
} from '@nestjs/typeorm';

import {
  Repository
} from 'typeorm';

import { AuthUser } from '../entity/auth-user.entity';


@Injectable()
export class AuthUserRepository {


  constructor(
    @InjectRepository(AuthUser)
    private repo: Repository<AuthUser>
  ) { }



  findByUsername(
    username: string
  ) {

    return this.repo.findOne({
      where: {
        username
      }
    });

  }

  async findById(id: string) {
    return this.repo.findOne({
      where: { id }
    });
  }

  async existsByUsername(username: string): Promise<boolean> {
    const count = await this.repo.count({
      where: { username }
    });
    return count > 0;
  }

  async create(authUser: AuthUser): Promise<AuthUser> {
    return this.repo.save(authUser);
  }

  async deleteById(id: string): Promise<void> {
    await this.repo.delete({ id });
  }

  async findByAccountId(accountId: number): Promise<AuthUser | null> {
    return this.repo.findOne({
      where: { accountId }
    });
  }

}