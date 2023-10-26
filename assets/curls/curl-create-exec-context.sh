#!/bin/bash
curl -X POST -u qqq:123 -d "sourceCodeId=<SourceCodeId>" http://localhost:8080/rest/v1/dispatcher/source-code/exec-context-add-commit
