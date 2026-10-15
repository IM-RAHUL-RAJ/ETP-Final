import { JwtPayload } from '../auth/types/jwt-payload';

declare global {
    namespace Express {
        interface Request {
            user?: JwtPayload;
        }
    }
}