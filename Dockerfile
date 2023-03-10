FROM openjdk:17-bullseye
WORKDIR /app
COPY build/libs/*.jar ./
CMD java -jar *.jar

