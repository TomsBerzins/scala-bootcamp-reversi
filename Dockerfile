FROM openjdk:18-alpine AS assembled-fat-jar

WORKDIR ./http4s-build

RUN \
    apk update && \
    apk upgrade && \
    apk add --update npm && \
    apk add sbt --repository=http://dl-cdn.alpinelinux.org/alpine/edge/testing/

COPY . .

RUN sbt assembly

FROM openjdk:18-alpine

WORKDIR /reversi-app

RUN addgroup http4s-user-group && \
    adduser http4s-user -G http4s-user-group -D -g "" && \
    chown http4s-user:http4s-user-group -R /reversi-app

USER http4s-user

COPY --chown=http4s-user:http4s-user-group --from=assembled-fat-jar /http4s-build/target/scala-2.13/reversi-app.jar .

EXPOSE 8080

CMD [ "java", "-jar" ,"reversi-app.jar" ]