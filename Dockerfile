FROM openjdk:13
COPY build/libs/*.jar .
CMD java -jar *.jar

