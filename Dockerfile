FROM openjdk:13
COPY target/importer-*.jar .
CMD java -jar *.jar

