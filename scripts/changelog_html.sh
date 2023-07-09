#!/bin/bash
PRE="<!DOCTYPE html><head><title>CHANGELOG</title></head><body><h1>SondeChaser Changelog</h1><p>"
POST="</p></body>"
CHANGE=$(cat CHANGELOG)
CHANGE=$(echo "${CHANGE}" | sed '/^VERSION/ s/$/<\/b>/')
CHANGE=$(echo "${CHANGE}" | sed '/^VERSION/ s/^/<b>/')
CHANGE=$(echo "${CHANGE}" | sed 's/$/<br>/')
echo "${PRE}${CHANGE}${POST}" > app/src/main/assets/changelog.html
