FROM ubuntu:24.04

RUN apt-get update

# Install requirements
RUN apt-get install -y openjdk-17-jdk
RUN apt-get install -y git

# Clone the repository
WORKDIR /home/ubuntu
RUN git clone https://github.com/facumolina/tori
WORKDIR /home/ubuntu/tori

# Build the project
RUN ./gradlew build
RUN ./gradlew fatJar

# Clone example project
WORKDIR /home/ubuntu
RUN git clone https://github.com/apache/flink