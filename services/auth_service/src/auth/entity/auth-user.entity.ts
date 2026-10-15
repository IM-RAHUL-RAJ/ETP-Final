import {
  Entity,
  Column,
  PrimaryGeneratedColumn
} from 'typeorm';


@Entity("auth_users")
export class AuthUser {


  @PrimaryGeneratedColumn("uuid")
  id!: string;


  @Column({
    unique: true
  })
  username!: string;


  @Column({
    name: "password_hash"
  })
  passwordHash!: string;



  @Column({
    name: "account_id",
    type: "bigint"
  })
  accountId!: number;



  @Column({
    type: "text",
    array: true
  })
  roles!: string[];

  @Column({
    name: "created_at",
    type: "timestamptz",
    default: () => "CURRENT_TIMESTAMP"
  })
  createdAt!: Date;

}