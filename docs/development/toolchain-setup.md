# T024：开发工具准备执行记录

状态：进行中，尚未完成。此报告不将已有工具误记为已达目标版本。

## 已完成

- Docker Desktop已授权启动；`docker version`返回Client/Server29.5.3，Server为linux/arm64。
- `docker compose --env-file .env.dev -f infra/compose.dev.yml config --quiet`通过。
- 开发MySQL独立容器已Healthy；应用账号只读SQL返回MySQL8.4.13、jpetstore_dev、jpetstore_app，未使用root连接。
- `.env.dev`使用随机本地凭据、权限0600，并确认Git忽略。凭据不记录在报告，不输出compose完整展开配置。
- 使用已有nvm安装Node24.22.2，下载SHA256校验通过。nvm最后激活因用户.npmrc的prefix/globalconfig设置返回11，但二进制实际已安装。
- 用绝对路径验证Node v24.22.2和配套npm11.19.1成功；不修改用户全局.npmrc。
- Python3.14.4及Maven3.8.7已存在，Wrapper3.9.11将在对应任务创建。

## Node项目使用方式

使用项目shell中的PATH优先级选择目标目录：

```bash
export PATH="$HOME/.nvm/versions/node/v24.22.2/bin:$PATH"
node --version
npm --version
```

这里只按标准用途引用HOME，不重定义它；不执行会改写用户prefix的自动修复命令。IDE运行配置同样选目标Node路径。

## 当前阻碍

本机完整OracleJDK21.0.7可运行，但未达到锁定的Temurin21.0.13+9。Adoptium API及官方GitHub release API返回HTTP403，目标安装包尚未取得/校验/安装；T024不能勾选，也不能声称目标工具链验证完成。后续应从可访问官方分发取得对应macOS arm64 JDK及校验后继续，不静默改用Java25或把旧补丁声明为目标已就绪。

## 未执行

现代backend/frontend尚未创建，尚无mvnw，未执行现代栈verify、Testcontainers或浏览器用例；仅完成工具版本、Compose、真实数据库健康与受限账号连接验证。开发数据库正在运行，数据卷保留；正常停止不执行down -v。

## T017契约校验

`docs/contracts/openapi.yaml`包含19个路径、24个操作、33个schema。使用已安装Node24.22.2和固定Redocly2.3.0执行lint，退出码0；去除未使用schema后离线复跑通过。尚有1个info-license警告，未擅自为仓库补充许可证声明。此次只验证契约，不声称HTTP端点已实现。
