#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
cd "$ROOT/backend"
exec ./mvnw -B -ntp spring-boot:run
