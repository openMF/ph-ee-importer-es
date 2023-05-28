FROM openjdk:17
WORKDIR /app
COPY build/libs/*.jar .
CMD java -jar *.jar

