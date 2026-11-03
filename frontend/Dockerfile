# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# Các giá trị này được Next.js nhúng lúc build (rewrites và NEXT_PUBLIC_*).
ARG BACKEND_URL
ARG NEXT_PUBLIC_ENABLE_DEMO_LOGIN
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID
ENV BACKEND_URL=$BACKEND_URL \
    NEXT_PUBLIC_ENABLE_DEMO_LOGIN=$NEXT_PUBLIC_ENABLE_DEMO_LOGIN \
    NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID \
    NEXT_TELEMETRY_DISABLED=1
RUN npm run build && npm prune --omit=dev

FROM node:24-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1
COPY --from=builder --chown=node:node /app/package.json ./
COPY --from=builder --chown=node:node /app/node_modules ./node_modules
COPY --from=builder --chown=node:node /app/.next ./.next
COPY --from=builder --chown=node:node /app/public ./public
COPY --from=builder --chown=node:node /app/next.config.ts ./
USER node
EXPOSE 3000
CMD ["npm", "start"]
