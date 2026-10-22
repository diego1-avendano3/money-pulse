# Single-stage on purpose: unlike the Java services, `vite preview` needs
# Node + vite at runtime anyway (this isn't a static bundle served by nginx),
# so a separate runtime stage wouldn't save much. This is a lab setup, not
# production hosting -- the real PWA host is S3+CloudFront in Phase 1.
FROM node:22-slim
# curl is needed by compose.yaml's healthcheck for this service.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build
EXPOSE 5173
CMD ["npm", "run", "preview", "--", "--host", "0.0.0.0", "--port", "5173"]
