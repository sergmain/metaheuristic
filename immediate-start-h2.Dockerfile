# ========================================================================
# !!! DO NOT FORGET TO REBUILD WITH MAVEN BEFORE BUILDING DOCKER IMAGE !!!
# ========================================================================

FROM alpine:latest

RUN addgroup -S metaheuristic && adduser -S mh -G metaheuristic

RUN apk add --update git
RUN apk add --update tzdata


FROM eclipse-temurin:17.0.7_7-jdk

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
    JAVA_HOME=/opt/java/openjdk \
    TZ=America/Los_Angeles

RUN mkdir -p /metaheuristic

COPY /distrib/metaheuristic.jar /metaheuristic
COPY /docker/quickstart/processor/* /metaheuristic/processor/
#COPY /apps/metaheuristic/src/main/resources/application-quickstart.prop /metaheuristic/config/application.properties

WORKDIR /metaheuristic
EXPOSE 8083

# java -Xms1g -Xmx1g -Dfile.encoding=UTF-8 -Dspring.profiles.active=dispatcher,h2 -DMH_HOME=/mhbp_home -jar distrib/metaheuristic.jar
ENTRYPOINT ["java", "-Dserver.port=8083", "-Dserver.address=0.0.0.0", "-Xrs", "-Xms384m", "-Xmx384m", "-Dfile.encoding=UTF-8", "-Dspring.profiles.active=dispatcher,h2", "-DMH_HOME=/metaheuristic", "-jar", "/metaheuristic/metaheuristic.jar"]


