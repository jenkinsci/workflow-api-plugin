# Pipeline API Plugin

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/workflow-api)](https://plugins.jenkins.io/workflow-api)
[![GitHub Release](https://img.shields.io/github/v/tag/jenkinsci/workflow-api-plugin?label=changelog)](https://github.com/jenkinsci/workflow-api-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/workflow-api?color=blue)](https://plugins.jenkins.io/workflow-api)

# Introduction

Plugin that defines Pipeline API.

A component of [Pipeline
Plugin](https://plugins.jenkins.io/workflow-aggregator).

# JEP-210: External log storage for Pipeline
## Implementation
This plugin supports [https://github.com/jenkinsci/jep/tree/master/jep/210](JEP-210) which allows using one logger at a time: the pipeline logs are written and read from the same storage. The pipeline logger is automatically selected during startup through the `@Extension` annotation `ordinal` value.

## Multiple log storages
A new "Pipeline logger" section is now configurable through UI or CasC, instead of relying only on the `@Extension ordinal` value, a specific logger can be selected.

A new "Multiple loggers" log storage implementation following JEP-210 has been introduced, allowing to configure a "Primary" (for read/writes) and a "Secondary" (for writes-only) logger. This acts as a "tee" command.

# Changelog

* For new versions, see [GitHub Releases](https://github.com/jenkinsci/workflow-api-plugin/releases)
* For versions 2.41 and older, see the [CHANGELOG](https://github.com/jenkinsci/workflow-api-plugin/blob/master/CHANGELOG.md)
