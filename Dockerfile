FROM eclipse-temurin

#COPY api api
#COPY backend backend
#COPY gradle gradle
#COPY build.gradle.kts build.gradle.kts
#COPY gradlew gradlew
#COPY gradlew.bat gradlew.bat
#COPY settings.gradle.kts settings.gradle.kts
#RUN ./gradlew installDist --no-daemon

RUN mkdir /app
COPY backend/build/install/backend /app
WORKDIR /app

RUN chmod +x bin/backend

EXPOSE 5939/tcp

ENTRYPOINT ["/bin/sh", "bin/backend", "-wd", "/app/"]
