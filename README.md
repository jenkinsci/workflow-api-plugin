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
This plugin provides APIs for [https://github.com/jenkinsci/jep/tree/master/jep/210](JEP-210), which allow plugins to take over Pipeline build logging.
The default logging implementation is the `@Extension LogStorageFactoryDescriptor` with the highest `ordinal` value that implements `LogStorageFactoryDescriptor.getDefaultInstance`.
You can override the default implementation by configuring a logger explicitly under "Pipeline logger" on the Jenkins system configuration page.

## Multiple loggers
In some cases, you may want to use a logging implementation that sends logs to an external system, while also preserving logs in Jenkins for other use cases.
You can accomplish by configuring the "Multiple loggers" implementation in the "Pipeline logger" section on the Jenkins system configuration page.
This implementation allows you to select a "Primary" logger that handles reads and writes, as well as a "Secondary" logger which receives copies of all writes, similarly to the Unix `tee` command.

# Changelog

* For new versions, see [GitHub Releases](https://github.com/jenkinsci/workflow-api-plugin/releases)
* For versions 2.41 and older, see the [CHANGELOG](https://github.com/jenkinsci/workflow-api-plugin/blob/master/CHANGELOG.md)
