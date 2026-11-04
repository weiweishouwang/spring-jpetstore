# 本地开发与打包的常用命令。
#
#   make package    产出本地部署用的 tar 包：后端 jar + 前端产物 + 启停脚本 + 配置模板
#   make help       列出全部目标
#
# 打包产物在 target/package/ 下（该目录已在 .gitignore 中）。
# 部署包不包含 JDK，也不包含 MySQL、Redis、pgvector 这些外部服务。

SHELL := /bin/bash
MAVEN := ./mvnw
PYTHON ?= python3
NPM ?= npm
FRONTEND := frontend
PACKAGE_DIR := target/package

.DEFAULT_GOAL := help

.PHONY: help package backend-build frontend-build backend-test frontend-test script-test test

help:
	@echo "可用目标："
	@echo "  package         打包本地部署用的 tar 包（$(PACKAGE_DIR)/）"
	@echo "  backend-build   构建后端 jar（release profile：装配前端产物）"
	@echo "  frontend-build  安装前端依赖并构建生产产物"
	@echo "  backend-test    后端 verify（单元 + 集成）"
	@echo "  frontend-test   前端 lint、typecheck 与单元测试"
	@echo "  script-test     脚本工具的单元测试"
	@echo "  test            上面三类测试全跑"

# 打包：先构建（release profile 会把前端产物装配进 jar），再组装 tar 包。
package: backend-build
	$(PYTHON) scripts/package-local.py

backend-build:
	$(MAVEN) -B -ntp -Prelease -DskipTests package

frontend-build:
	cd $(FRONTEND) && $(NPM) ci && $(NPM) run build

backend-test:
	$(MAVEN) -B -ntp verify

frontend-test:
	cd $(FRONTEND) && $(NPM) run lint && $(NPM) run typecheck && $(NPM) test

script-test:
	PYTHONDONTWRITEBYTECODE=1 $(PYTHON) -m unittest discover -s scripts/tests

test: backend-test frontend-test script-test
