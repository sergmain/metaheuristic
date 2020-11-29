FROM alpine:latest

RUN apk add --update \
    python3 \
    openjdk11 \
    git \
    wget \
  && rm -rf /var/cache/apk/*
ENV JAVA_HOME=/usr/bin/java \
    JAVA_OPTS="-Xms1g -Xmx1g"

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
    rm -r /usr/share/zoneinfo/Canada
RUN echo "America/Los_Angeles" >  /etc/timezone
ENV TZ=America/Los_Angeles

# Set the lang
ENV MUSL_LOCPATH=/usr/local/share/i18n/locales/musl
RUN apk add --update git cmake make musl-dev gcc gettext-dev libintl
RUN cd /tmp && git clone https://github.com/rilian-la-te/musl-locales.git
RUN cd /tmp/musl-locales && cmake . && make && make install
ENV LANG=en_EN.UTF-8 \
    LANGUAGE=en_EN.UTF-8

# Add ImageMagick-latest
RUN apk add --no-cache imagemagick

RUN mkdir -p /metaheuristic
WORKDIR /metaheuristic
RUN mkdir -p /logs
COPY /metaheuristic/metaheuristic.jar .
COPY /metaheuristic/config ./config
COPY /metaheuristic/processor ./processor
ENTRYPOINT ["sh", "-c", "/usr/bin/java -Dhttps.protocols=TLSv1.2 -Xrs -Xms384m -Xmx384m -jar /metaheuristic/metaheuristic.jar"]