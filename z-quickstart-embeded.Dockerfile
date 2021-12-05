# ========================================================================
# !!! DO NOT FORGET TO REBUILD WITH MAVEN BEFORE BUILDING DOCKER IMAGE !!!
# ========================================================================

FROM alpine:latest

RUN addgroup -S metaheuristic && adduser -S mh -G metaheuristic

RUN apk add --update git tzdata
#RUN apk add --update git openjdk11-jre tzdata && rm -rf /var/cache/apk/*

FROM eclipse-temurin:17.0.1_12-jdk
#RUN apk add --update eclipse-temurin:17.0.1_12-jdk


# Set language
#ENV MUSL_LOCPATH=/usr/local/share/i18n/locales/musl
##RUN apk --update add cmake make musl-dev gcc gettext-dev libintl && \
#RUN apk git clone -b 76fcf3c822b77a987657f0832c873c465b842438 https://github.com/rilian-la-te/musl-locales.git && \
#    cd musl-locales && \
#    cmake . && \
#    make && \
#    make install && \
#    apk del cmake make musl-dev gcc gettext-dev libintl && \
#    rm -rf /musl-locales/
#    && \
#    rm /var/cache/apk/*

# Set environment
ENV LANG=en_EN.UTF-8 \
    LANGUAGE=en_EN.UTF-8 \
    JAVA_HOME=/usr/bin/java \
    TZ=America/Los_Angeles

RUN mkdir -p /metaheuristic
RUN mkdir -p /metaheuristic/logs
RUN mkdir -p /metaheuristic/config
RUN mkdir -p /metaheuristic/mh-processor
RUN mkdir -p /metaheuristic/mh-dispatcher

COPY /apps/metaheuristic/target/metaheuristic.jar /metaheuristic
COPY /docker/quickstart/processor/* /metaheuristic/mh-processor/
COPY /apps/metaheuristic/src/main/resources/application-quickstart.prop /metaheuristic/config/application.properties

WORKDIR /metaheuristic
EXPOSE 8083

ENTRYPOINT ["java", "-Dserver.port=8083", "-Dserver.address=0.0.0.0", "-Xrs", "-Xms384m", "-Xmx384m", "-jar", "/metaheuristic/metaheuristic.jar"]
#ENTRYPOINT ["sh", "-c", "/usr/bin/java -Dserver.port=8083 -Dspring.profiles.active=quickstart,dispatcher,processor -Dhttps.protocols=TLSv1.2 -Xrs -Xms384m -Xmx384m -jar /metaheuristic/metaheuristic.jar"]

