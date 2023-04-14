FROM openjdk:17-bullseye
WORKDIR /app

RUN apt update && apt install -y vim less && apt clean
COPY target/*.jar ./
CMD java -jar *.jar

