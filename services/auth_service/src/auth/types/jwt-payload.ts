export interface JwtPayload {
    sub: string;
    accountId: number;
    roles: string[];
    sessionId: string;
    iat: number;
    exp: number;
    iss: string;
}