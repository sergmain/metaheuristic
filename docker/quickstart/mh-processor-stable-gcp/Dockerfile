FROM alpine:latest
MAINTAINER egorov@cons411.ru

RUN apk add --update openjdk11-jre imagemagick tzdata && rm -rf /var/cache/apk/*

# Set env and set lang
ENV MUSL_LOCPATH=/usr/local/share/i18n/locales/musl
COPY /musl-locales ./musl-locales
RUN apk --update add cmake make musl-dev gcc gettext-dev libintl && \
    cd musl-locales && \
    cmake . && \
    make && \
    make install && \
    apk del cmake make musl-dev gcc gettext-dev libintl && \
    rm /var/cache/apk/*
ENV LANG=en_EN.UTF-8 \
    LANGUAGE=en_EN.UTF-8 \
    JAVA_HOME=/usr/bin/java \
    TZ=America/Los_Angeles

RUN mkdir -p /metaheuristic
WORKDIR /metaheuristic
RUN mkdir -p /logs
COPY /mh/metaheuristic.jar .
COPY /mh/config ./config
COPY /mh/processor ./processor
ENTRYPOINT ["sh", "-c", "/usr/bin/java -Dhttps.protocols=TLSv1.2 -Xrs -Xms384m -Xmx384m -jar /metaheuristic/metaheuristic.jar"]