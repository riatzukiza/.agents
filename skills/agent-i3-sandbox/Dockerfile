FROM ubuntu:20.04

ARG TARGETARCH
ARG ESPANSO_VERSION=2.1.4-beta
ARG ESPANSO_SHA256=55b1977ccd77d2cc46870af181c41a3650169058daff7538601572acf7befdb2

ENV DEBIAN_FRONTEND=noninteractive \
    DISPLAY=:99 \
    HOME=/home/agent \
    XDG_RUNTIME_DIR=/tmp/runtime-agent \
    XDG_CONFIG_HOME=/home/agent/.config \
    TERM=xterm \
    EMACS_DAEMON_NAME=agent-core

RUN test "${TARGETARCH:-amd64}" = "amd64" || { echo "Espanso ${ESPANSO_VERSION} X11 fixture supports linux/amd64 only; got ${TARGETARCH:-unknown}" >&2; exit 1; }
RUN apt-get update && apt-get install -y --no-install-recommends \
      bash ca-certificates curl dbus-x11 emacs i3-wm jq libnotify-bin libwxbase3.0-0v5 libwxgtk3.0-gtk3-0v5 procps systemd x11-utils xclip xdotool xvfb xterm \
    && rm -rf /var/lib/apt/lists/*
RUN curl --fail --location --retry 3 --output /tmp/espanso.deb \
      "https://github.com/espanso/espanso/releases/download/v${ESPANSO_VERSION}/espanso-debian-x11-amd64.deb" \
    && echo "${ESPANSO_SHA256}  /tmp/espanso.deb" | sha256sum --check --strict \
    && dpkg --install /tmp/espanso.deb \
    && rm /tmp/espanso.deb \
    && espanso --help >/tmp/espanso-help.txt \
    && grep -Eq '(^|[[:space:]])(start|status|stop)([[:space:]]|$)' /tmp/espanso-help.txt

RUN useradd --create-home --shell /bin/bash --uid 1000 agent \
    && install -d --mode=1777 /tmp/.X11-unix \
    && install -d --owner=agent --group=agent /workspace /tmp/runtime-agent /home/agent/.config/i3 /home/agent/.config/espanso/match /home/agent/.emacs.d /home/agent/bin
COPY --chown=agent:agent i3.config /home/agent/.config/i3/config
RUN touch /home/agent/.agent-sandbox-container
COPY --chown=agent:agent emacs/init.el /home/agent/.emacs.d/init.el
COPY --chown=agent:agent espanso/ /home/agent/.config/espanso/
COPY --chown=agent:agent scripts/ /home/agent/bin/
RUN chmod 0755 /home/agent/bin/*

USER agent:agent
WORKDIR /workspace
ENTRYPOINT ["/home/agent/bin/agent-entrypoint"]
