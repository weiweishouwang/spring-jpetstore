# T019：开发机支持矩阵与准备清单

目标版本见 [versions.md](../decisions/versions.md)。本次实测主机为macOS15.3.1、arm64。

| 工具 | 实测 | 目标/状态 |
| --- | --- | --- |
| Java | Oracle21.0.7；完整JDK8u351另存 | 项目Java21，目标Temurin21.0.13+9；升级尚未完成，旧基线单独用完整JDK8 |
| Maven | 3.8.7 | 后续Wrapper3.9.11，当前无mvnw |
| Node/npm | 全局v25.8.2；目标已装v24.22.2 / npm11.19.1 | 独立路径已验证，nvm激活受既有prefix设置影响 |
| Python | 3.14.4 | Python3为标准库测试/诊断最小引导前提 |
| Docker CLI | 29.5.3，darwin/arm64 | 已安装 |
| Compose | v5.1.4（docker compose子命令） | 符合Compose v2及以上命令接口要求，后续实际config验证；不降级安装旧v1 |
| Docker daemon | 启动后Server29.5.3、linux/arm64 | 已连接，开发MySQL已Healthy |

## 准备与验证

1. 保留全局旧Java，使用已有版本管理器或显式项目JAVA_HOME选择目标完整JDK21；`java -version`、`javac -version`、`mvn -version`必须指向一致工具。旧Java8浏览器插件JRE没有tools.jar，不能执行旧基线编译。
2. 用现有Node管理器安装24.22.2，或验证官方SHA256后安装用户可写目录。不要把当前Node25作为项目目标；安装后检查node/npm精确版本。
3. Docker Desktop已启动；后续通过，执行`docker version`和`docker compose version`；需要Server信息才能算daemon就绪。
4. macOS容器使用linux/arm64；Linux x64可使用相同索引的linux/amd64。Windows建议WSL2中使用Linux工具链；其他平台实际验证前只标“待验证”。
5. 后续开发Compose仅启动数据库，不要求本机第二个MySQL服务。默认拟用3306/8080/5173，启动前检查占用，不停止用户已有服务；端口可通过开发变量调整。
6. Docker需足够可用磁盘/内存；实际可用值由doctor记录，不把尚未测量的容量标为满足。网络须可访问Maven Central、npm、Node官方分发与OCI仓库；JDK官方API本次403，需在T024取得可校验下载再安装。

本文件只完成主机清单与操作依据。T024安装准备、T025/T026诊断脚本、T179基础环境和T406全栈启动仍需分别执行，未声称环境已搭好。
