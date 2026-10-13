# Deliberately "complex" multi-stage build with several upstream base images.
# Guardener Image Suggestions should comment on EACH FROM with its Chainguard
# equivalent (one-click where the stage has no RUN / shell entrypoint).
FROM golang:1.23 AS build-go
WORKDIR /src
RUN echo "compile the go service here"

FROM node:20 AS build-web
WORKDIR /app
RUN echo "npm ci && npm run build"

FROM python:3.12 AS api
WORKDIR /svc
RUN pip install --no-cache-dir flask gunicorn

FROM nginx:1.27
COPY --from=build-web /app/dist /usr/share/nginx/html
EXPOSE 80
