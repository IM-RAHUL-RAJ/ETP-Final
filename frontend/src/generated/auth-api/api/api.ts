export * from './auth.service';
import { AuthService } from './auth.service';
export * from './auth.serviceInterface';
export * from './profile.service';
import { ProfileService } from './profile.service';
export * from './profile.serviceInterface';
export const APIS = [AuthService, ProfileService];
