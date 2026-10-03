ARG CORE_IMAGE=agent-i3-sandbox-core
FROM ${CORE_IMAGE}

ARG SPACEMACS_REF=491e17ba9cdcb253a3292a3049abb8767c91b9bb
USER root
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
USER agent:agent
WORKDIR /home/agent
RUN rm -rf .emacs.d \
    && git init .emacs.d \
    && git -C .emacs.d remote add origin https://github.com/syl20bnr/spacemacs \
    && git -C .emacs.d fetch --depth 1 origin "${SPACEMACS_REF}" \
    && git -C .emacs.d checkout --detach FETCH_HEAD \
    && test "$(git -C .emacs.d rev-parse HEAD)" = "${SPACEMACS_REF}"
COPY --chown=agent:agent spacemacs/.spacemacs.agent /home/agent/.spacemacs
COPY --chown=agent:agent spacemacs/.emacs /home/agent/.emacs
COPY --chown=agent:agent spacemacs/private/agent-sandbox/ /home/agent/.emacs.d/private/agent-sandbox/
ENV EMACS_DAEMON_NAME=agent-spacemacs
WORKDIR /workspace
