#!/usr/bin/env bash
# 统一注入本机 JDK 21 / Maven 环境（安装在用户目录，无需 root）
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-21}"
export MAVEN_HOME="${MAVEN_HOME:-$HOME/.local/opt/apache-maven-3.9.16}"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
