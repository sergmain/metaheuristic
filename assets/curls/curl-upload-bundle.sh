#!/bin/bash
curl -u qqq:123 -F "file=@bundle-2023-10-21.zip"  http://localhost:8080/rest/v1/dispatcher/bundle/bundle-upload-from-file