FROM fnproject/fn-java-fdk-build:jdk11-1.0.157 as build-stage
WORKDIR /function
ENV MAVEN_OPTS -Dhttp.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyHost= -Dhttps.proxyPort= -Dhttp.nonProxyHosts=localhost|127.0.0.1|.oracle.com|.oraclecorp.com|.grungy.us|docker.internal -Dmaven.repo.local=/usr/share/maven/ref/repository

# Copy lib directory with shared JAR
ADD lib /function/lib

# Copy pom.xml and download dependencies
ADD pom.xml /function/pom.xml
RUN ["mvn", "package", "dependency:copy-dependencies", "-DincludeScope=runtime", "-DskipTests=true", "-Dmdep.prependGroupId=true", "-DoutputDirectory=target", "--fail-never"]

# Copy source and build
ADD src /function/src
RUN ["mvn", "package"]

# Extract shared library classes
RUN mkdir -p /function/shared_classes && \
    cd /function/shared_classes && \
    jar xf /function/lib/functions-shared-1.0.0-SNAPSHOT.jar

FROM fnproject/fn-java-fdk:jre11-1.0.157
WORKDIR /function
# Copy shared library classes and application JAR
COPY --from=build-stage /function/shared_classes /function/app/
COPY --from=build-stage /function/target/sync-processor-*.jar /function/app/
# Copy all dependency JARs (Maven creates them with groupId prefix)
COPY --from=build-stage /function/target/*.jar /function/app/
CMD ["com.zenith.trade.SyncProcessorFunction::handleRequest"]
