# Quick Database Setup with Docker Compose

This directory contains Docker Compose files to set up various databases for testing purposes quickly. Docker Compose simplifies the process of creating and managing multi-container Docker applications, making it easy to spin up databases with minimal configuration.

## Table of Contents

- [Supported Databases](#supported-databases)
- [Prerequisites](#prerequisites)
- [Usage](#usage)

## Supported Databases

Currently, the following databases are supported:

1. [PostgreSQL](./postgres/docker-compose.yml)
2. [MySQL](./mysql/docker-compose.yml)

## Prerequisites

Before you can use these Docker Compose files, ensure that you have the following prerequisites installed on your system:

- [Docker](https://www.docker.com/)
- [Docker Compose](https://docs.docker.com/compose/install/)

## Usage

1. Navigate to the specific database directory you want to use, e.g., `postgres`, `mysql`.

2. Start the database container by running the following command:

   ```bash
   docker-compose up -d
   ```

3. The database service should now be up and running. You can connect to it using the provided connection details or use it in your application as needed.

4. When you are done, you can stop and remove the containers by running:

   ```bash
   docker-compose down
   ```

