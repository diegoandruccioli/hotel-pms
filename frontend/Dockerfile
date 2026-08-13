FROM node:26-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine
# Base image already drops to a non-root "nginx" user (uid 101) by default —
# apk needs root to update the package index/upgrade packages, so switch back
# explicitly for this step, then drop to non-root again below.
USER root
RUN apk update && apk upgrade --no-cache
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
USER nginx
CMD ["nginx", "-g", "daemon off;"]
