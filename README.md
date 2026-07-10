# ElasticMapper

> MyBatis 风格的 Elasticsearch ORM 框架

[![Java](https://img.shields.io/badge/Java-17+-blue)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

ElasticMapper 将 MyBatis 的编程范式带入 Elasticsearch——通过接口 + 注解 / XML 定义查询，框架自动生成 ES 请求并映射返回值。

## 特性

- **MyBatis 风格 API** — `@Select` / `@Update` / `@Delete` 注解, XML 动态 SQL
- **无 Spring 依赖 Core** — `elastic-mapper-core` 可独立使用
- **Spring Boot 自动配置** — `@ESMapperScan` 一键集成
- **分页** — from/size + search_after 深分页
- **聚合查询** — `AggR<T>` + `@JsonPath` 类型安全提取
- **插件拦截器** — 对标 MyBatis Interceptor
- **内嵌 ES 低层客户端** — 零额外 ES 依赖

## 快速开始

```xml
<dependency>
    <groupId>io.github.timtools</groupId>
    <artifactId>elastic-mapper-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
elastic-mapper:
  hosts:
    - 127.0.0.1:9200
```

```java
// 1. 实体
import io.github.timtools.elasticmapper.annotations.Id;
import io.github.timtools.elasticmapper.annotations.IndexName;

@IndexName("users")
public class User {
    @Id private String id;
    private String name;
    private Integer age;

    public User() {}
    public User(String id, String name, Integer age) { /* ... */ }
    // getters / setters ...
}

// 2. Mapper 接口
import io.github.timtools.elasticmapper.binding.BaseMapper;
import io.github.timtools.elasticmapper.annotations.Select;
import io.github.timtools.elasticmapper.annotations.Param;
import io.github.timtools.elasticmapper.result.AggR;

public interface UserMapper extends BaseMapper<User> {

    @Select("{\"query\":{\"match\":{\"name\":#{keyword}}}}")
    List<User> searchByName(@Param("keyword") String keyword);

    @Select("{\"size\":0,\"aggs\":{\"by_age\":{\"terms\":{\"field\":\"age\"}}}}")
    AggR<Map<String, Object>> countByAge();
}

// 3. 启动类（@ESMapperScan 扫描并注册 Mapper 接口）
import io.github.timtools.elasticmapper.spring.scan.ESMapperScan;

@SpringBootApplication
@ESMapperScan("com.example.mapper")
public class Application { /* ... */ }

// 4. 使用
@Autowired UserMapper userMapper;

userMapper.insert(new User("1", "Alice", 28));
User u = userMapper.selectById("1");
List<User> hits = userMapper.searchByName("Bob");
```

## 模块

| 模块 | 说明 |
|------|------|
| `elastic-mapper-core` | 核心引擎：注解、绑定、XML 解析、Gson 序列化、ES 客户端 |
| `elastic-mapper-spring-boot-starter` | Spring Boot 自动配置、`@ESMapperScan`、`MapperFactoryBean` |
| `elastic-mapper-example` | 完整示例应用 |

## 文档

- [使用说明](使用说明.md) — 完整 API 参考

## 环境要求

- Java 17+
- Spring Boot 3.x（使用 starter 时）
- Elasticsearch 7.x

## 许可

MIT License
