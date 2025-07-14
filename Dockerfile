FROM gcr.io/distroless/java21
COPY build/libs/app.jar ./

#ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Doracle.jdbc.javaNetNio=false -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787"

CMD [ "app.jar" ]