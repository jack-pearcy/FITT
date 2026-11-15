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

# Stage 2: build Caddy with a patched golang.org/x/net.
# The stock caddy:2-alpine binary can lag behind fixes in its Go dependencies, and
# CI fails on any fixable HIGH finding. xcaddy rebuilds the same Caddy 2.x with the
# dependency replaced, which clears CVE-2026-78661 (x/net, fixed in 0.62.0).
# Remove the --replace once caddy:2-alpine ships x/net 0.62.0 or newer.
FROM caddy:2-builder AS caddy-build
RUN xcaddy build --replace golang.org/x/net=golang.org/x/net@v0.62.0

# Stage 3: the runtime image. Stock Caddy image for its layout and entrypoint, with
# the patched binary dropped over the stock one.
FROM caddy:2-alpine

# Patch the base image's OS packages, same as the backend, so a fixable
# Alpine CVE here cannot block a publish later.
RUN apk --no-cache upgrade

COPY --from=caddy-build /usr/bin/caddy /usr/bin/caddy

# The Angular build output goes where Caddy serves static files from.
# Only the browser subfolder is the real web root for the application
# builder. Copying the parent dist/fitt-web would put an extra folder
# level in front of index.html and every asset path would 404.
COPY --from=build /app/dist/fitt-web/browser /srv
# The proxy and SPA-fallback config.
COPY Caddyfile /etc/caddy/Caddyfile
