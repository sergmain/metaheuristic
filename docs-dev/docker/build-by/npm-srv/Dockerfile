# STEP 1 build static website
FROM node:alpine3.11 as builder
RUN apk update && \
    apk upgrade && \
    apk add --no-cache git g++ make python curl&& \
    apk update && \
    npm install -g npm@latest && \
    npm install -g @angular/cli && \
    apk update

# Create app directory
RUN mkdir -p /app
RUN cd /app && \
    git clone -b release-v6.x --recursive https://github.com/sergmain/metaheuristic-angular.git

# Install app dependencies
WORKDIR /app/metaheuristic-angular
RUN npm set progress=false && npm install

# Copy project files into the docker image
COPY mh/environment.production.ts  /app/metaheuristic-angular/src/environments
RUN node --max_old_space_size=2048 /usr/local/bin/ng build --aot --prod=true

# STEP 2 build a small nginx image with static website
FROM nginx:alpine
## Remove default nginx website
RUN rm -rf /usr/share/nginx/html/*
## From 'builder' copy website to default nginx public folder
COPY --from=builder /app/metaheuristic-angular/dist/metaheuristic-app /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]