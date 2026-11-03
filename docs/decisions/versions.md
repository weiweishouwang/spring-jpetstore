# T016：精确版本与工程参数

状态：版本选择与发布元数据核验完成；尚未安装目标工具、解析现代依赖树或运行集成测试。版本事实由官方Maven Central、npm、Node分发及Docker Registry只读查询取得。

## 后端和工具

| 项目 | 锁定值 | 依据/边界 |
| --- | --- | --- |
| Java | 21；开发目标Eclipse Temurin 21.0.13+9 | 本机现有Oracle21.0.7可识别但未达目标补丁；目标下载/校验和安装是T024，未完成 |
| Spring Boot parent/BOM | 4.1.2 | BOM实际可下载；Java21受官方支持 |
| MyBatis Starter | 4.0.0 | 官方兼容表支持Boot4.0+；显式集中版本，不混入旧MyBatis-Spring |
| Maven Wrapper分发 | Apache Maven 3.9.11 | Maven Central分发SHA512已核验；本机Maven3.8.7不作为最终项目分发 |
| MySQL Server | 8.4.13（8.4分支，InnoDB） | 从固定索引对应arm64镜像config的MYSQL_VERSION=8.4.13-1.el9核验 |
| 架构 | 本机darwin/arm64；容器linux/arm64；CI可linux/amd64 | 服务端和集成测试使用同一MySQL镜像索引/子版本 |

Boot管理依赖（从4.1.2 BOM实际提取，不在子POM另写版本）：

- `flyway.version`：`12.4.0`。
- `jackson-bom.version`：`3.1.7`。
- `junit-jupiter.version`：`6.0.3`。
- `mysql.version`：`9.7.0`。
- `r2dbc-mysql.version`：`1.4.3`。
- `testcontainers.version`：`2.0.5`。

MySQL Connector/J的9.x版本是驱动版本，不代表改用MySQL9服务器；真实连接/事务兼容性必须通过目标MySQL8.4测试。Java测试使用BOM管理的Jupiter，不把旧JUnit4移入新工程。

## 前端精确版本

| 包 | 版本 |
| --- | --- |
| Node.js | v24.22.2（24 LTS） |
| npm | 11.19.1（该Node官方分发所带） |
| vue | 3.5.44 |
| vite | 8.3.5 |
| typescript | 5.9.3 |
| vitest | 5.1.0 |
| @vitejs/plugin-vue | 6.0.10 |
| vue-router | 4.6.4 |
| pinia | 3.0.4 |
| @playwright/test | 1.64.1 |
| @vue/test-utils | 2.5.1 |
| vue-tsc | 3.3.14 |

Vue Router4与Pinia3明确选择该兼容线，不直接采用latest新增peer体系；TypeScript选5.9.3。已核对Vue/Router/Pinia/Vite/plugin-vue/Vitest的Node及peer声明；最终npm ci与vue-tsc/Vitest构建在前端任务执行。lint、契约lint、ArchUnit等BOM外附加工具引入前须将精确版本追加到本记录，不得使用未锁定latest。

## 分发校验

Maven 3.9.11 binary ZIP SHA512：

```text
03e2d65d4483a3396980629f260e25cac0d8b6f7f2791e4dc20bc83f9514db8d0f05b0479e699a5f34679250c49c8e52e961262ded468a20de0be254d8207076
```

Node官方分发SHA256：

- `node-v24.22.2-darwin-arm64.tar.gz`：`aa9549349e47dffaa42dcf95d6e06ade8c4bdafc980979883b88767670df385a`。
- `node-v24.22.2-linux-arm64.tar.xz`：`eb187837a2230d4047161ac0be008584e16b46f726e9c9490ee35dc6b17d9ba8`。
- `node-v24.22.2-linux-x64.tar.xz`：`48cc0e21e048790e54c0cf5749c2725005313d652651c40b7a21feb9697aaf75`。

前端包完整性（供生成lockfile核对）：

- `vue@3.5.44`：`sha512-dvvmaOCG7lAMkJTPqPwWeGMsPS6DIsFsfZDUgBp9V6mHf9b3+/wyVwYR4Es+WV6uJC0FKRxp81Obcq0ZwUcGFQ==`。
- `vite@8.3.5`：`sha512-z1YpjieJ9g0ebTmQpLBv/oq9E3F9EIlOe+7LahW8SrE3LTiG3udBRUXQOlYXP9b+IC2q2phHeVxj9xGlw34OEQ==`。
- `typescript@5.9.3`：`sha512-jl1vZzPDinLr9eUt3J/t7V6FgNEw9QjvBPdysz9KfQDD41fQrC2Y4vKQdiaUpFT4bXlb1RHhLpp8wtm6M5TgSw==`。
- `vitest@5.1.0`：`sha512-MzmNFnrwa8v8lF1bLxw3UJ4EREwVOVb75VqJZmUDRjJ6EluK/x9th7qTbaeDk41WxE1GxWqmTXcdsfobfH5sOA==`。
- `@vitejs/plugin-vue@6.0.10`：`sha512-b80aUQh3n+qEzzZm5oQp3rT2QImeCx58K6eFl/RkJ3d65KW0kTuv5lNsLRVAjbW9YRUpo/wzmvzMgV34bTAItA==`。
- `vue-router@4.6.4`：`sha512-Hz9q5sa33Yhduglwz6g9skT8OBPii+4bFn88w6J+J4MfEo4KRRpmiNG/hHHkdbRFlLBOqxN8y8gf2Fb0MTUgVg==`。
- `pinia@3.0.4`：`sha512-l7pqLUFTI/+ESXn6k3nu30ZIzW5E2WZF/LaHJEpoq6ElcLD+wduZoB2kBN19du6K/4FDpPMazY2wJr+IndBtQw==`。
- `@playwright/test@1.64.1`：`sha512-IaYGml/nSOiZWROfPtuIL7neuu9qDOZ3TNvNljUIrhDMxiGTgPjzj/g9p9RxpS2tSepPDRY+UwAaEeiKtUvIAg==`。
- `@vue/test-utils@2.5.1`：`sha512-V/4a99odJ7hapr0bRt35XxljGk8hefq31gSOnlKTcEq2fUeoj2qb+FcpJYfQu4qfYhW9Ug+O2+CZ3QmZ7gXDzA==`。
- `vue-tsc@3.3.14`：`sha512-URAu2H9AuMZMg9GCgnqbaHHHd8pBZRAwNV2e2yYS8VDsoTVXHiOSo663OS6DkEvauQ2yixn5TPFJ9WV9QIspkA==`。

## OCI镜像不可变引用

- `library/mysql@sha256:a0225d1ea6631c8bc2385dc4c2faba17bbf1834ecf369e1659a9a382bd8d5dd7`，已核对包含linux/arm64与linux/amd64。
- `paketobuildpacks/builder-jammy-java-tiny@sha256:f5a5fcbb218ce54bbc7391ddd35721556052272430b0a095df240aba170222ca`，已核对包含linux/arm64与linux/amd64。
- `paketobuildpacks/run-jammy-tiny@sha256:19af015a6f3ce9455fe832343179d81cc5293666b9c2290f08e2de99c11e0542`，已核对包含linux/arm64与linux/amd64。

Buildpacks明确设Java21，builder/run为兼容jammy-tiny族。镜像摘要已读取，不代表镜像已拉取或应用可运行；必须在Docker就绪后验证构建和运行。目标Temurin下载API/GitHub API本次返回403，未下载JDK，不能跳过下载校验。

## 官方依据

- [Boot系统要求](https://docs.spring.io/spring-boot/system-requirements.html)
- [Boot4.1.2 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom)
- [MyBatis兼容表](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
- [Temurin官方发布状态21.0.13+9](https://github.com/adoptium/temurin/issues/317)
- [Node官方分发](https://nodejs.org/dist/v24.22.2/SHASUMS256.txt)
- [Maven分发校验](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip.sha512)

npm精确版本和integrity直接来自https://registry.npmjs.org对应包/版本接口；Docker摘要直接来自registry-1.docker.io的OCI manifest，不含认证token。

## 本批次契约校验工具

`@redocly/cli@2.3.0`固定版本使用Node24.22.2，安装在临时npm缓存中，未全局安装；OpenAPI推荐规则lint已实际执行。后续前端工程若引入该工具，沿用此版本并写入lockfile。
