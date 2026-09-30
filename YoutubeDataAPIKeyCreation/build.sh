#!/bin/bash

# Linux, x86_64
CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o bin/ydakc-linux-amd64 .

# macOS, Apple Silicon
CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 go build -trimpath -ldflags="-s -w" -o bin/ydakc-macos-arm64 .

# macOS, Intel
CGO_ENABLED=0 GOOS=darwin GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o bin/ydakc-macos-amd64 .

# Windows, x64
CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o bin/ydakc.exe .

# Windows, ARM
CGO_ENABLED=0 GOOS=windows GOARCH=arm64 go build -trimpath -ldflags="-s -w" -o bin/ydakc-arm64.exe .
