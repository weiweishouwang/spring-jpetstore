# T024：开发工具准备执行记录

状态：T024已完成（2026-11-03复核）。工具安装与开发数据库已就绪；T025/T026诊断及后续工程验证单独记录。

## 已完成

- Docker Desktop已授权启动；`docker version`返回Client/Server29.5.3，Server为linux/arm64。
- `docker compose --env-file .env.dev -f infra/compose.dev.yml config --quiet`通过。
- 开发MySQL独立容器已Healthy；应用账号只读SQL返回MySQL8.4.13、jpetstore_dev、jpetstore_app，未使用root连接。
- `.env.dev`使用随机本地凭据、权限0600，并确认Git忽略。凭据不记录在报告，不输出compose完整展开配置。
- 使用已有nvm安装Node24.22.2，下载SHA256校验通过。nvm最后激活因用户.npmrc的prefix/globalconfig设置返回11，但二进制实际已安装。
- 用绝对路径验证Node v24.22.2和配套npm11.19.1成功；不修改用户全局.npmrc。
- Python3.14.4及Maven3.8.7已存在，Wrapper3.9.11将在对应任务创建。
- Git2.33.0可用；主机macOS15.3.1/arm64，工作盘可用约244GiB。
- 已从官方GitHub Release下载Temurin21.0.13+9的macOS aarch64归档和SHA256文件，校验后解压到忽略的项目目录`.tools/jdk-21.0.13+9`。没有修改系统JDK或全局默认版本。
- 实测`java -XshowSettings:properties -version`返回`java.version=21.0.13`、`java.runtime.version=21.0.13+9-LTS`、`java.vendor=Eclipse Adoptium`、`os.arch=aarch64`；同目录`javac -version`为21.0.13，Maven3.8.7在项目JAVA_HOME下识别同一JDK。
- 复核固定MySQL镜像索引包含linux/arm64及linux/amd64；开发容器为healthy，受限应用账号查询再次返回`8.4.13 / jpetstore_dev / jpetstore_app@%`。

## JDK下载与校验依据

- [官方归档](https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.13_9.tar.gz)
- [官方SHA256](https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.13_9.tar.gz.sha256.txt)
- 实际SHA256：`bf036b35b071bfb2f54f2c0af8ee8d88afa95ee6eb3dce02c0f94cfc2524d04a`，与官方文件一致。
- 原OracleJDK21.0.7仍保留。本项目使用下述项目shell配置，其他项目继续使用原环境。

## 项目shell使用方式

使用项目shell中的PATH优先级选择目标目录：

```bash
cd /Users/zhw/Documents/GitHub/spring-jpetstore
export JAVA_HOME="$PWD/.tools/jdk-21.0.13+9/Contents/Home"
export PATH="$JAVA_HOME/bin:$HOME/.nvm/versions/node/v24.22.2/bin:$PATH"
java -version
javac -version
mvn --version
node --version
npm --version
```

这里只按标准用途引用HOME，不重定义它；不执行会改写用户prefix的自动修复命令。IDE运行配置同样选目标Node路径。

## 本次复核命令

```bash
docker version --format '{{.Server.Version}} {{.Server.Os}}/{{.Server.Arch}}'
docker compose version --short
docker compose --env-file .env.dev -f infra/compose.dev.yml config --quiet
docker compose --env-file .env.dev -f infra/compose.dev.yml ps --format json
docker compose --env-file .env.dev -f infra/compose.dev.yml exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=tcp -h127.0.0.1 -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -N -e "SELECT VERSION(), DATABASE(), CURRENT_USER();"'
```

Docker需可访问Desktop socket的终端权限；受限沙箱内的permission denied不等于daemon停止。早期API403已通过官方Release直链解决，不再是T024阻碍；Adoptium版本API此次仍返回404，不作为安装依据。

## 未执行

现代backend/frontend尚未创建，尚无mvnw，未执行现代栈verify、Testcontainers或浏览器用例；仅完成工具版本、Compose、真实数据库健康与受限账号连接验证。开发数据库正在运行，数据卷保留；正常停止不执行down -v。

> 补记（2026-11-04）：本节是 2026-11-03 的工具链安装快照。此后现代栈已建好，
> `./mvnw verify`、Testcontainers 集成测试与浏览器端到端验收都已实际执行；当前状态见
> [现代化验收报告](../verification/modernization.md)。

## T017契约校验

`docs/contracts/openapi.yaml`包含19个路径、24个操作、33个schema。使用已安装Node24.22.2和固定Redocly2.3.0执行lint，退出码0；去除未使用schema后离线复跑通过。尚有1个info-license警告，未擅自为仓库补充许可证声明。此次只验证契约，不声称HTTP端点已实现。
