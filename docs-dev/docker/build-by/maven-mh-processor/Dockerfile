FROM alpine:latest

# STEP 1 build metaheuristic app
FROM alpine:latest as builder
LABEL stage=builder
RUN apk update && \
    apk upgrade && \
    apk add --no-cache git openjdk11 python3 maven curl&& \
    apk update

# Create app directory
RUN mkdir -p /app
ARG SHA=LATEST
RUN cd /app && \
    git clone -b release-v4.x --recursive https://github.com/sergmain/metaheuristic.git


RUN cd /app/metaheuristic && \
    mvn -Dmaven.repo.local=/M2/repo clean install -f pom.xml -Dmaven.test.skip=true

# STEP 2 build an image with processor
FROM alpine:latest
RUN apk add --update \
    openjdk11 \
    git \
    wget \
    cmake \
    make \
    musl-dev \
    gcc \
    gettext-dev \
    libintl \
  && rm -rf /var/cache/apk/*
ENV JAVA_HOME=/usr/bin/java

# Set the timezone
RUN apk add tzdata
RUN cp /usr/share/zoneinfo/America/Los_Angeles /etc/localtime
RUN rm -r /usr/share/zoneinfo/Africa && \
    rm -r /usr/share/zoneinfo/Antarctica && \
    rm -r /usr/share/zoneinfo/Arctic && \
    rm -r /usr/share/zoneinfo/Asia && \
    rm -r /usr/share/zoneinfo/Atlantic && \
    rm -r /usr/share/zoneinfo/Australia && \
    rm -r /usr/share/zoneinfo/Indian && \
    rm -r /usr/share/zoneinfo/Mexico && \
    rm -r /usr/share/zoneinfo/Pacific && \
    rm -r /usr/share/zoneinfo/Chile && \
    rm -r /usr/share/zoneinfo/Canada && \
    rm -rf /var/cache/apk/*
RUN echo "America/Los_Angeles" >  /etc/timezone
ENV TZ=America/Los_Angeles

# Set the lang
ENV MUSL_LOCPATH=/usr/local/share/i18n/locales/musl
RUN cd /tmp && git clone https://github.com/rilian-la-te/musl-locales.git
RUN cd /tmp/musl-locales && cmake . && make && make install
ENV LANG=en_EN.UTF-8 \
    LANGUAGE=en_EN.UTF-8

# Add ImageMagick-latest
RUN apk add --no-cache imagemagick

RUN mkdir -p /metaheuristic
WORKDIR /metaheuristic
RUN mkdir -p /logs
COPY --from=builder /app/metaheuristic/apps/metaheuristic/target/metaheuristic.jar .
COPY /metaheuristic/config ./config
COPY /metaheuristic/processor ./processor
ENTRYPOINT ["sh", "-c", "/usr/bin/java -Dhttps.protocols=TLSv1.2 -Xrs -Xms384m -Xmx384m -jar /metaheuristic/metaheuristic.jar"]