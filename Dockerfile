FROM ubuntu:22.04 AS build_monosat

WORKDIR /monosat
RUN apt update
RUN apt install -y git g++ openjdk-11-jdk cmake libgmp-dev zlib1g-dev
RUN git clone https://github.com/sambayless/monosat.git .
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk
RUN cmake -DJAVA=ON -DBUILD_STATIC=OFF .
RUN make


FROM maven:3.8.4-openjdk-11-slim AS build_jar

WORKDIR /jar
COPY . .
RUN mvn -DskipTests package


FROM ubuntu:22.04

WORKDIR /app
SHELL ["/bin/bash", "-c"]

RUN apt update
RUN apt install -y python3 python3-pip curl

COPY backend/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --upgrade -U -r ./requirements.txt
RUN pip install "uvicorn[standard]"
COPY --from=build_monosat /monosat/libmonosat.so ./
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
