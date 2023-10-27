#!/bin/bash
curl -u qqq:123 -F "file=@source-code.yaml"  http://localhost:8080/rest/v1/dispatcher/source-code/source-code-upload-from-file