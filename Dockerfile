FROM maven:3.8.4-openjdk-11-slim AS build_jar

WORKDIR /jar
COPY . .
RUN mvn -DskipTests package


FROM ubuntu:22.04

WORKDIR /app
SHELL ["/bin/bash", "-c"]

# copy shared libraries
COPY src/main/resources/lib* /lib/x86_64-linux-gnu/
RUN ln -s /lib/x86_64-linux-gnu/libmonosat.so /lib/x86_64-linux-gnu/liblibmonosat.so

RUN apt update
RUN apt install -y python3 python3-pip curl openjdk-11-jdk libgmp-dev libboost-log-dev

COPY backend/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --upgrade -U -r ./requirements.txt
COPY --from=build_jar /jar/target/*.jar ./
COPY backend .

# use nvm to install node and npm
RUN curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.5/install.sh | bash
ENV NODE_VERSION=20.10.0
ENV NVM_DIR=/root/.nvm
RUN . "$NVM_DIR/nvm.sh" && nvm install ${NODE_VERSION}
RUN . "$NVM_DIR/nvm.sh" && nvm use v${NODE_VERSION}
RUN . "$NVM_DIR/nvm.sh" && nvm alias default v${NODE_VERSION}
ENV PATH="/root/.nvm/versions/node/v${NODE_VERSION}/bin/:${PATH}"

RUN npm install -g http-server
COPY frontend/package*.json ./
RUN npm install
COPY frontend .
RUN npm run build
EXPOSE 8080
EXPOSE 8000
COPY ./run.sh .
RUN chmod +x ./run.sh
RUN mkdir result
CMD "./run.sh"
