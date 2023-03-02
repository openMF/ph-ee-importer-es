FROM openjdk:13
WORKDIR /app
COPY build/libs/*.jar .
CMD java -jar *.jar

