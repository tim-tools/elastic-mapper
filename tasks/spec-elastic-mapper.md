# SPEC: ElasticMapper — Elasticsearch ORM 框架

> 技术规格说明书，派生自: [PRD: ElasticMapper](prd-elastic-mapper.md)  
> 生成日期: 2026-06-26 | 分支: 无 (greenfield) | 目标版本: 1.0.0

---

## 1. 摘要

### 1.1 本 SPEC 覆盖范围

本 SPEC 定义 ElasticMapper 框架的完整技术架构，包括：模块划分与依赖关系、所有公共 API 的接口契约（注解、BaseMapper、XML DTD、配置类）、核心执行链路（Mapper 代理 → 动态语句编译 → ES 请求）、数据模型（元数据、结果类型）、错误处理策略、性能设计、测试策略，以及分阶段实施计划。

### 1.2 PRD 引用
- 来源: `tasks/prd-elastic-mapper.md`
- 覆盖用户故事: US-001 至 US-009
- 覆盖功能需求: FR-1 至 FR-17

### 1.3 设计决策总览

| 决策 | 选择 | 理由 |
|------|------|------|
| ES 通信 | `elasticsearch-rest-low-level-client` (7.x/8.x) | 用户指定；避免 High Level Client 额外依赖 |
| JSON 序列化 | Gson 2.10+ | 用户指定；比 Jackson 轻量；API 直观 |
| 动态表达式引擎 | Apache JEXL 3.3+ | 语法贴近 Java，错误提示优秀，Apache 基金会项目 |
| XML 解析 | JDK `javax.xml.parsers` + 自定义 `XMLScriptBuilder` | 零额外依赖；与 MyBatis 方案一致 |
| Mapper 代理 | JDK `java.lang.reflect.Proxy` | 零依赖；运行时动态代理 |
| 模板语法 | JEXL 内置 `${...}` 模板 + 自定义 `#{...}` 占位符 | 复用表达式引擎，无需引入额外模板库 |
| Spring 集成 | `@MapperScan` + `FactoryBean` + `@ConfigurationProperties` | 对标 MyBatis-Spring 集成模式 |
| 模块化 | core (无 Spring) + generator + starter | core 可独立使用；Spring 集成可选 |
| XML 查询语法 | 嵌入 ES JSON 原文 + `<if>`/`<foreach>` 标签做条件拼接 | 降低学习成本；避免创造不成熟的抽象层 |

---

## 2. 架构

### 2.1 系统上下文

```
┌─────────────────────────────────────────────────────────────┐
│                     应用层 (Spring Boot / Plain Java)         │
│                                                             │
│  UserMapper.java (接口)    UserService.java (业务)           │
│       │                        │                            │
│       ▼                        ▼                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              ElasticMapper Framework                   │  │
│  │                                                       │  │
│  │  ┌──────────┐  ┌──────────┐  ┌────────────────────┐  │  │
│  │  │ binding  │  │  script  │  │     executor       │  │  │
│  │  │ (代理层) │──▶│ (脚本层) │──▶│   (执行层)         │  │  │
│  │  └──────────┘  └──────────┘  └────────┬───────────┘  │  │
│  │                                       │               │  │
│  └───────────────────────────────────────┼───────────────┘  │
│                                          │                   │
│                    ┌──────────────────────▼──────────────┐  │
│                    │  ES RestLowLevelClient               │  │
│                    │  (org.elasticsearch.client.RestClient)│  │
│                    └──────────────────────┬──────────────┘  │
│                                           │                  │
└───────────────────────────────────────────┼──────────────────┘
                                            ▼
                                  ┌─────────────────┐
                                  │  Elasticsearch   │
                                  │  7.x / 8.x       │
                                  └─────────────────┘
```

### 2.2 组件设计

#### 2.2.1 核心模块 (`elastic-mapper-core`)

| 包 | 职责 | 关键类 |
|----|------|--------|
| `annotations/` | 映射声明和查询注解 | `@IndexName`, `@Id`, `@Field`, `@Select`, `@Update`, `@Delete`, `@Param` |
| `binding/` | Mapper 接口动态代理 | `MapperProxy`, `MapperProxyFactory`, `MapperRegistry` |
| `metadata/` | 索引和字段元数据解析 | `EntityMetadata`, `FieldMetadata`, `MetadataParser` |
| `parser/` | XML Mapper 文件解析 | `XMLMapperParser`, `XMLScriptBuilder`, `DynamicContext` |
| `script/` | 动态语句编译和执行 | `DynamicStatement`, `StatementCompiler`, `JexlEngineHolder` |
| `executor/` | ES 请求封装 | `ElasticTemplate`, `RequestBuilder`, `ResponseExtractor` |
| `config/` | 全局配置 | `ElasticMapperConfig` |
| `result/` | 结果类型 | `Page<T>`, `AggResult`, `ESResponse<T>` |
| `serialize/` | Gson 序列化配置 | `GsonFactory`, `TypeAdapterRegistry`, `EsTypeMapping` |
| `plugin/` | 插件接口 | `Interceptor`, `InterceptorChain` |

#### 2.2.2 代码生成器 (`elastic-mapper-generator`)

| 包 | 职责 | 关键类 |
|----|------|--------|
| `engine/` | 生成引擎核心 | `CodeGenerator`, `IndexIntrospector`, `TypeMapper` |
| `template/` | 代码模板 | `BeanTemplate`, `MapperTemplate`, `XmlTemplate` |

#### 2.2.3 Spring Boot Starter (`elastic-mapper-spring-boot-starter`)

| 包 | 职责 | 关键类 |
|----|------|--------|
| `autoconfigure/` | 自动配置 | `ElasticMapperAutoConfiguration`, `ElasticMapperProperties` |
| `scan/` | Mapper 扫描注册 | `MapperScan`, `MapperScannerRegistrar`, `MapperFactoryBean` |

### 2.3 核心链路：一次查询的完整流程

```
用户调用 userMapper.searchUsers(param)
        │
        ▼
┌─── MapperProxy.invoke() ──────────────────────────────────────────┐
│  1. 从 MapperRegistry 获取 MapperMethod                           │
│  2. 选择语句来源: AnnotationStatements / XMLStatements             │
│  3. 构建 MethodInvocation { method, args, paramNames }            │
└──────────────────────────┬────────────────────────────────────────┘
                           ▼
┌─── DynamicStatement.execute(MethodInvocation) ────────────────────┐
│  1. 将参数注入 JexlContext (args → named vars)                    │
│  2. 遍历 StatementNode 树:                                        │
│     - IfNode: JEXL 求值 test 表达式 → 决定是否保留子节点内容       │
│     - ForeachNode: 迭代集合 → 按 separator 拼接子内容              │
│     - ChooseNode: 按 when/otherwise 分支求值                       │
│     - TextNode: JEXL 模板替换 ${} 和 #{}                           │
│  3. 输出: 完整的 ES JSON 查询字符串                               │
└──────────────────────────┬────────────────────────────────────────┘
                           ▼
┌─── ElasticTemplate.execute(jsonString, indexName) ────────────────┐
│  1. 拼接 ES REST 路径: /{indexName}/_search                        │
│  2. 通过 RestLowLevelClient.performRequest() 发送                  │
│  3. 接收 ResponseEntity → JSON 字符串                              │
│  4. ResponseExtractor.extract(response, returnType)                │
│  5. Gson 反序列化为目标类型，返回                                  │
└────────────────────────────────────────────────────────────────────┘
```

### 2.4 文件结构

```
elastic-mapper/
├── pom.xml                                  # 父 POM (多模块)
│
├── elastic-mapper-core/
│   └── src/main/java/io/elasticmapper/
│       ├── annotations/
│       │   ├── IndexName.java               # FR-1: 索引名标注
│       │   ├── Id.java                      # FR-1: 文档 ID 标注
│       │   ├── Field.java                   # FR-1: 字段映射标注
│       │   ├── Select.java                  # FR-5: 查询注解
│       │   ├── Update.java                  # FR-5: 更新注解
│       │   ├── Delete.java                  # FR-5: 删除注解
│       │   └── Param.java                   # FR-9: 参数名绑定
│       ├── binding/
│       │   ├── MapperProxy.java             # FR-4: JDK 动态代理
│       │   ├── MapperProxyFactory.java
│       │   ├── MapperRegistry.java
│       │   ├── MapperMethod.java
│       │   └── MethodInvocation.java
│       ├── metadata/
│       │   ├── EntityMetadata.java          # FR-2: 实体元数据
│       │   ├── FieldMetadata.java
│       │   └── MetadataParser.java
│       ├── parser/
│       │   ├── XMLMapperParser.java         # FR-6: XML 文件解析入口
│       │   ├── XMLScriptBuilder.java        # FR-6: 标签 → 节点树
│       │   ├── DynamicContext.java          # FR-8: 运行时上下文
│       │   └── node/
│       │       ├── StatementNode.java       # 抽象节点
│       │       ├── IfNode.java
│       │       ├── ForeachNode.java
│       │       ├── ChooseNode.java
│       │       ├── WhenNode.java
│       │       ├── OtherwiseNode.java
│       │       ├── WhereNode.java
│       │       ├── SetNode.java
│       │       └── TextNode.java
│       ├── script/
│       │   ├── DynamicStatement.java        # FR-8: 编译后的语句对象
│       │   ├── StatementCompiler.java       # FR-8: 编译入口
│       │   └── JexlEngineHolder.java        # FR-7: JEXL 引擎持有者
│       ├── executor/
│       │   ├── ElasticTemplate.java         # FR-10: Low Level Client 封装
│       │   ├── RequestBuilder.java
│       │   └── ResponseExtractor.java
│       ├── config/
│       │   └── ElasticMapperConfig.java     # FR-17: 全局配置
│       ├── result/
│       │   ├── Page.java                    # FR-12: 分页结果
│       │   └── AggResult.java               # FR-13: 聚合结果
│       ├── serialize/
│       │   ├── GsonFactory.java             # FR-11: Gson 工厂
│       │   ├── TypeAdapterRegistry.java
│       │   └── EsTypeMapping.java
│       └── plugin/
│           ├── Interceptor.java
│           └── InterceptorChain.java
│
├── elastic-mapper-generator/
│   └── src/main/java/io/elasticmapper/generator/
│       ├── CodeGenerator.java               # FR-15: 生成器入口
│       ├── IndexIntrospector.java
│       ├── TypeMapper.java
│       └── template/
│           ├── BeanTemplate.java
│           ├── MapperTemplate.java
│           └── XmlTemplate.java
│
└── elastic-mapper-spring-boot-starter/
    └── src/main/java/io/elasticmapper/spring/
        ├── autoconfigure/
        │   ├── ElasticMapperAutoConfiguration.java  # FR-16
        │   └── ElasticMapperProperties.java
        └── scan/
            ├── MapperScan.java                      # FR-16
            ├── MapperScannerRegistrar.java
            └── MapperFactoryBean.java
```

---

## 3. 数据模型

### 3.1 核心注解定义

```java
// === @IndexName ===
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IndexName {
    /** ES 索引名，支持 SpEL 占位符如 "${es.index.prefix}-user" */
    String value();
}

// === @Id ===
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {}

// === @Field ===
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Field {
    /** ES 字段类型: "keyword", "text", "long", "nested", "geo_point", ... */
    String type() default "";
    /** ES 字段名，不指定则按驼峰转下划线规则自动推断 */
    String name() default "";
    /** 是否索引 */
    boolean index() default true;
    /** 自定义 analyzer */
    String analyzer() default "";
    /** 日期格式化 pattern */
    String format() default "";
}

// === @Select ===
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Select {
    /** ES 查询 JSON 片段，支持 ${paramName} 占位符 */
    String value();
}

// === @Update ===
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Update {
    String value();
}

// === @Delete ===
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Delete {
    String value();
}

// === @Param ===
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
    String value();
}
```

### 3.2 实体元数据 (EntityMetadata)

```
EntityMetadata
├── indexName: String              // 来自 @IndexName 或约定推断
├── idField: FieldMetadata         // @Id 标注的字段
├── fields: Map<String, FieldMetadata>  // Java字段名 → 元数据
│
└── FieldMetadata
    ├── javaName: String           // Java 字段名 (camelCase)
    ├── esName: String             // ES 字段名 (snake_case)
    ├── javaType: Class<?>
    ├── esType: String             // "keyword", "text", "long", "nested", ...
    ├── isNested: boolean          // esType == "nested"
    ├── isId: boolean
    ├── nestedClass: Class<?>      // 仅当 isNested=true
    ├── index: boolean
    ├── analyzer: String
    └── dateFormat: String
```

**类型推断规则 (Java → ES):**

| Java 类型 | 默认 ES 类型 |
|-----------|-------------|
| `String` | `text` (另生成 `.keyword` 子字段) |
| `Integer`, `int`, `Long`, `long`, `Short`, `short` | `long` |
| `Float`, `float`, `Double`, `double`, `BigDecimal` | `double` |
| `Boolean`, `boolean` | `boolean` |
| `Date`, `LocalDate`, `LocalDateTime`, `Instant` | `date` |
| `List<T>`, `Collection<T>` | `nested` (当 T 是复杂类型时) |
| `Map<String, ?>` | `object` (flat) |
| 其他复杂类型 | `object` |

### 3.3 XML Mapper DTD

```xml
<!-- elastic-mapper-1.0.dtd -->
<!ELEMENT mapper (select|update|delete|insert)*>

<!ELEMENT select   (#PCDATA | if | foreach | choose | where | trim | agg)*>
<!ELEMENT update   (#PCDATA | if | foreach | choose | set    | trim)*>
<!ELEMENT delete   (#PCDATA | if | foreach | choose | where | trim)*>
<!ELEMENT insert   (#PCDATA | if | foreach | choose | trim)*>

<!ATTLIST select   id CDATA #REQUIRED>
<!ATTLIST update   id CDATA #REQUIRED>
<!ATTLIST delete   id CDATA #REQUIRED>
<!ATTLIST insert   id CDATA #REQUIRED>

<!ELEMENT if        (#PCDATA | if | foreach | choose | where | set | trim | agg)*>
<!ATTLIST if        test CDATA #REQUIRED>

<!ELEMENT foreach   (#PCDATA | if | foreach | choose)*>
<!ATTLIST foreach   collection CDATA #REQUIRED
                    item       CDATA #REQUIRED
                    separator  CDATA ","
                    open       CDATA ""
                    close      CDATA "">

<!ELEMENT choose    (when+, otherwise?)>
<!ELEMENT when      (#PCDATA | if | foreach)*>
<!ATTLIST when      test CDATA #REQUIRED>
<!ELEMENT otherwise (#PCDATA | if | foreach)*>

<!ELEMENT where     (#PCDATA | if | foreach | choose)*>
<!ELEMENT set       (#PCDATA | if | foreach | choose)*>
<!ELEMENT trim      (#PCDATA | if | foreach | choose)*>
<!ATTLIST trim      prefix CDATA ""
                    suffix CDATA ""
                    prefixOverrides CDATA ""
                    suffixOverrides CDATA "">

<!ELEMENT agg       (#PCDATA | if | agg)*>
<!ATTLIST agg       name CDATA #REQUIRED
                    type CDATA #REQUIRED>
```

**XML 示例 (动态复杂查询):**

```xml
<mapper>
  <select id="searchUsers">
    {
      "query": {
        "bool": {
          <where>
            <if test="name != null && !name.isEmpty()">
              {"must": [{"match": {"name": #{name}}}]}
            </if>
            <if test="statusList != null && !statusList.isEmpty()">
              {"filter": [{"terms": {"status": #{statusList}}}]}
            </if>
            <if test="ageMin > 0 || ageMax > 0">
              {"filter": [{"range": {"age": {
                <if test="ageMin > 0">"gte": #{ageMin},</if>
                <if test="ageMax > 0">"lte": #{ageMax}</if>
              }}}]}
            </if>
          </where>
        }
      }
      <if test="sortField != null && !sortField.isEmpty()">
        ,"sort": [{${sortField}: {"order": ${sortOrder != null ? sortOrder : 'desc'}}}]
      </if>
    }
  </select>
</mapper>
```

### 3.4 结果类型

```java
// === Page<T> ===
public class Page<T> {
    private long total;              // 命中总数
    private long pages;              // 总页数
    private long current;            // 当前页码
    private long size;               // 每页大小
    private List<T> records;         // 当前页数据
    private String searchAfter;      // search_after 游标 (深分页)
    private boolean hasNext;         // 是否有下一页

    // 构造: new Page<>(pageNum, pageSize)
    public Page(long current, long size) { ... }

    // 获取 from 偏移量
    public long from() { return (current - 1) * size; }

    // 深分页模式: 传入上次返回的 searchAfter 游标
    public Page(long size, String searchAfter) { ... }
}

// === AggResult ===
public class AggResult {
    private String name;                     // 聚合名称
    private String type;                     // terms / range / date_histogram / avg / sum / ...
    private Object value;                    // 单值聚合结果 (如 avg=42.5)
    private List<Map<String, Object>> buckets; // 多值聚合的桶
    private Map<String, AggResult> subAggs;  // 子聚合
}
```

---

## 4. API 设计

### 4.1 BaseMapper<T> 接口

```java
public interface BaseMapper<T> {

    // ── 插入 ──
    /** 插入单个文档，ID 自动生成或从 @Id 字段取值 */
    ESResponse insert(T entity);

    /** 批量插入 */
    ESResponse insertBatch(List<T> entities);

    // ── 查询 ──
    /** 根据 ID 查询 */
    T selectById(String id);

    /** 根据 ID 列表批量查询 */
    List<T> selectByIds(List<String> ids);

    /** 查询全部 (有上限，默认为 10000) */
    List<T> selectList();

    /** 分页查询 */
    Page<T> selectPage(Page<T> page);

    // ── 更新 ──
    /** 根据 ID 全量更新 */
    ESResponse updateById(T entity);

    /** 根据 ID 局部更新 (仅更新非 null 字段) */
    ESResponse updatePartialById(T entity);

    // ── 删除 ──
    /** 根据 ID 删除 */
    ESResponse deleteById(String id);

    /** 根据 ID 列表批量删除 */
    ESResponse deleteByIds(List<String> ids);
}
```

**注意:** `BaseMapper` 中的方法由 `MapperProxy` 直接路由到 `ElasticTemplate` 的预设方法，不走动态语句引擎。只有当 Mapper 子接口声明了自定义方法（标注 `@Select`/`@Update`/`@Delete` 或在 XML 中定义了同名 statement）时，才走 `DynamicStatement` 执行。

### 4.2 ElasticTemplate (核心执行器 API)

```java
public class ElasticTemplate implements AutoCloseable {

    // 构造
    public ElasticTemplate(ElasticMapperConfig config);

    // 基础操作
    public <T> ESResponse       insert(String index, T entity, Class<T> entityClass);
    public <T> ESResponse       insertBatch(String index, List<T> entities, Class<T> entityClass);
    public <T> T                selectById(String index, String id, Class<T> entityClass);
    public <T> List<T>          selectByIds(String index, List<String> ids, Class<T> entityClass);
    public <T> ESResponse       updateById(String index, String id, T partialEntity, Class<T> entityClass);
    public <T> ESResponse       deleteById(String index, String id, Class<T> entityClass);

    // 底层查询 API (对 DynamicStatement 暴露)
    public <T> List<T>          query(String index, String jsonBody, Class<T> entityClass);
    public <T> Page<T>          queryPage(String index, String jsonBody, Page<T> page, Class<T> entityClass);
    public Map<String, AggResult> queryAggs(String index, String jsonBody);

    // 原生 API (逃生舱: 允许用户直接操作 RestClient)
    public Response             rawPerformRequest(String method, String endpoint, String jsonBody);
    public RestClient           getRestClient();
}
```

### 4.3 ElasticMapperConfig

```java
public class ElasticMapperConfig {
    // ── 连接配置 ──
    private List<String> hosts;                  // ["127.0.0.1:9200", "127.0.0.1:9201"]
    private String scheme = "http";              // http / https
    private String username;
    private String password;
    private int connectTimeoutMs   = 5000;
    private int socketTimeoutMs    = 30000;
    private int maxConnTotal       = 30;
    private int maxConnPerRoute    = 10;

    // ── 行为配置 ──
    private int maxResultWindow    = 10000;      // 对应 ES 的 index.max_result_window
    private boolean prettyJson     = false;      // 格式化输出 JSON (调试用)
    private String dateFormat      = "yyyy-MM-dd HH:mm:ss";

    // ── Gson 定制 ──
    private List<TypeAdapterFactory> typeAdapterFactories;

    // ── 扫描配置 ──
    private List<String> mapperXmlLocations;     // XML mapper 文件路径
    private List<String> entityPackages;         // 实体类扫描包

    // Builder 模式构造
    public static Builder builder() { ... }
}
```

### 4.4 Spring Boot 配置示例

```yaml
# application.yml
elastic-mapper:
  hosts:
    - 127.0.0.1:9200
    - 127.0.0.1:9201
  scheme: http
  username: elastic
  password: changeme
  connect-timeout-ms: 5000
  socket-timeout-ms: 30000
  max-conn-total: 30
  max-conn-per-route: 10
  date-format: yyyy-MM-dd HH:mm:ss
  mapper-xml-locations:
    - classpath*:/mapper/**/*.xml
  entity-packages:
    - com.example.entity
```

### 4.5 注解 vs XML 优先级规则

当 Mapper 接口中某方法同时存在 `@Select`/`@Update`/`@Delete` 注解 AND XML 中有同名 `<select>`/`<update>`/`<delete>` 时：

1. **XML 优先**: XML 中的 statement 覆盖注解
2. 启动时打印 WARN 日志: `"Method X has both @Select and XML statement; using XML"`
3. 原因: XML 通常承载复杂查询，注解仅用于简单场景

---

## 5. 业务逻辑 / 核心算法

### 5.1 MapperProxy 执行流程

```
MapperProxy.invoke(method, args)
│
├─ 1. 拦截 Object 方法 (toString/equals/hashCode) → 直接执行
│
├─ 2. 查找 MapperMethod
│   ├─ 缓存命中: MapperRegistry.get(interfaceName, methodName)
│   └─ 缓存未命中: 解析并缓存
│       ├─ 检查方法是否有 @Select/@Update/@Delete 注解 → AnnotationStatements
│       ├─ 检查 XML 中是否有同名 statement → XMLStatements (优先)
│       └─ 检查方法是否属于 BaseMapper 基础方法 → BuiltinMethod
│
├─ 3. 构建 MethodInvocation
│   ├─ 解析参数名 (通过 @Param 注解或 -parameters 编译参数获取)
│   └─ 绑定 args[i] → paramName 映射
│
├─ 4. 执行
│   ├─ BuiltinMethod → ElasticTemplate 直接调用
│   └─ Custom Statement → DynamicStatement.execute(invocation)
│       ├─ JexlContext 填充: {paramName → value, ...}
│       ├─ 遍历 StatementNode 树
│       │   ├─ IfNode:  JexlEngine.eval(test) → boolean → 保留/跳过子节点
│       │   ├─ ForeachNode: for(item : collection) → 逐项渲染 + separator
│       │   ├─ ChooseNode: 顺序求值 when[].test → 第一个 true 的分支
│       │   ├─ WhereNode: 自动去除前置 "AND"/"OR" + 包裹 bool query shell
│       │   ├─ SetNode: 自动去除前置逗号
│       │   └─ TextNode: 模板引擎替换 ${var} / #{var}
│       └─ 输出: 完整 JSON 字符串
│
├─ 5. 调用 ElasticTemplate (带拦截器链)
│   ├─ before: interceptorChain.intercept(invocation)
│   ├─ execute: elasticTemplate.query(..., returnType)
│   ├─ after: interceptorChain.after(invocation, result)
│   └─ return: 反序列化后的结果
│
└─ 6. 返回
```

### 5.2 XMLScriptBuilder 解析算法

```
输入: XML Document (来自 mapper XML 文件)
输出: Map<String, DynamicStatement>

对每个 <select>/<update>/<delete>/<insert> 元素:

parseNode(element) → StatementNode:
  switch element.tagName:
    case "if":
      → new IfNode(test=element.attr("test"), children=parseChildren(element))
    case "foreach":
      → new ForeachNode(collection, item, separator, open, close, children)
    case "choose":
      → new ChooseNode(whens=[parseNode(w) for w in element.whenChildren],
                         otherwise=parseNode(element.otherwiseChild))
    case "when":
      → new WhenNode(test=element.attr("test"), children)
    case "otherwise":
      → new OtherwiseNode(children)
    case "where":
      → new WhereNode(children)       // 自动包裹 bool query + 去除前置 AND/OR
    case "set":
      → new SetNode(children)         // 自动去除前置逗号
    case "trim":
      → new TrimNode(prefix, suffix, prefixOverrides, suffixOverrides, children)
    case "agg":
      → new AggNode(name, type, children)
    default:
      → new TextNode(element.textContent)
```

**WhereNode 智能处理逻辑:**

```
WhereNode.render(ctx):
  1. 渲染所有子节点为字符串 s
  2. s = s.trim()
  3. 去除前置 "AND" / "OR" / ","
  4. 去除后置 "AND" / "OR" / ","
  5. 如果 s 为空 → 返回 "" (不输出 bool query)
  6. 如果 s 非空 → 包装: '{"bool": {' + s + '}}'
```

### 5.3 占位符替换规则

| 占位符 | 行为 | 示例 |
|--------|------|------|
| `${var}` | JEXL 模板求值，结果直接拼入 JSON | `"gte": ${ageMin}` → `"gte": 18` |
| `#{var}` | 安全绑定，字符串类型自动加引号 + JSON 转义；数字/布尔不加引号 | `"match": {"name": #{name}}` → `"match": {"name": "John"}` |

**`#{var}` 安全绑定规则:**
- `String` / `char` → JSON 字符串 (`"value"`)
- `Number` (int/long/double/...) → 数值字面量 (`42`, `3.14`)
- `Boolean` → `true` / `false`
- `List` / `Array` → JSON 数组 (`["a", "b"]`)
- `null` → `null`
- 其他对象 → Gson 序列化为 JSON

### 5.4 JEXL 引擎集成

```java
// JexlEngineHolder.java — 单例，线程安全
public final class JexlEngineHolder {
    private static final JexlEngine INSTANCE = new JexlBuilder()
        .namespaces(null)       // 不使用命名空间
        .strict(true)           // 严格模式: 未定义变量抛异常
        .silent(false)          // 不静默: null 属性访问抛异常
        .debug(false)           // 生产环境关闭调试
        .cache(512)             // 缓存已编译表达式
        .create();

    public static JexlEngine get() { return INSTANCE; }

    /** 预编译表达式 (启动时调用) */
    public static JexlExpression compile(String testExpr) {
        return INSTANCE.createExpression(testExpr);
    }

    /** 运行时求值 */
    public static boolean eval(JexlExpression compiled, Map<String, Object> vars) {
        // JexlContext 封装参数
        MapContext ctx = new MapContext();
        vars.forEach(ctx::set);
        Object result = compiled.evaluate(ctx);
        return Boolean.TRUE.equals(result);
    }
}
```

---

## 6. 错误处理

### 6.1 错误分类

| 类别 | 错误码前缀 | HTTP 状态 (如果是 REST API) | 示例 |
|------|-----------|---------------------------|------|
| 配置错误 | `EM-CFG-` | — | `EM-CFG-001: hosts 未配置` |
| 映射错误 | `EM-MAP-` | — | `EM-MAP-001: 未找到 @Id 字段` |
| 语句编译错误 | `EM-SQL-` | — | `EM-SQL-001: XML 解析失败: line 12` |
| 表达式求值错误 | `EM-EXPR-` | — | `EM-EXPR-001: test 表达式求值异常: name == null` |
| ES 通信错误 | `EM-ES-` | — | `EM-ES-001: 连接超时` |
| 序列化错误 | `EM-SER-` | — | `EM-SER-001: Gson 反序列化失败` |

### 6.2 ElasticMapperException 体系

```
ElasticMapperException (RuntimeException)
├── ConfigException            # EM-CFG-*  配置相关
├── MappingException           # EM-MAP-*  映射相关
├── StatementCompileException  # EM-SQL-*  XML 编译期错误 (启动时抛)
├── ExpressionEvalException    # EM-EXPR-* 表达式运行时错误
├── ESExecutionException       # EM-ES-*   ES 通信错误
└── SerializationException     # EM-SER-*  序列化/反序列化错误
```

### 6.3 启动时快速失败

XML Mapper 解析在应用启动时完成。遇到以下情况立即抛出 `StatementCompileException` 阻止启动：
- XML 格式不合法
- `test` 表达式编译失败 (JEXL 语法错误)
- `foreach` 的 `collection` 表达式编译失败
- 未知标签

**错误信息格式:**
```
Failed to compile XML mapper: mapper/UserMapper.xml
  Statement: searchUsers (line 8)
  Cause: Invalid test expression at line 12: "name != null && && age > 0"
         Syntax error: unexpected token '&&' at position 17
```

### 6.4 ES 通信重试策略

```
- 连接超时: 重试 1 次 (间隔 100ms)
- 响应 5xx: 重试 2 次 (间隔 200ms, 400ms)
- 响应 4xx: 不重试，直接抛出 ESExecutionException
- 重试全部失败: 抛出 ESExecutionException，包含原始异常链
```

---

## 7. 性能设计

### 7.1 启动时预编译

| 资源 | 启动时执行 | 运行时使用 |
|------|-----------|-----------|
| XML Mapper 文件 | 解析为 `DynamicStatement` (节点树) | 渲染文本 |
| JEXL `test` 表达式 | 编译为 `JexlExpression` | `evaluate(context)` |
| JEXL 模板 (`${}`) | 编译为 `JexlTemplate` | `template.evaluate(context)` |
| 实体类元数据 | 反射解析 + `EntityMetadata` 缓存 | `metadata.getIndexName()` |

**预热原则:** 启动时完成所有解析和编译工作，运行时仅执行求值和字符串拼接。这确保了运行时零解析开销。

### 7.2 缓存策略

```
MapperProxyFactory     → ConcurrentHashMap<Class, MapperProxy>     (无限，启动时填满)
MapperMethod           → ConcurrentHashMap<String, MapperMethod>   (无限，启动时填满)
EntityMetadata         → ConcurrentHashMap<Class, EntityMetadata>  (无限，懒加载)
JexlExpression         → JEXL 内置 LRU 缓存，容量 512
DynamicStatement       → ConcurrentHashMap<String, DynamicStatement> (无限，启动时填满)
Gson 实例              → ThreadLocal<Gson>？不，单例线程安全，直接复用
```

### 7.3 RestClient 连接池

```java
// 通过 ElasticMapperConfig 透传 RestClientBuilder 配置
RestClientBuilder builder = RestClient.builder(
    hosts.stream().map(HttpHost::create).toArray(HttpHost[]::new)
)
.setHttpClientConfigCallback(httpClientBuilder ->
    httpClientBuilder
        .setMaxConnTotal(config.getMaxConnTotal())          // 默认 30
        .setMaxConnPerRoute(config.getMaxConnPerRoute())    // 默认 10
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectTimeout(config.getConnectTimeoutMs())
            .setSocketTimeout(config.getSocketTimeoutMs())
            .build())
);
```

---

## 8. 测试策略

### 8.1 单元测试

| 测试对象 | 覆盖率目标 | Mock 策略 |
|----------|-----------|----------|
| `MetadataParser` | 100% | 纯逻辑，无需 mock |
| `XMLScriptBuilder` | 100% | 从 classpath 读 XML fixture 文件 |
| `DynamicStatement.render()` | 100% | 构造 MethodInvocation + Map params |
| `JexlEngineHolder` | 95% | 测试各种边界表达式 |
| `TypeAdapterRegistry` | 90% | 纯逻辑 |
| `MapperProxy` | 90% | Mock ElasticTemplate |

**关键测试场景:**

```
XMLScriptBuilder 测试:
  ✓ 空 XML (只有 <mapper> 根元素)
  ✓ 单个 <select> 无标签
  ✓ <if test="..."> true → 保留内容
  ✓ <if test="..."> false → 删除内容
  ✓ 嵌套 <if>
  ✓ <foreach> 列表拼接 (空列表 / 单元素 / 多元素)
  ✓ <choose>/<when>/<otherwise> 匹配第一个 / 匹配中间 / fallback
  ✓ <where> 去除前置 AND/OR
  ✓ <where> 空内容 → 不输出 bool query
  ✓ <set> 去除前置逗号
  ✓ <trim> 自定义 prefix/suffix/prefixOverrides
  ✓ 非法 XML → StatementCompileException
  ✓ 非法 test 表达式 → StatementCompileException

DynamicStatement 测试:
  ✓ ${var} 替换
  ✓ #{var} 安全绑定: String 加引号
  ✓ #{var} 安全绑定: Integer 不加引号
  ✓ #{var} 安全绑定: List → JSON 数组
  ✓ 未定义参数 → ExpressionEvalException

BaseMapper 测试 (需 Docker ES 或 Testcontainers):
  ✓ insert + selectById 往返
  ✓ updateById 局部更新
  ✓ deleteById
  ✓ selectPage 分页
  ✓ nested 对象读写
```

### 8.2 集成测试 (需 ES 实例)

使用 Testcontainers `ElasticsearchContainer`:

```java
@Container
static ElasticsearchContainer es = new ElasticsearchContainer(
    "docker.elastic.co/elasticsearch/elasticsearch:7.17.0"
);

@BeforeAll
static void init() {
    // 创建索引 + mapping
    // 插入测试数据
}
```

### 8.3 验收标准映射

| PRD 故事 | 测试类型 | 关键断言 |
|----------|----------|----------|
| US-001 | 单元 + 集成 | `EntityMetadata.indexName` 正确，约定推断正确 |
| US-002 | 集成 | CRUD 全部通过，Gson 序列化/反序列化往返一致 |
| US-003 | 单元 + 集成 | `@Select("...${name}")` 参数绑定正确 |
| US-004 | 单元 | 所有标签组合覆盖，启动期编译失败快速报错 |
| US-005 | 集成 | `Page.total` / `from` / `search_after` 正确 |
| US-006 | 集成 | `AggResult` 结构与 ES 聚合响应一致 |
| US-007 | 集成 | nested 对象写入 + 查询反序列化正确 |
| US-008 | 单元 | 给定 mapping → 生成的 Java 类结构正确 |
| US-009 | 上下文加载测试 | `ApplicationContext` 加载成功，Mapper Bean 已注入 |

---

## 9. 实施计划

### 9.1 阶段划分

| 阶段 | 内容 | 依赖 | 交付物 |
|------|------|------|--------|
| **P1: 核心骨架** | 模块搭建 + 注解 + 元数据 + Gson 序列化 + ElasticMapperConfig + ElasticTemplate 基础操作 | 无 | `elastic-mapper-core` 基础可运行 |
| **P2: 动态代理 + 注解查询** | MapperProxy + MapperRegistry + BaseMapper 内置方法 + @Select/@Update/@Delete | P1 | 可用注解完成简单 CRUD |
| **P3: XML 解析 + 动态语句引擎** | XMLScriptBuilder + 所有标签 + JEXL 集成 + DynamicStatement | P1 | XML mapper 动态查询可用 |
| **P4: 高级查询** | 分页 + 聚合 + nested 对象映射 + 拦截器链 | P2, P3 | 完成核心功能矩阵 |
| **P5: Spring Boot Starter** | 自动配置 + @MapperScan + Properties | P2, P3 | Spring Boot 开箱即用 |
| **P6: 代码生成器** | IndexIntrospector + TypeMapper + 模板渲染 | P1 | 命令行可用 |

### 9.2 Issue 映射

| Issue | PRD 故事 | SPEC 章节 | 阶段 | 优先级 |
|-------|----------|----------|------|--------|
| #1 项目结构 & 核心注解 | US-001 | 3.1, 3.2 | P1 | 🔴 high |
| #2 Gson 序列化 & ElasticTemplate 基础操作 | US-002 | 4.2, 7.3 | P1 | 🔴 high |
| #3 MapperProxy 动态代理 + BaseMapper | US-002 | 5.1, 4.1 | P2 | 🔴 high |
| #4 @Select/@Update/@Delete 注解查询 | US-003 | 4.1, 5.3 | P2 | 🔴 high |
| #5 XML 标签解析 (XMLScriptBuilder) | US-004 | 3.3, 5.2 | P3 | 🟡 medium |
| #6 JEXL 动态语句引擎集成 | US-004 | 5.3, 5.4 | P3 | 🟡 medium |
| #7 分页查询 (Page + search_after) | US-005 | 3.4, 4.2 | P4 | 🟡 medium |
| #8 聚合查询 | US-006 | 3.4, 4.2 | P4 | 🟢 low |
| #9 嵌套对象映射 | US-007 | 3.1, 3.2 | P4 | 🟢 low |
| #10 拦截器链 + 插件接口 | FR-12 | 5.1 | P4 | 🟢 low |
| #11 Spring Boot 自动配置 | US-009 | 4.4, 模块设计 | P5 | 🟡 medium |
| #12 代码生成器 | US-008 | 模块设计 | P6 | 🟢 low |

### 9.3 渐进交付

| 里程碑 | 用户可验证行为 |
|--------|--------------|
| **M1 (P1 完成)** | 能手动创建 `ElasticTemplate`，通过 Java 代码完成 ES 文档 CRUD |
| **M2 (P2 完成)** | 能定义一个 Mapper 接口 + `@Select` 注解，一行代码完成查询 |
| **M3 (P3 完成)** | 能编写 XML mapper 文件，使用 `<if>`/`<foreach>` 标签完成动态条件查询 |
| **M4 (P4 完成)** | 完整功能矩阵: 分页、聚合、嵌套、拦截器 |
| **M5 (P5 完成)** | Spring Boot 引入 Starter 即可使用，零手动配置 |
| **M6 (P6 完成)** | 运行命令行工具，从 ES 索引反向生成全部代码 |

---

## 10. 开放问题 & 风险

### 10.1 未解决问题

1. **ES 版本兼容边界** — 初始版本锁定 ES 7.17.x，但 REST API 在 7.x 和 8.x 间有细微差异（如 `_doc` vs `_doc` 类型名、total hits tracking）。是否在 SPEC 层面预留版本适配接口？
   - **建议:** 在 `RequestBuilder` 中预留 `EsVersion` 策略枚举，P4 阶段实现。

2. **JSON 片段拼接的边界情况** — 动态语句渲染的 JSON 可能出现多余逗号（如 `{"a":1,}`），需要在哪些层级做最终校验？
   - **建议:** `DynamicStatement.render()` 返回前对完整 JSON 做一次 `JsonParser` 快速校验，失败时抛 `StatementCompileException` 并包含渲染后的 JSON 原文以便调试。

3. **Gson vs Jackson 生态兼容** — 如果用户项目已使用 Jackson，Gson 与 Jackson 共存是否会造成类加载冲突或混淆？
   - **建议:** 依赖声明 `provided` scope (由用户决定)，或在 `elastic-mapper-core` 中使用 shade 重定位 Gson 包名。

4. **代码生成器的目标语言** — 生成的是 Java 源码还是字节码？是否需要支持 Kotlin？
   - **建议:** 优先只生成 Java 源码，Kotlin 在后续版本考虑。

### 10.2 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| JEXL 对 Lambda/Stream API 的支持有限 | 中 | `test` 表达式中不能使用 `list.stream().filter(...)` | 文档明确列出支持的表达式语法边界；建议用户在 Java 代码中预处理数据再传入 |
| ES Low Level Client 在 8.x 中已弃用部分 API | 中 | P4/P5 适配工作量增加 | 最小化直接使用弃用 API 的表面积，集中在 `ElasticTemplate` 中 |
| 启动时全量解析 XML + 编译 JEXL 表达式的耗时 | 低 (XML 文件通常 <50 个) | 100+ XML 时启动变慢 | 提供 Lazy 模式和 Eager 模式配置开关 |
| Gson 与 JDK17+ 的反射限制 | 低 | 序列化失败 | 在 `module-info.java` 中声明 opens，或降级到 `--add-opens` |

### 10.3 假设

以下假设基于当前信息做出，如有偏差需在实施前修正:

- **[A1]** 目标 ES 版本为 7.17.x — 与当前最广泛使用的 ES 版本一致
- **[A2]** 用户 Java 版本 ≥ 8（目标 8+，推荐 11+）
- **[A3]** XML Mapper 文件数量在单个应用中 < 200 个 — 启动期全量预编译在可接受范围内（< 2 秒）
- **[A4]** 用户不依赖 ES X-Pack SQL 或其他专有功能
- **[A5]** 字段映射按驼峰转下划线约定进行，除非有 `@Field(name=...)` 显式覆盖

---

## 附录 A: Maven 依赖清单

```xml
<!-- elastic-mapper-core 核心依赖 -->
<dependencies>
    <!-- ES Low Level Client -->
    <dependency>
        <groupId>org.elasticsearch.client</groupId>
        <artifactId>elasticsearch-rest-client</artifactId>
        <version>7.17.0</version>
    </dependency>

    <!-- Gson -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>

    <!-- Apache JEXL -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-jexl3</artifactId>
        <version>3.3</version>
    </dependency>

    <!-- 测试 -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>elasticsearch</artifactId>
        <version>1.19.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## 附录 B: 与 MyBatis 概念映射

| MyBatis | ElasticMapper | 差异说明 |
|---------|---------------|----------|
| `SqlSession` | `ElasticTemplate` | 都封装底层通信 |
| `SqlSessionFactory` | `ElasticMapperConfig.buildTemplate()` | 简化: 不需要 Factory，直接 Builder |
| `<select>`/`<insert>` | 同名 | SQL → ES JSON DSL |
| `<if test="...">` | 同名 | OGNL → JEXL |
| `<foreach>` | 同名 | 语义相同 |
| `<choose>/<when>/<otherwise>` | 同名 | 语义相同 |
| `<where>` | 同名 | SQL: 自动加 WHERE + 去除 AND；ES: 自动包裹 bool query + 去除 AND |
| `<set>` | 同名 | SQL: UPDATE SET；ES: _update body 的 "doc" 部分 |
| `<trim>` | 同名 | 语义相同 |
| `#{}` vs `${}` | 同名 | SQL 注入防护 → ES JSON 注入防护 |
| `@Select`/`@Update` | 同名 | 语义相同 |
| `@Insert` | `insert()` on BaseMapper | 不提供 `@Insert` 注解，插入走 BaseMapper |
| `RowBounds` | `Page<T>` | MyBatis-Plus 风格 |
| `Plugin` / `Interceptor` | `Interceptor` | 语义相同 |
| MyBatis-Plus `BaseMapper` | `BaseMapper<T>` | 核心方法集对齐 |
