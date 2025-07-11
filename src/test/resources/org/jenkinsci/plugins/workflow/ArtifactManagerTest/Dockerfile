FROM ubuntu:noble

# install requirements
RUN apt-get update -y && \
    apt-get install --no-install-recommends -y \
        openjdk-17-jdk-headless \
        openssh-server \
        locales

RUN mkdir -p /var/run/sshd

# create a test user
RUN useradd test -d /home/test && \
    mkdir -p /home/test/.ssh && \
    chown -R test:test /home/test && \
    echo "test:test" | chpasswd

# https://stackoverflow.com/a/38553499/12916
RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && \
    dpkg-reconfigure --frontend=noninteractive locales && \
    update-locale LANG=en_US.UTF-8
ENV LANG en_US.UTF-8

# run SSHD in the foreground with error messages to stderr
ENTRYPOINT ["/usr/sbin/sshd", "-D", "-e"]
