### process angular-based site
FROM node:alpine3.11 as builder

RUN apk add --update git openjdk11-jre imagemagick tzdata && rm -rf /var/cache/apk/*

RUN apk update && \
    apk upgrade && \
    apk add --no-cache git g++ make python curl& & \
    apk update && \
    npm install -g npm@latest && \
    npm install -g @angular/cli && \
    apk update

# Create app directory
RUN mkdir -p /app
RUN cd /app && \
    git clone -b release-v4.x --recursive https://github.com/sergmain/metaheuristic-angular.git

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
RUN rm -rf /app/metaheuristic-angular/*

EXPOSE 8888
CMD ["nginx", "-g", "daemon off;"]


### process metaheuristic
# Set language
ENV MUSL_LOCPATH=/usr/local/share/i18n/locales/musl
RUN apk --update add cmake make musl-dev gcc gettext-dev libintl && \
    git clone https://github.com/rilian-la-te/musl-locales.git && \
    cd musl-locales && \
    cmake . && \
    make && \
    make install && \
    apk del cmake make musl-dev gcc gettext-dev libintl && \
    rm -rf /musl-locales/ && \
    rm /var/cache/apk/*

# Set environment
ENV LANG=en_EN.UTF-8 \
    LANGUAGE=en_EN.UTF-8 \
    JAVA_HOME=/usr/bin/java \
    TZ=America/Los_Angeles

RUN mkdir -p /metaheuristic
WORKDIR /metaheuristic
RUN mkdir -p /logs
COPY /apps/metaheuristic/target/metaheuristic.jar .
COPY config ./config
COPY processor ./processor
ENTRYPOINT ["sh", "-c", "/usr/bin/java -Dhttps.protocols=TLSv1.2 -Xrs -Xms384m -Xmx384m -jar /metaheuristic/metaheuristic.jar"]

### process metaheuristic
