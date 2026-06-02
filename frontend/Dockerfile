# Stage 1: build the Angular app into static files.
# Node 22 is the current LTS line. Alpine keeps the build image small.
FROM node:22-alpine AS build

WORKDIR /app

# Copy only the manifest files first so the npm install layer caches.
# Dependencies change far less often than source code, so this layer
# gets reused on most rebuilds and only the later copy busts the cache.
COPY package.json package-lock.json ./

# npm ci installs the exact versions from package-lock.json.
# It is the reproducible-build install, unlike npm install which can
# drift the lockfile.
RUN npm ci

# Now copy the rest of the source. This is the layer that changes most.
COPY . .

# Production build. The application builder writes the servable output
# to dist/frontend/browser, not dist/frontend.
RUN npm run build -- --configuration production

# Stage 2: the runtime image. Stock Caddy serves the files and proxies.
FROM caddy:2-alpine

# The Angular build output goes where Caddy serves static files from.
# Only the browser subfolder is the real web root for the application
# builder. Copying the parent dist/frontend would put an extra folder
# level in front of index.html and every asset path would 404.
COPY --from=build /app/dist/frontend/browser /srv

# The proxy and SPA-fallback config.
COPY Caddyfile /etc/caddy/Caddyfile
