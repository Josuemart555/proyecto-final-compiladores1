#!/bin/sh
set -e

cd /app
exec ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev}"
