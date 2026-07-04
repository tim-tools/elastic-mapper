# PRD: ElasticMapper — Elasticsearch ORM 框架

## 1. 概述

**ElasticMapper** 是一套面向 Elasticsearch 的 Java ORM 框架，设计理念对标 MyBatis + MyBatis-Plus。底层基于 Elasticsearch Low Level REST Client 通信、Gson 做序列化/反序列化，并在 Mapper XML 文件中提供一套类似 MyBatis 的动态语句 DSL，让开发者用熟悉的 MyBatis 范式操作 Elasticsearch。

**解决的问题：**
- Spring Data Elasticsearch 的查询方式与国内主流的 MyBatis 开发习惯差异大，学习成本高
- 复杂 ES 查询（多条件组合、聚合、嵌套）在代码中拼接 JSON 字符串可读性差、维护困难
- 现有方案无法像 MyBatis-Plus 一样提供代码生成器、分页插件、逻辑删除等开箱即用的增强能力

## 2. 目标

- 提供与 MyBatis 一致的编程模型：**Mapper 接口 + XML DSL + 动态代理**
- 覆盖 ES 核心操作：基础 CRUD、复杂查询、聚合、嵌套对象、分页
- 支持代码生成器，从 ES 索引反向生成 Java Bean 和 Mapper
- 内置 Spring Boot Starter，自动配置、开箱即用
- 保持轻量：核心依赖仅限于 `elasticsearch-rest-low-level-client` + `gson` + 动态表达式引擎

## 3. 动态语句引擎选型推荐

这是本 PRD 的核心决策之一。MyBatis 的方案是 **自定义 XML 标签（`<if>`, `<foreach>` 等）+ OGNL 表达式求值**。我们沿袭这一思路，将引擎拆为两层：

| 层次 | 职责 | 方案 |
|------|------|------|
| **结构层** | XML 标签解析、控制流（if/foreach/where） | 自定义 XML namespace + DOM 解析（参考 MyBatis `XMLScriptBuilder`） |
| **表达式层** | `test` 属性中的布尔表达式求值 | 接入第三方轻量表达式引擎 |

以下是表达式引擎候选对比：

| 引擎 | 体积 | 语法 | 错误提示 | 维护状态 | 综合评分 |
|------|------|------|----------|----------|----------|
| **Apache JEXL** | ~400KB | JSTL/EL 风格，Java 开发者零学习成本 | ⭐⭐⭐ 优秀（行号+上下文） | Apache 活跃维护 | ⭐⭐⭐⭐⭐ |
| **MVEL2** | ~300KB | Java-like，支持属性导航 | ⭐⭐ 一般（堆栈信息较底层） | 维护放缓 | ⭐⭐⭐⭐ |
| **OGNL** | ~250KB | 点号导航 + 方法调用 | ⭐⭐ 一般 | 偶有更新 | ⭐⭐⭐ |
| **Janino** | ~800KB | 纯 Java 片段编译为字节码 | ⭐⭐⭐ 好（编译器标准错误） | 活跃 | ⭐⭐⭐⭐ |
| **Aviator** | ~500KB | 自定义轻量语法 | ⭐⭐⭐ 好 | 活跃 | ⭐⭐⭐⭐ |

### 推荐结论：Apache JEXL

**理由：**
1. **语法最贴近 Java 开发者认知**——JEXL 表达式即 Java 表达式，MyBatis 用户从 OGNL 迁移几乎无感
2. **错误提示友好**——`JexlException` 携带行号、列号、上下文代码片段，IDE 控制台可直接定位
3. **Apache 基金会项目**——与项目中已使用的 `httpclient`（ES Low Level Client 底层）同源，企业接受度高
4. **模板语法内置**——JEXL 自带 `${...}` 模板语法，可直接用于 ES JSON 片段拼接，无需额外引入模板引擎
5. **性能足够**——JEXL 支持表达式预编译为 `JexlExpression` 对象缓存，重复调用走已编译路径

**典型用法示例：**
```xml
<select id="searchUsers">
  {
    "query": {
      "bool": {
        "must": [
          <if test="name != null && !name.isEmpty()">
            {"match": {"name": "${name}"}}
          </if>
          <if test="age > 0">
            {"range": {"age": {"gte": ${age}}}}
          </if>
        ]
      }
    }
  }
  <if test="pageSize > 0">
    ,"size": ${pageSize},"from": ${(pageNum - 1) * pageSize}
  </if>
</select>
```

> **备选方案：** 如果对体积极度敏感（要求 < 200KB），可选择 MVEL2；如果团队偏好纯 Java 编译期检查，可选 Janino。以上对比细节将在 SPEC 中展开。

## 4. 用户故事

### US-001: 定义 ES 索引与 Java Bean 的映射
**描述：** 作为开发者，我希望用注解或约定把 Java Bean 映射到 ES 索引，以便用面向对象的方式操作文档。

**验收标准：**
- [ ] `@IndexName("index_name")` 标注在类上，指定 ES 索引名
- [ ] `@Id` 标注文档 ID 字段，支持 String/Long 类型
- [ ] `@Field(type = "keyword")` 可选覆盖 ES 字段类型，不标注则按 Java 类型自动推断
- [ ] 不标注任何注解时，自动按类名小写驼峰转下划线确定索引名，字段名同规则
- [ ] 类型检查（编译）通过

### US-002: 编写 Mapper 接口并执行基础 CRUD
**描述：** 作为开发者，我希望定义一个 Mapper 接口并调用其方法，即可在 ES 上完成增删改查，无需手写 Low Level Client 调用。

**验收标准：**
- [ ] 定义 `interface UserMapper extends BaseMapper<User>`，自动获得 `selectById`、`insert`、`updateById`、`deleteById` 等方法
- [ ] `userMapper.insert(user)` 调用后，文档写入 ES 并返回写入结果
- [ ] `userMapper.selectById("123")` 返回反序列化后的 `User` 对象
- [ ] `userMapper.updateById(user)` 局部更新文档
- [ ] `userMapper.deleteById("123")` 删除文档
- [ ] 单元测试验证所有 CRUD 操作

### US-003: 用注解在 Mapper 方法上定义简单查询
**描述：** 作为开发者，我希望通过 `@Select` 注解直接在 Mapper 方法上写 ES 查询 JSON，避免为简单查询创建 XML 文件。

**验收标准：**
- [ ] `@Select("{\"match\": {\"name\": \"${name}\"}}")` 支持参数占位符 `${}`
- [ ] 方法参数自动绑定到占位符（参数名匹配或 `@Param` 注解指定）
- [ ] 支持 `@Select`、`@Update`、`@Delete` 三种操作类型
- [ ] 单元测试覆盖参数绑定场景

### US-004: 在 XML Mapper 文件中编写动态查询
**描述：** 作为开发者，我希望在 XML 文件中使用 `<if>`、`<foreach>` 等标签编写动态 ES 查询，以应对多条件组合的场景。

**验收标准：**
- [ ] XML 文件放置于 `resources/mapper/` 目录，框架启动时自动加载解析
- [ ] 支持 `<if test="...">` 条件判断标签，test 表达式使用 JEXL 语法
- [ ] 支持 `<foreach collection="list" item="item" separator=",">` 循环拼接
- [ ] 支持 `<choose>` / `<when>` / `<otherwise>` 分支选择
- [ ] 支持 `<where>` 自动处理 bool query 中的 must/should/filter 组合
- [ ] 支持 `<set>` 自动处理 update 时的部分字段更新
- [ ] 支持 `${...}` 变量替换（防注入转义）和 `#{...}` 参数占位符（安全拼接）
- [ ] XML 解析在应用启动时完成（预编译为 `DynamicStatement` 对象），运行时不重复解析
- [ ] 单元测试验证各标签组合场景

### US-005: 分页查询
**描述：** 作为开发者，我希望像 MyBatis-Plus 一样用 `Page` 对象进行分页查询，无需手动计算 `from` 和 `size`。

**验收标准：**
- [ ] `Page<User> page = new Page<>(1, 20)` 定义页码和每页大小
- [ ] `userMapper.selectPage(page, queryWrapper)` 返回分页结果，包含 `total`、`records`、`pages` 等字段
- [ ] 分页信息自动从 ES 的 `total_relation` 和 `hits` 中提取
- [ ] 支持 `search_after` 深分页模式（通过 `Page` 配置项切换）
- [ ] 单元测试验证 from/size 分页和 search_after 分页

### US-006: 聚合查询
**描述：** 作为开发者，我希望通过 XML 或注解定义聚合查询，并用类型安全的方式获取聚合结果。

**验收标准：**
- [ ] XML 中支持 `<agg name="byCategory">` 标签定义聚合
- [ ] 聚合结果映射为 `Map<String, AggResult>` 返回
- [ ] 支持 terms、range、date_histogram、avg/sum/min/max 等常用聚合类型
- [ ] 支持嵌套聚合（子聚合）
- [ ] 单元测试覆盖 terms 聚合 + 子聚合场景

### US-007: 嵌套对象映射
**描述：** 作为开发者，我希望 Java Bean 中的 `List<NestedObject>` 字段能自动映射到 ES 的 `nested` 类型，并支持嵌套查询。

**验收标准：**
- [ ] `@Field(type = "nested")` 标注在 `List<T>` 字段上，插入时自动序列化为 ES nested 结构
- [ ] 查询结果中 nested 数组正确反序列化回 Java 对象
- [ ] 支持嵌套对象的条件查询（通过 `nested` query 包装）
- [ ] 单元测试覆盖 nested 对象读写

### US-008: 代码生成器
**描述：** 作为开发者，我希望运行一个生成器命令，从已有的 ES 索引反向生成 Java Bean、Mapper 接口和 XML 文件。

**验收标准：**
- [ ] 指定 ES 索引名，自动获取 mapping 并生成 Java 类（字段类型映射为 ES 类型对应 Java 类型）
- [ ] 生成的类自动添加 `@IndexName` 和 `@Field` 注解
- [ ] 同时生成对应的 Mapper 接口（继承 `BaseMapper`）
- [ ] 可选生成 XML mapper 模板文件
- [ ] 支持命令行和 Maven 插件两种调用方式

### US-009: Spring Boot 自动配置
**描述：** 作为开发者，我希望在 Spring Boot 项目中引入 Starter 依赖后，无需手动配置即可使用 Mapper。

**验收标准：**
- [ ] 引入 `elastic-mapper-spring-boot-starter` 依赖
- [ ] `application.yml` 中配置 `elastic-mapper.hosts: 127.0.0.1:9200`
- [ ] `@MapperScan("com.example.mapper")` 自动扫描 Mapper 接口并注入 Spring 容器
- [ ] Mapper 实例可直接 `@Autowired` 使用
- [ ] 单元测试验证自动配置链路

## 5. 功能需求

- **FR-1:** 系统必须提供 `@IndexName`、`@Id`、`@Field` 注解用于 Java Bean 到 ES 索引的映射声明
- **FR-2:** 系统必须在无注解时按约定自动推断索引名（类名转下划线）和字段名（驼峰转下划线）
- **FR-3:** 系统必须提供 `BaseMapper<T>` 接口，内置 `insert`、`selectById`、`selectList`、`updateById`、`deleteById`、`selectPage` 等基础方法
- **FR-4:** 系统必须通过动态代理（`MapperProxy`）将 Mapper 接口方法调用路由到实际的 ES 请求
- **FR-5:** 系统必须提供 `@Select`、`@Update`、`@Delete` 注解支持直接在方法上写 ES JSON 查询片段
- **FR-6:** 系统必须支持 XML Mapper 文件，使用 MyBatis 风格的自定义标签（`<if>`、`<foreach>`、`<choose>`、`<where>`、`<set>`）
- **FR-7:** XML 中 `test` 属性表达式必须使用 **Apache JEXL** 作为求值引擎
- **FR-8:** 系统必须在启动时预编译所有 XML Mapper 为 `DynamicStatement` 对象，运行时仅执行表达式求值和字符串拼接
- **FR-9:** 系统必须支持 `${...}` 变量替换和 `#{...}` 参数占位符两种占位方式
- **FR-10:** 系统必须基于 `RestLowLevelClient` 发送所有 ES 请求，不依赖 High Level Client
- **FR-11:** 系统必须使用 Gson 完成 Java Bean ↔ JSON 的双向序列化，支持自定义 `TypeAdapter`
- **FR-12:** 系统必须提供 `Page<T>` 分页对象，支持 `from/size` 分页和 `search_after` 深分页
- **FR-13:** 系统必须支持 ES 聚合查询，聚合结果映射为结构化 Java 对象
- **FR-14:** 系统必须支持 ES `nested` 类型的自动序列化和反序列化
- **FR-15:** 系统必须提供代码生成器，从已有 ES 索引反向生成 Java Bean 和 Mapper
- **FR-16:** 系统必须提供 Spring Boot Starter，包含自动配置和 `@MapperScan` 注解
- **FR-17:** 系统必须提供 `ElasticMapperConfig` 全局配置类，支持设置 hosts、用户名密码、连接超时、socket 超时等

## 6. 非目标（本期不做）

- **不提供** High Level REST Client 封装——已选择 Low Level Client 作为唯一底层
- **不提供** 基于 Elasticsearch SQL API 的查询方式
- **不提供** 多数据源动态切换（多 ES 集群路由）
- **不提供** 乐观锁/逻辑删除等增强插件（后续版本规划，对标 MyBatis-Plus 的 `@Version`、`@TableLogic`）
- **不提供** 图形化管理界面
- **不提供** 异步/响应式链式调用 API（Reactive Streams）
- **不提供** ES 版本兼容适配层——初始版本锁定 ES 7.x/8.x

## 7. 技术考量

| 决策点 | 选择 | 理由 |
|--------|------|------|
| ES 通信层 | `elasticsearch-rest-low-level-client` | 用户明确指定；无 High Level Client 额外依赖 |
| 序列化 | Gson | 用户明确指定；相比 Jackson 更轻量，API 更直观 |
| 动态表达式引擎 | Apache JEXL（详见第 3 节） | 最佳开发体验，语法熟悉，错误提示好 |
| XML 解析 | JDK 内置 `javax.xml` + 自定义 `XMLScriptBuilder` | 零额外依赖，与 MyBatis 方案一致 |
| Mapper 代理 | JDK 动态代理（`java.lang.reflect.Proxy`） | 零依赖，与 MyBatis 方案一致 |
| 连接管理 | 复用 ES Low Level Client 的 `HttpAsyncClient` 线程池 | 不引入额外连接池 |
| Spring 集成 | 自定义 `@MapperScan` + `FactoryBean` + `@ConfigurationProperties` | 与 MyBatis-Spring 方案一致 |

## 8. 风险与开放问题

### 风险
| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| ES JSON DSL 与 MyBatis SQL DSL 语义差异大 | XML 标签设计可能不贴合 ES 场景 | 优先在 XML 中嵌入 ES JSON 原文 + 标签做条件拼接，而非创造新的抽象层 |
| Gson 对 ES 特殊类型（如 `geo_point`）支持不足 | 特定字段类型序列化可能报错 | 提供可扩展的 `TypeAdapter` 注册机制 |
| Low Level Client API 较底层 | 初始开发量大于基于 High Level Client | 封装 `ElasticTemplate` 层屏蔽底层细节 |
| 缺少 SQL 的 join 语义 | 用户期待 join 但 ES 无原生支持 | 文档明确说明：不提供 join，仅支持 nested/parent-child 查询 |

### 开放问题
- Q1: XML mapper 中是嵌入 **完整 ES JSON** + 标签做条件拼接，还是设计一套更抽象的查询标签语法（如 `<must>`, `<should>`）？
  - **建议：** 优先采用"嵌入 JSON + 条件标签"方式，降低学习成本，后续版本再考虑高层抽象标签
- Q2: 代码生成器模板引擎是否需要复用 JEXL，还是引入独立模板引擎（如 Pebble / Mustache）？
  - **建议：** 代码生成器部分复用 JEXL 的模板语法即可，避免引入额外依赖
- Q3: 是否强依赖 Spring？是否提供纯 Java（无 Spring）的使用方式？
  - **建议：** 核心模块与 Spring 解耦，提供 `elastic-mapper-core`（无 Spring 依赖）和 `elastic-mapper-spring-boot-starter` 两个模块

## 附：推荐模块结构

```
elastic-mapper/
├── elastic-mapper-core/           # 核心引擎（无 Spring 依赖）
│   ├── annotations/               # @IndexName, @Id, @Field, @Select, @Update, @Delete, @Param
│   ├── binding/                   # MapperProxy 动态代理
│   ├── builder/                   # DynamicStatement 构建器
│   ├── config/                    # ElasticMapperConfig
│   ├── executor/                  # ElasticTemplate (Low Level Client 封装)
│   ├── metadata/                  # 索引/字段元数据解析
│   ├── parser/                    # XML Mapper 解析器 (XMLScriptBuilder)
│   ├── plugin/                    # 插件接口（分页、拦截器等）
│   ├── result/                    # Page<T>, AggResult 等结果类型
│   ├── script/                    # 动态语句引擎（JEXL 集成）
│   └── serialize/                 # Gson TypeAdapter 注册 & ES 类型映射
├── elastic-mapper-generator/      # 代码生成器
│   ├── engine/                    # 生成引擎
│   └── template/                  # 代码模板
└── elastic-mapper-spring-boot-starter/  # Spring Boot 自动配置
    ├── autoconfigure/
    └── MapperScan/
```
