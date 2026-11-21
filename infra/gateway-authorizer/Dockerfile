FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B verify
FROM public.ecr.aws/lambda/java:21
COPY --from=build /build/target/authorizer.jar ${LAMBDA_TASK_ROOT}/lib/authorizer.jar
USER 10001:10001
CMD ["cl.eventpass.gateway.Authorizer::handleRequest"]
