FROM gcr.io/distroless/java21-debian12@sha256:e9ed0a9d3a0114f2d471e8fbbc7fd76b80dbf59890831814281506c1e81aee43
COPY build/libs/app.jar ./

#ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Doracle.jdbc.javaNetNio=false -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787"
USER nonroot
CMD [ "app.jar" ]