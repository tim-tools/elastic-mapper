package io.github.timtools.elasticmapper.executor;

import io.github.timtools.elasticmapper.annotations.Field;
import io.github.timtools.elasticmapper.annotations.Id;
import io.github.timtools.elasticmapper.annotations.IndexName;
import io.github.timtools.elasticmapper.config.ElasticMapperConfig;
import io.github.timtools.elasticmapper.result.AggR;
import io.github.timtools.elasticmapper.result.Page;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Verification test against real ES instance: 10.0.13.131:9200
 */
@DisplayName("Real ES Verification")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealESVerificationTest {

    static final String ES_HOST = "10.0.13.131:9200";
    static final String INDEX_PREFIX = "elastic_mapper_verify";
    static String INDEX;  // set at runtime in setup()

    static ElasticTemplate template;

    @IndexName("elastic_mapper_verify")
    static class VerifyUser {
        @Id
        private String id;
        private String name;
        private Integer age;
        private Boolean active;

        VerifyUser() {}
        VerifyUser(String id, String name, Integer age, Boolean active) {
            this.id = id; this.name = name; this.age = age; this.active = active;
        }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    @BeforeAll
    static void setup() {
        INDEX = INDEX_PREFIX + "_" + System.currentTimeMillis();

        ElasticMapperConfig config = ElasticMapperConfig.builder()
                .hosts(ES_HOST)
                .scheme("http")
                .username("elastic")
                .password("fx6kjFPdvFkn3XbtB8VB")
                .connectTimeoutMs(5000)
                .socketTimeoutMs(10000)
                .dateFormat("yyyy-MM-dd HH:mm:ss")
                .build();
        template = new ElasticTemplate(config);

        // Create test index with minimal shards (cluster at limit)
        template.performRequest("PUT", "/" + INDEX,
                "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}");
        System.out.println("✅ Connected to ES at " + ES_HOST + " (v8.17), index created: " + INDEX);
    }

    @AfterAll
    static void teardown() {
        if (template != null) {
            try {
                template.performRequest("DELETE", "/" + INDEX, null);
                System.out.println("✅ Test index deleted");
            } catch (Exception e) {
                System.err.println("⚠️  Failed to delete test index: " + e.getMessage());
            }
            try { template.close(); } catch (Exception ignored) {}
        }
    }

    @AfterEach
    void cleanup() {
        try {
            template.performRequest("POST", "/" + INDEX + "/_delete_by_query",
                    "{\"query\":{\"match_all\":{}}}");
            template.performRequest("POST", "/" + INDEX + "/_refresh", null);
        } catch (Exception ignored) {}
    }

    @Test
    @Order(1)
    @DisplayName("1. INSERT — write document and verify response")
    void testInsert() {
        VerifyUser user = new VerifyUser("u001", "Alice", 30, true);
        ESResponse resp = template.insert(INDEX, user, VerifyUser.class);

        assertNotNull(resp, "Response must not be null");
        assertEquals("u001", resp.getId(), "ID should match");
        assertTrue(resp.isCreated(), "Should be 'created' result");
        System.out.println("✅ INSERT: " + resp);
    }

    @Test
    @Order(2)
    @DisplayName("2. SELECT_BY_ID — read back inserted document")
    void testSelectById() {
        // Insert first
        template.insert(INDEX, new VerifyUser("u002", "Bob", 25, false), VerifyUser.class);
        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        VerifyUser fetched = template.selectById(INDEX, "u002", VerifyUser.class);
        assertNotNull(fetched, "Fetched user must not be null");
        assertEquals("Bob", fetched.getName(), "Name must match");
        assertEquals(25, fetched.getAge(), "Age must match");
        assertEquals(false, fetched.getActive(), "Active must match");
        System.out.println("✅ SELECT_BY_ID: " + fetched.getName() + ", age=" + fetched.getAge());
    }

    @Test
    @Order(3)
    @DisplayName("3. UPDATE — partial update")
    void testUpdateById() {
        template.insert(INDEX, new VerifyUser("u003", "Charlie", 35, true), VerifyUser.class);

        VerifyUser partial = new VerifyUser();
        partial.setAge(36);
        ESResponse resp = template.updatePartialById(INDEX, "u003", partial, VerifyUser.class);
        assertTrue(resp.isUpdated(), "Should be 'updated'");

        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        VerifyUser fetched = template.selectById(INDEX, "u003", VerifyUser.class);
        assertEquals("Charlie", fetched.getName(), "Name should be unchanged");
        assertEquals(36, fetched.getAge(), "Age should be updated");
        System.out.println("✅ UPDATE: age " + 35 + " → " + fetched.getAge());
    }

    @Test
    @Order(4)
    @DisplayName("4. DELETE — delete by ID")
    void testDeleteById() {
        template.insert(INDEX, new VerifyUser("d1", "DeleteMe", 50, true), VerifyUser.class);

        ESResponse resp = template.deleteById(INDEX, "d1");
        assertTrue(resp.isDeleted(), "Should be 'deleted'");

        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        VerifyUser fetched = template.selectById(INDEX, "d1", VerifyUser.class);
        assertNull(fetched, "Deleted user should be null");
        System.out.println("✅ DELETE: document removed");
    }

    @Test
    @Order(5)
    @DisplayName("5. QUERY — search by field")
    void testQuery() {
        template.insert(INDEX, new VerifyUser("q1", "Alice", 28, true), VerifyUser.class);
        template.insert(INDEX, new VerifyUser("q2", "Bob", 35, true), VerifyUser.class);
        template.insert(INDEX, new VerifyUser("q3", "Alice", 42, false), VerifyUser.class);
        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        List<VerifyUser> results = template.query(INDEX,
                "{\"query\":{\"match\":{\"name\":\"Alice\"}}}", VerifyUser.class);

        assertEquals(2, results.size(), "Should find 2 Alices");
        System.out.println("✅ QUERY: found " + results.size() + " records matching 'Alice'");
        results.forEach(u -> System.out.println("   - " + u.getName() + ", age=" + u.getAge()));
    }

    @Test
    @Order(6)
    @DisplayName("6. BATCH INSERT — bulk insert multiple documents")
    void testInsertBatch() {
        List<VerifyUser> users = java.util.Arrays.asList(
                new VerifyUser("b1", "User1", 20, true),
                new VerifyUser("b2", "User2", 30, false),
                new VerifyUser("b3", "User3", 40, true));

        ESResponse resp = template.insertBatch(INDEX, users, VerifyUser.class);
        assertTrue(resp.isAcknowledged(), "Bulk insert should be acknowledged");

        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        List<VerifyUser> all = template.selectByIds(INDEX,
                Arrays.asList("b1", "b2", "b3"), VerifyUser.class);
        assertEquals(3, all.size(), "Should fetch all 3 batch-inserted docs");
        System.out.println("✅ BATCH_INSERT: inserted and fetched back " + all.size() + " documents");
    }

    @Test
    @Order(7)
    @DisplayName("7. PAGINATION — from/size pagination")
    void testPagination() {
        // Insert 10 docs
        for (int i = 1; i <= 10; i++) {
            template.insert(INDEX, new VerifyUser("p" + i, "User" + i, 20 + i, i % 2 == 0), VerifyUser.class);
        }
        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        Page<VerifyUser> page = new Page<>(1, 3);
        page = template.queryPage(INDEX,
                "{\"query\":{\"match_all\":{}},\"from\":" + page.from() + ",\"size\":" + page.getSize() + ",\"sort\":[{\"age\":\"asc\"}]}",
                page, VerifyUser.class);

        assertTrue(page.getTotal() >= 10, "Total should be at least 10");
        assertEquals(3, page.getRecords().size(), "Page size should be 3");
        assertTrue(page.isHasNext(), "Should have next page");
        System.out.println("✅ PAGINATION: total=" + page.getTotal() + ", pages=" + page.getPages() +
                ", current page size=" + page.getRecords().size());
    }

    @Test
    @Order(8)
    @DisplayName("8. AGGREGATION — terms aggregation")
    void testAggregation() {
        template.insert(INDEX, new VerifyUser("a1", "Alice", 28, true), VerifyUser.class);
        template.insert(INDEX, new VerifyUser("a2", "Bob", 35, false), VerifyUser.class);
        template.insert(INDEX, new VerifyUser("a3", "Alice", 42, true), VerifyUser.class);
        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        String aggQuery = "{\"size\":0,\"aggs\":{\"by_name\":{\"terms\":{\"field\":\"name.keyword\"}}}}";
        AggR<JsonObject> response = template.queryAggs(INDEX, aggQuery,
                new TypeToken<AggR<JsonObject>>() {}.getType());

        JsonObject aggs = response.getAggregations();
        assertNotNull(aggs, "Should have aggregation results");
        JsonObject byName = aggs.getAsJsonObject("by_name");
        assertNotNull(byName, "Should have 'by_name' aggregation");
        assertTrue(byName.has("buckets"), "Should have buckets");
        JsonArray buckets = byName.getAsJsonArray("buckets");
        System.out.println("✅ AGGREGATION: " + buckets.size() + " buckets");
        buckets.forEach(b -> {
            JsonObject bucket = b.getAsJsonObject();
            System.out.println("   - " + bucket.get("key").getAsString()
                    + ": " + bucket.get("doc_count").getAsLong());
        });
    }

    @Test
    @Order(9)
    @DisplayName("9. SELECT_LIST — query all with match_all")
    void testSelectList() {
        template.insert(INDEX, new VerifyUser("s1", "ListUser1", 22, true), VerifyUser.class);
        template.insert(INDEX, new VerifyUser("s2", "ListUser2", 33, false), VerifyUser.class);
        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        List<VerifyUser> all = template.query(INDEX,
                "{\"query\":{\"match_all\":{}},\"size\":100}", VerifyUser.class);

        assertFalse(all.isEmpty(), "Should find documents");
        System.out.println("✅ SELECT_LIST: " + all.size() + " total documents in index");
    }

    @Test
    @Order(10)
    @DisplayName("10. DELETE_BY_QUERY — condition-based deletion")
    void testDeleteByQuery() {
        template.insert(INDEX, new VerifyUser("dq1", "Temp", 10, true), VerifyUser.class);
        template.insert(INDEX, new VerifyUser("dq2", "Temp", 20, true), VerifyUser.class);
        template.insert(INDEX, new VerifyUser("dq3", "Keep", 30, true), VerifyUser.class);
        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        ESResponse resp = template.deleteByQuery(INDEX,
                "{\"query\":{\"match\":{\"name\":\"Temp\"}}}");
        assertTrue(resp.isAcknowledged(), "Delete-by-query should succeed");

        template.performRequest("POST", "/" + INDEX + "/_refresh", null);

        List<VerifyUser> remaining = template.query(INDEX,
                "{\"query\":{\"match\":{\"name\":\"Keep\"}}}", VerifyUser.class);
        assertEquals(1, remaining.size(), "One 'Keep' doc should remain");
        System.out.println("✅ DELETE_BY_QUERY: removed Temp docs, " + remaining.size() + " remaining");
    }
}
