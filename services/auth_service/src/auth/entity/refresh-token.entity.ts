import {
   Entity,
   Column,
   PrimaryGeneratedColumn
} from 'typeorm';


@Entity("refresh_tokens")
export class RefreshToken {


   @PrimaryGeneratedColumn("uuid")
   id!: string;


   @Column({
      name: "user_id",
      type: "uuid"
   })
   userId!: string;


   @Column({
      name: "token_hash"
   })
   tokenHash!: string;


   @Column({
      name: "expires_at"
   })
   expiresAt!: Date;


   @Column({
      name: "consumed_at",
      nullable: true
   })
   consumedAt?: Date;


   @Column({
      name: "revoked_at",
      nullable: true
   })
   revokedAt?: Date;

   @Column({
      name: "created_at",
      type: "timestamptz",
      default: () => "CURRENT_TIMESTAMP"
   })
   createdAt!: Date;

}