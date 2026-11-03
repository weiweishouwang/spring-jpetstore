# T019：开发机支持矩阵与准备清单

目标版本见 [versions.md](../decisions/versions.md)。本次实测主机为macOS15.3.1、arm64；以下状态于2026-11-03完成Phase 1时复核，安装依据见[toolchain-setup.md](toolchain-setup.md)。

| 工具 | 实测 | 目标/状态 |
| --- | --- | --- |
| Java | 项目Temurin21.0.13+9 / javac21.0.13；原Oracle21.0.7与完整JDK8u351保留 | 目标JDK已在忽略的.tools目录安装并校验；旧基线单独用完整JDK8 |
| Maven | 3.8.7 | 后续Wrapper3.9.11，当前无mvnw |
| Node/npm | 全局v25.8.2；目标已装v24.22.2 / npm11.19.1 | 独立路径已验证，nvm激活受既有prefix设置影响 |
| Python | 3.14.4 | Python3为标准库测试/诊断最小引导前提 |
| Docker CLI | 29.5.3，darwin/arm64 | 已安装 |
| Compose | v5.1.4（docker compose子命令） | 符合Compose v2及以上命令接口要求，实际config验证已通过 |
| Docker daemon | 启动后Server29.5.3、linux/arm64 | 已连接，开发MySQL已Healthy |

## 准备与验证

1. 保留全局旧Java，使用已有版本管理器或显式项目JAVA_HOME选择目标完整JDK21；`java -version`、`javac -version`、`mvn -version`必须指向一致工具。旧Java8浏览器插件JRE没有tools.jar，不能执行旧基线编译。
2. 用现有Node管理器安装24.22.2，或验证官方SHA256后安装用户可写目录。不要把当前Node25作为项目目标；安装后检查node/npm精确版本。
3. Docker Desktop已启动；执行`docker version`和`docker compose version`验证；需要Server信息才能算daemon就绪。受限沙箱可能缺少socket权限，应在获授权终端执行诊断。
4. macOS容器使用linux/arm64；Linux x64可使用相同索引的linux/amd64。Windows建议WSL2中使用Linux工具链；其他平台实际验证前只标“待验证”。
5. 后续开发Compose仅启动数据库，不要求本机第二个MySQL服务。默认拟用3306/8080/5173，启动前检查占用，不停止用户已有服务；端口可通过开发变量调整。
6. 实际诊断可用磁盘243.9GiB、Docker内存7.7GiB。doctor默认以10GiB磁盘、4GiB容器内存作开发准备检查阈值，可通过参数调整；这些值不是应用容量或生产性能承诺。网络须可访问Maven Central、npm、Node官方分发与OCI仓库；此前JDK API阻碍已通过官方Release直链及SHA256校验解决。

Phase 1的T019～T026已完成；T179工程基础验证和T406全栈启动仍待后续执行。安装工具和诊断通过不代表现代业务应用已经可运行。
