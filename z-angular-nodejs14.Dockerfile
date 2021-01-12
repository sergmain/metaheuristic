# STEP 1 build static website
FROM node:14-alpine as builder

RUN echo "=== Prepare apk"
RUN apk add git

RUN echo "=== Clone metaheuristic-angular"
# Create app directory
RUN mkdir -p /app
RUN cd /app && \
    git clone -b release --recursive https://github.com/sergmain/metaheuristic-angular.git

# Install app dependencies
WORKDIR /app/metaheuristic-angular
RUN npm set progress=true && npm i

ENV PATH /home/node/app/node_modules/.bin:$PATH

RUN echo "=== Compile metaheuristic-angular"
# Copy project files into the docker image
COPY /docker/quickstart/angular/environment.production.ts  /app/metaheuristic-angular/src/environments
RUN node --max_old_space_size=2048 /usr/local/bin/npx ng build --aot --prod=true

RUN echo "=== Prepare nginx"
# STEP 2 build a small nginx image with static website
FROM nginx:alpine
## Remove default nginx website
RUN rm -rf /usr/share/nginx/html/*
## From 'builder' copy website to default nginx public folder
COPY --from=builder /app/metaheuristic-angular/dist/metaheuristic-app /usr/share/nginx/html

RUN rm -f /etc/nginx/nginx.conf
COPY /docker/quickstart/angular/nginx.conf /etc/nginx/nginx.conf
#
RUN rm -rf /app/metaheuristic-angular/*

EXPOSE 8085
CMD ["nginx", "-g", "daemon off;"]