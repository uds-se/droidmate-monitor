# DroidMate-2 ![GNU GPL v3](https://www.gnu.org/graphics/gplv3-88x31.png)[![Build Status](https://travis-ci.org/natanieljr/droidmate-monitor.svg?branch=master)](https://travis-ci.org/natanieljr/droidmate-monitor)

DroidMate-2, an automated execution generator for Android apps.  
Copyright (C) 2012-2018 Saarland University

This program is free software. 

* www.droidmate.org  

##### Current Maintainers

* Nataniel Borges Jr. `<nataniel dot borges at cispa dot saarland>`
* Jenny Hotzkow `<jenny dot hotzkow at cispa dot saarland>`

Date of last full review of this document: 07 Aug 2018

# Introduction

This packages builds an APK file responsible for intercepting API calls between the application under test and the Android operating system.

## Repository structure:

Following directories are sources which can be opened  as IntelliJ projects (`File -> Open`):

| project in `repo/dev`| description |
| ------- | ----------- |
| droidmate-monitor | main sources of DroidMate-Monitor genetator. |
| src/main/resources/monitorApk | java Apk project which is used to generate the API monitor |

## This repository is part of the [DroidMate-2](https://github.com/uds-se/droidmate) project. For information about building, running or extending DroidMate, check our [wiki](https://github.com/uds-se/droidmate/wiki) ###


##### Former Maintainers #####

* Konrad Jamrozik `<jamrozik at st dot cs dot uni-saarland dot de>`
