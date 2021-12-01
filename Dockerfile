FROM openjdk:13
COPY build/libs/importer-*.jar .
CMD java -jar *.jar

