package com.ecs160.persistence;

import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.PersistableField;
import com.ecs160.persistence.annotations.PersistableObject;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AppTest {
    private RedisDB redisDB;
    private static final int TEST_DB = 15; // Use a different database for testing

    @Before
    public void setUp() {
        redisDB = new RedisDB("localhost", 6379, TEST_DB);
        // clean test database
        Jedis jedis = new Jedis("localhost", 6379);
        jedis.select(TEST_DB);
        jedis.flushDB();
        jedis.close();
    }

    @Test
    public void testPersistSimpleObject() {
        TestSimpleObject obj = new TestSimpleObject();
        obj.setId("test-1");
        obj.setName("Test Name");
        obj.setValue(42);

        boolean result = redisDB.persist(obj);
        assertTrue("Should persist successfully", result);

        // verify it was persisted
        TestSimpleObject loaded = (TestSimpleObject) redisDB.load(obj);
        assertNotNull("Loaded object should not be null", loaded);
        assertEquals("test-1", loaded.getId());
        assertEquals("Test Name", loaded.getName());
        assertEquals(42, loaded.getValue());
    }

    @Test
    public void testPersistObjectWithDate() {
        TestSimpleObject obj = new TestSimpleObject();
        obj.setId("test-2");
        obj.setName("Test Date");
        Date testDate = new Date();
        obj.setCreatedAt(testDate);

        boolean result = redisDB.persist(obj);
        assertTrue("Should persist successfully", result);

        TestSimpleObject loaded = (TestSimpleObject) redisDB.load(obj);
        assertNotNull("Loaded object should not be null", loaded);
        assertNotNull("Date should be loaded", loaded.getCreatedAt());
    }

    @Test
    public void testPersistObjectWithList() {
        TestObjectWithList obj = new TestObjectWithList();
        obj.setId("test-3");
        List<String> items = new ArrayList<>();
        items.add("item1");
        items.add("item2");
        items.add("item3");
        obj.setItems(items);

        boolean result = redisDB.persist(obj);
        assertTrue("Should persist successfully", result);

        TestObjectWithList loaded = (TestObjectWithList) redisDB.load(obj);
        assertNotNull("Loaded object should not be null", loaded);
        assertNotNull("List should not be null", loaded.getItems());
        assertEquals(3, loaded.getItems().size());
        assertEquals("item1", loaded.getItems().get(0));
        assertEquals("item2", loaded.getItems().get(1));
        assertEquals("item3", loaded.getItems().get(2));
    }

    @Test
    public void testPersistObjectWithNestedObject() {
        TestNestedObject parent = new TestNestedObject();
        parent.setId("parent-1");
        parent.setName("Parent");

        TestSimpleObject child = new TestSimpleObject();
        child.setId("child-1");
        child.setName("Child");
        child.setValue(100);
        parent.setChild(child);

        boolean result = redisDB.persist(parent);
        assertTrue("Should persist successfully", result);

        TestNestedObject loaded = (TestNestedObject) redisDB.load(parent);
        assertNotNull("Loaded object should not be null", loaded);
        assertNotNull("Child should not be null", loaded.getChild());
        assertEquals("child-1", loaded.getChild().getId());
        assertEquals("Child", loaded.getChild().getName());
        assertEquals(100, loaded.getChild().getValue());
    }

    @Test
    public void testLoadNonExistentObject() {
        TestSimpleObject obj = new TestSimpleObject();
        obj.setId("non-existent");

        Object loaded = redisDB.load(obj);
        assertNull("Should return null for non-existent object", loaded);
    }

    @Test
    public void testPersistNullObject() {
        boolean result = redisDB.persist(null);
        assertFalse("Should return false for null object", result);
    }

    @Test
    public void testPersistObjectWithoutId() {
        TestObjectWithoutId obj = new TestObjectWithoutId();
        obj.setName("Test");

        try {
            redisDB.persist(obj);
            fail("Should throw exception for object without @Id");
        } catch (RuntimeException e) {
            assertTrue("Exception should mention @Id", e.getMessage().contains("@Id"));
        }
    }

    @Test
    public void testPersistObjectWithNullId() {
        TestSimpleObject obj = new TestSimpleObject();
        obj.setId(null);
        obj.setName("Test");

        try {
            redisDB.persist(obj);
            fail("Should throw exception for object with null id");
        } catch (RuntimeException e) {
            assertTrue("Exception should mention null id", e.getMessage().contains("null"));
        }
    }

    @Test
    public void testPersistAndLoadMultipleObjects() {
        TestSimpleObject obj1 = new TestSimpleObject();
        obj1.setId("obj-1");
        obj1.setName("Object 1");
        obj1.setValue(10);

        TestSimpleObject obj2 = new TestSimpleObject();
        obj2.setId("obj-2");
        obj2.setName("Object 2");
        obj2.setValue(20);

        assertTrue("Should persist obj1", redisDB.persist(obj1));
        assertTrue("Should persist obj2", redisDB.persist(obj2));

        TestSimpleObject loaded1 = (TestSimpleObject) redisDB.load(obj1);
        TestSimpleObject loaded2 = (TestSimpleObject) redisDB.load(obj2);

        assertNotNull("Loaded obj1 should not be null", loaded1);
        assertNotNull("Loaded obj2 should not be null", loaded2);
        assertEquals("Object 1", loaded1.getName());
        assertEquals("Object 2", loaded2.getName());
        assertEquals(10, loaded1.getValue());
        assertEquals(20, loaded2.getValue());
    }

    @Test
    public void testPersistObjectWithEmptyList() {
        TestObjectWithList obj = new TestObjectWithList();
        obj.setId("test-empty-list");
        obj.setItems(new ArrayList<>());

        boolean result = redisDB.persist(obj);
        assertTrue("Should persist successfully", result);

        TestObjectWithList loaded = (TestObjectWithList) redisDB.load(obj);
        assertNotNull("Loaded object should not be null", loaded);
        assertNotNull("List should not be null", loaded.getItems());
        assertEquals(0, loaded.getItems().size());
    }

    // test classes
    @PersistableObject
    static class TestSimpleObject {
        @Id
        @PersistableField
        private String id;

        @PersistableField
        private String name;

        @PersistableField
        private int value;

        @PersistableField
        private Date createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    }

    @PersistableObject
    static class TestObjectWithList {
        @Id
        @PersistableField
        private String id;

        @PersistableField
        private List<String> items;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
    }

    @PersistableObject
    static class TestNestedObject {
        @Id
        @PersistableField
        private String id;

        @PersistableField
        private String name;

        @PersistableField
        private TestSimpleObject child;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public TestSimpleObject getChild() { return child; }
        public void setChild(TestSimpleObject child) { this.child = child; }
    }

    @PersistableObject
    static class TestObjectWithoutId {
        @PersistableField
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
