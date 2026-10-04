ARG BASE_IMAGE=agents-pr-final-core:latest
FROM ${BASE_IMAGE}
USER root
RUN apt-get update && apt-get install -y --no-install-recommends imagemagick fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
USER agent:agent
# Capture-only dependency layer; this does not modify PR #11's runtime.
