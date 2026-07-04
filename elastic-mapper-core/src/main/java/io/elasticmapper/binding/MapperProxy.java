package io.elasticmapper.binding;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.elasticmapper.annotations.Delete;
import io.elasticmapper.annotations.Param;
import io.elasticmapper.annotations.Select;
import io.elasticmapper.annotations.Update;
import io.elasticmapper.executor.ESResponse;
import io.elasticmapper.executor.ElasticMapperException;
import io.elasticmapper.executor.ElasticTemplate;
import io.elasticmapper.metadata.EntityMetadata;
import io.elasticmapper.metadata.MetadataParser;
import io.elasticmapper.parser.XMLMapperParser;
import io.elasticmapper.plugin.InterceptorChain;
import io.elasticmapper.result.AggR;
import io.elasticmapper.result.Page;
import io.elasticmapper.script.DynamicStatement;
import io.elasticmapper.binding.MapperMethod.BuiltinMethod;
import io.elasticmapper.binding.MapperMethod.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDK dynamic proxy {@link InvocationHandler} for Mapper interfaces.
 * Routes method calls to either:
 * <ul>
 *   <li>Built-in {@link ElasticTemplate} methods (when extending {@link BaseMapper})</li>
 *   <li>Custom annotation-driven statements ({@link Select}/{@link Update}/{@link Delete})</li>
 *   <li>XML-defined statements (resolved by {@link MapperRegistry})</li>
 * </ul>
 */
public class MapperProxy implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(MapperProxy.class);

    private final ElasticTemplate template;
    private final MapperRegistry registry;
    private final Class<?> mapperInterface;
    private final Class<?> entityClass;
    private final XMLMapperParser xmlParser;
    private final InterceptorChain interceptorChain;

    public MapperProxy(ElasticTemplate template,
                       MapperRegistry registry,
                       Class<?> mapperInterface,
                       Class<?> entityClass) {
        this(template, registry, mapperInterface, entityClass, null, null);
    }

    public MapperProxy(ElasticTemplate template,
                       MapperRegistry registry,
                       Class<?> mapperInterface,
                       Class<?> entityClass,
                       XMLMapperParser xmlParser) {
        this(template, registry, mapperInterface, entityClass, xmlParser, null);
    }

    public MapperProxy(ElasticTemplate template,
                       MapperRegistry registry,
                       Class<?> mapperInterface,
                       Class<?> entityClass,
                       XMLMapperParser xmlParser,
                       InterceptorChain interceptorChain) {
        this.template = template;
        this.registry = registry;
        this.mapperInterface = mapperInterface;
        this.entityClass = entityClass;
        this.xmlParser = xmlParser;
        this.interceptorChain = interceptorChain;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. Intercept Object methods
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        // 2. Look up or resolve MapperMethod
        MapperMethod mapperMethod = registry.getMethod(mapperInterface, method);
        if (mapperMethod == null) {
            mapperMethod = resolveMethod(method);
            registry.putMethod(mapperInterface, method, mapperMethod);
        }

        // 3. Build invocation
        MethodInvocation invocation = buildInvocation(method, args);

        // 4. Interceptor chain — fire before
        if (interceptorChain != null) {
            interceptorChain.fireBefore(invocation);
        }

        Object result;
        try {
            // 5. Dispatch
            switch (mapperMethod.getType()) {
            case BUILTIN:
                result = dispatchBuiltin(mapperMethod.getBuiltinMethod(), invocation);
                break;

            case ANNOTATION:
                result = dispatchAnnotation(mapperMethod, invocation);
                break;

            case XML:
                result = dispatchXml(mapperMethod, invocation);
                break;

            default:
                throw new ElasticMapperException("EM-BIND-001",
                        "No statement mapping found for method: " + method.getName() +
                        " in " + mapperInterface.getName() +
                        ". Add @Select, @Update, @Delete annotation, or define an XML statement.");
            }
        } finally {
            // 6. Interceptor chain — fire after (even on exception)
            if (interceptorChain != null) {
                interceptorChain.fireAfter(invocation, null);
            }
        }
        return result;
    }

    // ── Method resolution ──

    private MapperMethod resolveMethod(Method method) {
        // Check for annotations first
        Select selectAnn = method.getAnnotation(Select.class);
        if (selectAnn != null) {
            return new MapperMethod(method, Type.ANNOTATION, selectAnn.value());
        }
        Update updateAnn = method.getAnnotation(Update.class);
        if (updateAnn != null) {
            return new MapperMethod(method, Type.ANNOTATION, updateAnn.value());
        }
        Delete deleteAnn = method.getAnnotation(Delete.class);
        if (deleteAnn != null) {
            return new MapperMethod(method, Type.ANNOTATION, deleteAnn.value());
        }

        // Check for built-in BaseMapper methods
        BuiltinMethod builtin = resolveBuiltin(method);
        if (builtin != null) {
            return new MapperMethod(method, builtin);
        }

        // Check XML registry
        if (xmlParser != null) {
            String namespace = mapperInterface.getSimpleName();
            DynamicStatement stmt = xmlParser.getStatement(namespace, method.getName());
            if (stmt != null) {
                return new MapperMethod(method, Type.XML, null);
            }
        }

        // Unresolved — XML may provide it later
        return new MapperMethod(method, Type.UNRESOLVED, null);
    }

    private BuiltinMethod resolveBuiltin(Method method) {
        String name = method.getName();
        Class<?>[] paramTypes = method.getParameterTypes();

        switch (name) {
            case "insert":
                return paramTypes.length == 1 ? BuiltinMethod.INSERT : null;
            case "insertBatch":
                return BuiltinMethod.INSERT_BATCH;
            case "selectById":
                return paramTypes.length == 1 && paramTypes[0] == String.class
                        ? BuiltinMethod.SELECT_BY_ID : null;
            case "selectByIds":
                return BuiltinMethod.SELECT_BY_IDS;
            case "selectList":
                return paramTypes.length == 0 ? BuiltinMethod.SELECT_LIST : null;
            case "selectPage":
                return BuiltinMethod.SELECT_PAGE;
            case "updateById":
                return paramTypes.length == 1 ? BuiltinMethod.UPDATE_BY_ID : null;
            case "updatePartialById":
                return paramTypes.length == 1 ? BuiltinMethod.UPDATE_PARTIAL_BY_ID : null;
            case "deleteById":
                return paramTypes.length == 1 && paramTypes[0] == String.class
                        ? BuiltinMethod.DELETE_BY_ID : null;
            case "deleteByIds":
                return BuiltinMethod.DELETE_BY_IDS;
            default:
                return null;
        }
    }

    // ── Invocation building ──

    private MethodInvocation buildInvocation(Method method, Object[] args) {
        Map<String, Object> paramMap = new LinkedHashMap<>();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            if (args == null || i >= args.length) break;

            // @Param annotation takes priority
            Param paramAnn = parameters[i].getAnnotation(Param.class);
            if (paramAnn != null) {
                paramMap.put(paramAnn.value(), args[i]);
            } else if (parameters[i].isNamePresent()) {
                // -parameters compiler flag
                paramMap.put(parameters[i].getName(), args[i]);
            } else {
                // Fallback: argN
                paramMap.put("arg" + i, args[i]);
            }
        }

        return new MethodInvocation(method, args, paramMap);
    }

    // ── Built-in dispatch ──

    @SuppressWarnings("unchecked")
    private Object dispatchBuiltin(BuiltinMethod builtin, MethodInvocation inv) {
        EntityMetadata meta = MetadataParser.parse(entityClass);
        String index = meta.getIndexName();
        // Safe cast: entityClass is always the correct entity type for this mapper
        Class<Object> ec = (Class<Object>) entityClass;

        switch (builtin) {
            case INSERT:
                return template.insert(index, inv.getArg(0), ec);
            case INSERT_BATCH:
                return template.insertBatch(index, (List) inv.getArg(0), ec);
            case SELECT_BY_ID:
                return template.selectById(index, (String) inv.getArg(0), ec);
            case SELECT_BY_IDS:
                return template.selectByIds(index, (List<String>) inv.getArg(0), ec);
            case SELECT_LIST:
                return template.query(index,
                        "{\"query\":{\"match_all\":{}},\"size\":" + template.getConfig().getMaxResultWindow() + "}",
                        ec);
            case SELECT_PAGE: {
                Page<?> page = (Page<?>) inv.getArg(0);
                return selectPage(index, page);
            }
            case UPDATE_BY_ID: {
                Object entity = inv.getArg(0);
                String id = resolveId(entity);
                return template.updateById(index, id, entity, ec);
            }
            case UPDATE_PARTIAL_BY_ID: {
                Object entity = inv.getArg(0);
                String id = resolveId(entity);
                return template.updatePartialById(index, id, entity, ec);
            }
            case DELETE_BY_ID:
                return template.deleteById(index, (String) inv.getArg(0));
            case DELETE_BY_IDS:
                return deleteByIds(index, (List<String>) inv.getArg(0));
            default:
                throw new ElasticMapperException("EM-BIND-002",
                        "Unknown built-in method: " + builtin);
        }
    }

    // ── Annotation dispatch (delegates to DynamicStatement in #004, currently placeholder) ──

    @SuppressWarnings("unchecked")
    private Object dispatchAnnotation(MapperMethod mapperMethod, MethodInvocation inv) {
        EntityMetadata meta = MetadataParser.parse(entityClass);
        String index = meta.getIndexName();

        // Simple placeholder substitution for annotation-based queries
        String jsonBody = replacePlaceholders(mapperMethod.getStatementSource(), inv.getParamMap());

        log.debug("Executing annotation query on index {}: {}", index, jsonBody);

        Method method = mapperMethod.getMethod();
        if (method.getAnnotation(Delete.class) != null) {
            return template.deleteByQuery(index, jsonBody);
        } else if (method.getAnnotation(Update.class) != null) {
            return template.performRequest("POST", "/" + index + "/_update_by_query", jsonBody);
        } else {
            // @Select — search query or aggregation query
            if (method.getReturnType() == AggR.class) {
                return template.queryAggs(index, jsonBody, method.getGenericReturnType());
            }
            return template.query(index, jsonBody, (Class<Object>) entityClass);
        }
    }

    // ── XML dispatch ──

    @SuppressWarnings("unchecked")
    private Object dispatchXml(MapperMethod mapperMethod, MethodInvocation inv) {
        EntityMetadata meta = MetadataParser.parse(entityClass);
        String index = meta.getIndexName();
        Method method = mapperMethod.getMethod();

        // Look up the pre-compiled statement (OGNL expressions already parsed)
        String namespace = mapperInterface.getSimpleName();
        DynamicStatement stmt = xmlParser.getStatement(namespace, method.getName());
        if (stmt == null) {
            throw new ElasticMapperException("EM-BIND-003",
                    "No XML statement found for: " + namespace + "::" + method.getName());
        }

        // Render with pre-compiled OGNL expressions — placeholder substitution
        // happens inside TextNode.render(); no second pass needed
        String jsonBody = stmt.render(inv.getParamMap());

        log.debug("Executing XML query on index {}: {}", index, jsonBody);

        // Route based on return type — same branching as annotation dispatch
        if (method.getReturnType() == AggR.class) {
            return template.queryAggs(index, jsonBody, method.getGenericReturnType());
        }
        if (method.getReturnType() == List.class || method.getReturnType() == Page.class) {
            return template.query(index, jsonBody, (Class<Object>) entityClass);
        }
        // Fallback: treat as search query
        return template.query(index, jsonBody, (Class<Object>) entityClass);
    }

    // ── Placeholder replacement ──

    /**
     * Simple placeholder substitution for annotation-based queries.
     * Supports {@code ${param}} and {@code #{param}}.
     */
    public static String replacePlaceholders(String source, Map<String, Object> params) {
        if (source == null || params.isEmpty()) {
            return source;
        }

        String result = source;

        // #{param} — safe binding with type-aware quoting
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String replacement = formatSafe(value);
            result = result.replace("#{" + key + "}", replacement);
        }

        // ${param} — direct template substitution
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            result = result.replace("${" + key + "}", value != null ? value.toString() : "null");
        }

        return result;
    }

    /**
     * Formats a value using safe binding rules:
     * - String: JSON-quoted and escaped
     * - Number: literal
     * - Boolean: true/false
     * - List: JSON array
     * - null: null
     */
    public static String formatSafe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + ElasticTemplate.escapeJson((String) value) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(formatSafe(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        // Fallback: JSON-quote the toString
        return "\"" + ElasticTemplate.escapeJson(value.toString()) + "\"";
    }

    // ── Helpers ──

    private String resolveId(Object entity) {
        EntityMetadata meta = MetadataParser.parse(entityClass);
        if (meta.getIdField() != null) {
            try {
                Object idVal = meta.getIdField().getJavaField().get(entity);
                if (idVal != null) {
                    return idVal.toString();
                }
            } catch (IllegalAccessException e) {
                throw new ElasticMapperException("EM-MAP-003",
                        "Cannot access @Id field: " + meta.getIdField().getJavaName(), e);
            }
        }
        throw new ElasticMapperException("EM-MAP-002",
                "No @Id field value found on entity. Cannot perform update/delete without an ID.");
    }

    @SuppressWarnings("unchecked")
    private <T> Page<T> selectPage(String index, Page<T> page) {
        StringBuilder jsonBody = new StringBuilder("{\"query\":{\"match_all\":{}},\"size\":").append(page.getSize());

        if (page.isFromSizeMode()) {
            // from/size 传统分页
            jsonBody.append(",\"from\":").append(page.from());
        } else {
            // search_after 深分页
            String sortJson = page.buildSortJson();
            if (sortJson != null) {
                jsonBody.append(",\"sort\":").append(sortJson);
            }
            String saJson = page.buildSearchAfterJson();
            if (saJson != null) {
                jsonBody.append(",\"search_after\":").append(saJson);
            }
        }

        jsonBody.append("}");

        return template.queryPage(index, jsonBody.toString(), page, (Class<T>) entityClass);
    }

    private ESResponse deleteByIds(String index, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ESResponse(index, null, "noop", 0, true);
        }

        StringBuilder queryBody = new StringBuilder("{\"query\":{\"ids\":{\"values\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) queryBody.append(",");
            queryBody.append("\"").append(ElasticTemplate.escapeJson(ids.get(i))).append("\"");
        }
        queryBody.append("]}}}");

        return template.deleteByQuery(index, queryBody.toString());
    }
}
