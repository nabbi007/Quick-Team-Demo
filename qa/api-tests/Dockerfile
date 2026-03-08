# Use Maven with Java 17 for running tests and serving Allure reports
FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

# Install Allure command-line tool
RUN apt-get update && \
    apt-get install -y wget unzip && \
    wget -q https://github.com/allure-framework/allure2/releases/download/2.24.0/allure-2.24.0.zip && \
    unzip -q allure-2.24.0.zip && \
    rm allure-2.24.0.zip && \
    mv allure-2.24.0 /opt/allure && \
    ln -s /opt/allure/bin/allure /usr/local/bin/allure && \
    apt-get remove -y wget unzip && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code and resources
COPY src ./src

# Expose port for Allure server
EXPOSE 5050

# Create startup script
RUN echo '#!/bin/bash\n\
echo "Running tests..."\n\
mvn clean test -B || true\n\
echo "Tests completed. Starting Allure server on port 5050..."\n\
allure serve allure-results --port 5050 --host 0.0.0.0\n\
' > /app/start.sh && chmod +x /app/start.sh

# Run tests then serve Allure report
CMD ["/app/start.sh"]
