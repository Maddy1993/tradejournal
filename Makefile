# Makefile for building and deploying Vert.x webapp

# Variables
DOCKER_IMAGE_NAME = vertx-webapp
DOCKER_IMAGE_TAG ?= $(shell git rev-parse --short HEAD)
DOCKER_REGISTRY = localhost:5000
PROJECT_NAME = api
VERSION = 1.0.0-SNAPSHOT
BUILD_CONTEXT = .

# Maven settings
MVN = mvn
MVN_OPTS = -DskipTests

# Docker settings
DOCKER = docker
DOCKER_OPTS = --rm
DOCKER_BUILD_OPTS = --no-cache

.PHONY: all clean package build-api-image push-image deploy

# Default target
all: clean package build-api-image push-image

# Clean the project
clean:
	$(MVN) clean

# Package the Maven project
package:
	$(MVN) $(MVN_OPTS) -pl $(PROJECT_NAME) install

# Build the Docker image
build-api-image:
	docker build -t $(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG) -f api/docker/Dockerfile api

# Tag and push the Docker image to the local Artifactory
push-image:
	docker tag $(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG) $(DOCKER_REGISTRY)/$(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG)
	docker push $(DOCKER_REGISTRY)/$(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG)

# Deploy the application (optional)
deploy:
	helm upgrade --install $(PROJECT_NAME) ./vertx-webapp

# Run all targets
run: all deploy
