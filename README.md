spring-jpetstore
================

JPetStore 6 is a full web application built on top of Spring, Spring MVC, MyBatis 3, and Spring Security. 
This is another implementation of MyBatis sample application (https://github.com/mybatis/jpetstore-6).

Original application is available for downloading in the downloads section of MyBatis project site. 
In this section we will walk through this sample to understand how is it built and learn how to run it.

旧系统业务与回归基线
--------------------

- [业务需求规范](docs/spec.md)：业务规则、字段约束、验收场景和待确认决策。
- [旧系统基线](docs/baseline/README.md)：启动方式、页面流程、回归用例及浏览器检查清单。
- [数据结构](docs/baseline/data-model.md) 与 [行为保留/缺陷纠正](docs/baseline/decisions.md)。
- [实测记录](docs/baseline/verification.md)：17 条自动用例通过；原始 WAR 受历史标签库依赖阻碍，完整 JSP/浏览器验证待完成。

使用完整 JDK 8 运行原技术栈回归：

```bash
bash legacy/scripts/legacy-baseline.sh
```

脚本使用独立的 `legacy/baseline/pom.xml`，不改动原应用构建及业务代码。测试重建 H2 内存数据库；MockMvc 不渲染 JSP，不能代替完整页面验收。
