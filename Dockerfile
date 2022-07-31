FROM openjdk:18-alpine AS assembled-fat-jar

WORKDIR ./http4s-app

RUN \
    apk update && \
    apk upgrade && \
    apk add --update npm && \
    apk add sbt --repository=http://dl-cdn.alpinelinux.org/alpine/edge/testing/

COPY . .

RUN sbt assembly

FROM openjdk:18-alpine

COPY --from=assembled-fat-jar /http4s-app/target/scala-2.13/reversi-app.jar .

EXPOSE 8080

CMD [ "java", "-jar" ,"reversi-app.jar" ]