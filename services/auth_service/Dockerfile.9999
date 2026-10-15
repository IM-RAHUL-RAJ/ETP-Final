# Stage 1: Build
FROM node:20-alpine AS builder

WORKDIR /usr/src/app

# Install build tools for native addons like bcrypt
RUN apk add --no-cache python3 make g++

COPY package*.json ./

RUN npm ci

COPY . .

RUN npm run build && npm prune --omit=dev

# Stage 2: Production runtime
FROM node:20-alpine AS runner

WORKDIR /usr/src/app

USER node

COPY --chown=node:node package*.json ./
COPY --chown=node:node --from=builder /usr/src/app/node_modules ./node_modules
COPY --chown=node:node --from=builder /usr/src/app/dist ./dist

ENV NODE_ENV=production

# The listening port is AUTH_SERVICE_PORT (.env, passed in by docker-compose); 3000 is only the documented port.
ARG AUTH_SERVICE_PORT=3000
EXPOSE ${AUTH_SERVICE_PORT}

CMD ["node", "dist/main.js"]

