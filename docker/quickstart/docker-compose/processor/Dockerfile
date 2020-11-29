FROM alpine:latest

RUN apk add --update git openjdk11-jre imagemagick tzdata && rm -rf /var/cache/apk/*

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
COPY metaheuristic.jar .
COPY config ./config
COPY processor ./processor
ENTRYPOINT ["sh", "-c", "/usr/bin/java -Dhttps.protocols=TLSv1.2 -Xrs -Xms384m -Xmx384m -jar /metaheuristic/metaheuristic.jar"]