.DEFAULT_GOAL := help

PORT ?= 8080

.PHONY: help local run test package js-check verify

help:
	@printf "Available targets:\n"
	@printf "  make local [PORT=18080]  Run local mock profile\n"
	@printf "  make run                 Run default remote profile\n"
	@printf "  make test                Run unit tests\n"
	@printf "  make package             Build package and run tests\n"
	@printf "  make js-check            Check frontend JavaScript syntax\n"
	@printf "  make verify              Run tests and JavaScript syntax check\n"

local:
	./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=$(PORT)

run:
	./mvnw spring-boot:run

test:
	./mvnw test

package:
	./mvnw package

js-check:
	node --check src/main/resources/static/app.js

verify: test js-check
