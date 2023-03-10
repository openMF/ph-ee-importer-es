FROM openjdk:17-bullseye
WORKDIR /app
COPY target/*.jar ./
CMD java -jar *.jar