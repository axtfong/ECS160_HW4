package com.ecs160.persistence;

import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.LazyLoad;
import com.ecs160.persistence.annotations.PersistableField;
import com.ecs160.persistence.annotations.PersistableObject;
import redis.clients.jedis.Jedis;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class RedisDB {
    private Jedis jedis;
    private int defaultDatabase;
    private SimpleDateFormat dateFormat;

    public RedisDB() {
        this("localhost", 6379, 0);
    }

    public RedisDB(String host, int port, int database) {
        this.jedis = new Jedis(host, port);
        this.defaultDatabase = database;
        this.jedis.select(database);
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    }

    public boolean persist(Object o) {
        if (o == null) {
            return false;
        }

        try {
            Class<?> clazz = o.getClass();
            
            // check if class is annotated with @PersistableObject
            if (!clazz.isAnnotationPresent(PersistableObject.class)) {
                return false;
            }

            // find @Id field
            Field idField = findIdField(clazz);
            if (idField == null) {
                throw new RuntimeException("Class " + clazz.getName() + " must have a field annotated with @Id");
            }

            idField.setAccessible(true);
            Object idValue = idField.get(o);
            if (idValue == null) {
                throw new RuntimeException("Id field cannot be null for class " + clazz.getName());
            }

            String objectKey = idValue.toString();
            String className = clazz.getName();

            // persist object's fields
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(PersistableField.class)) {
                    field.setAccessible(true);
                    Object fieldValue = field.get(o);

                    if (fieldValue == null) {
                        jedis.hset(objectKey, field.getName(), "");
                        continue;
                    }

                    Class<?> fieldType = field.getType();

                    // handle list collections
                    if (List.class.isAssignableFrom(fieldType)) {
                        String redisKey = mapFieldNameToRedis(field.getName());
                        persistList(objectKey, redisKey, (List<?>) fieldValue);
                    }
                    // handle nested objects
                    else if (fieldType.isAnnotationPresent(PersistableObject.class)) {
                        String redisKey = mapFieldNameToRedis(field.getName());
                        persist(objectKey, redisKey, fieldValue);
                    }
                    // handle primitive types and strings
                    else {
                        String valueStr = convertToString(fieldValue);
                        String redisKey = mapFieldNameToRedis(field.getName());
                        jedis.hset(objectKey, redisKey, valueStr);
                    }
                }
            }

            // store class name for later loading
            jedis.hset(objectKey, "_class", className);

            return true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error persisting object: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void persist(String parentKey, String fieldName, Object nestedObject) {
        if (nestedObject == null) {
            jedis.hset(parentKey, fieldName, "");
            return;
        }

        persist(nestedObject);
        
        // store reference to nested object using id
        Field idField = findIdField(nestedObject.getClass());
        if (idField != null) {
            idField.setAccessible(true);
            try {
                Object nestedId = idField.get(nestedObject);
                jedis.hset(parentKey, fieldName, nestedId.toString());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void persistList(String parentKey, String fieldName, List<?> list) {
        if (list == null || list.isEmpty()) {
            jedis.hset(parentKey, fieldName, "");
            return;
        }

        List<String> itemIds = new ArrayList<>();
        
        for (Object item : list) {
            if (item == null) {
                continue;
            }

            // if item is @PersistableObject, persist and store id
            if (item.getClass().isAnnotationPresent(PersistableObject.class)) {
                persist(item);
                
                Field idField = findIdField(item.getClass());
                if (idField != null) {
                    idField.setAccessible(true);
                    try {
                        Object itemId = idField.get(item);
                        if (itemId != null) {
                            itemIds.add(itemId.toString());
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                // for primitive types in lists, store them directly
                itemIds.add(convertToString(item));
            }
        }

        // store comma-separated list of ids or values
        String listValue = String.join(",", itemIds);
        jedis.hset(parentKey, fieldName, listValue);
    }

    public Object load(Object o) {
        if (o == null) {
            return null;
        }

        try {
            Class<?> clazz = o.getClass();
            
            // check if class is annotated with @PersistableObject
            if (!clazz.isAnnotationPresent(PersistableObject.class)) {
                return null;
            }

            // find @Id field
            Field idField = findIdField(clazz);
            if (idField == null) {
                throw new RuntimeException("Class " + clazz.getName() + " must have a field annotated with @Id");
            }

            idField.setAccessible(true);
            Object idValue = idField.get(o);
            if (idValue == null) {
                throw new RuntimeException("Id field cannot be null for class " + clazz.getName());
            }

            String objectKey = idValue.toString();

            // check if object exists in redis
            if (!jedis.exists(objectKey)) {
                return null;
            }

            // create new instance
            Object instance = clazz.getDeclaredConstructor().newInstance();

            // load all fields
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(PersistableField.class)) {
                    field.setAccessible(true);
                    
                    // check if field is lazy loaded
                    if (isLazyLoaded(clazz, field.getName())) {
                        // don't load lazy fields immediately
                        continue;
                    }

                    String redisKey = mapFieldNameToRedis(field.getName());
                    String fieldValueStr = jedis.hget(objectKey, redisKey);
                    if (fieldValueStr == null || fieldValueStr.isEmpty()) {
                        // try w original field name as fallback
                        fieldValueStr = jedis.hget(objectKey, field.getName());
                    }
                    // try common variations for url field or search all fields
                    if ((fieldValueStr == null || fieldValueStr.isEmpty()) && "url".equalsIgnoreCase(field.getName())) {
                        // try common case variations
                        fieldValueStr = jedis.hget(objectKey, "Url");
                        if (fieldValueStr == null || fieldValueStr.isEmpty()) {
                            fieldValueStr = jedis.hget(objectKey, "URL");
                        }
                        // if still not found, search all hash fields for url-like keys
                        if ((fieldValueStr == null || fieldValueStr.isEmpty())) {
                            Map<String, String> allFields = jedis.hgetAll(objectKey);
                            for (Map.Entry<String, String> entry : allFields.entrySet()) {
                                String key = entry.getKey();
                                if (key != null && (key.equalsIgnoreCase("url") || 
                                    key.equalsIgnoreCase("htmlUrl") ||
                                    key.toLowerCase().contains("url"))) {
                                    fieldValueStr = entry.getValue();
                                    break;
                                }
                            }
                        }
                    }

                    Class<?> fieldType = field.getType();

                    // handle list collections
                    if (List.class.isAssignableFrom(fieldType)) {
                        List<?> loadedList = loadList(objectKey, redisKey, field);
                        field.set(instance, loadedList);
                    }
                    // skip if field value is empty and not list
                    else if (fieldValueStr == null || fieldValueStr.isEmpty()) {
                        continue;
                    }
                    // handle nested objects
                    else if (fieldType.isAnnotationPresent(PersistableObject.class)) {
                        Object nestedObject = loadNested(fieldType, fieldValueStr);
                        field.set(instance, nestedObject);
                    }
                    // handle primitive types and strings
                    else {
                        Object convertedValue = convertFromString(fieldValueStr, fieldType);
                        field.set(instance, convertedValue);
                    }
                }
            }

            // set id field
            idField.set(instance, idValue);

            return instance;
        } catch (Exception e) {
            System.err.println("Error loading object: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private boolean isLazyLoaded(Class<?> clazz, String fieldName) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(LazyLoad.class)) {
                LazyLoad annotation = method.getAnnotation(LazyLoad.class);
                if (annotation.field().equals(fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<?> loadList(String parentKey, String fieldName, Field field) {
        String listValueStr = jedis.hget(parentKey, fieldName);
        if (listValueStr == null || listValueStr.isEmpty()) {
            return new ArrayList<>();
        }

        String[] itemIds = listValueStr.split(",");
        List<Object> list = new ArrayList<>();

        // get generic type of List
        ParameterizedType listType = (ParameterizedType) field.getGenericType();
        Class<?> itemType = (Class<?>) listType.getActualTypeArguments()[0];

        for (String itemId : itemIds) {
            if (itemId == null || itemId.isEmpty()) {
                continue;
            }

            // if the item type is a @PersistableObject, load it
            if (itemType.isAnnotationPresent(PersistableObject.class)) {
                try {
                    Object item = itemType.getDeclaredConstructor().newInstance();
                    Field idField = findIdField(itemType);
                    if (idField != null) {
                        idField.setAccessible(true);
                        // try to parse as id field type
                        Object idValue = convertFromString(itemId, idField.getType());
                        idField.set(item, idValue);
                        Object loadedItem = load(item);
                        if (loadedItem != null) {
                            list.add(loadedItem);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                // for primitive types, convert directly
                Object convertedValue = convertFromString(itemId, itemType);
                list.add(convertedValue);
            }
        }

        return list;
    }

    private Object loadNested(Class<?> nestedType, String nestedId) {
        try {
            Object nestedInstance = nestedType.getDeclaredConstructor().newInstance();
            Field idField = findIdField(nestedType);
            if (idField != null) {
                idField.setAccessible(true);
                // try to parse as id field type
                Object idValue = convertFromString(nestedId, idField.getType());
                idField.set(nestedInstance, idValue);
                return load(nestedInstance);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Field findIdField(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Id.class)) {
                return field;
            }
        }
        return null;
    }

    private String convertToString(Object value) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof Date) {
            return dateFormat.format((Date) value);
        }
        
        return value.toString();
    }

    private Object convertFromString(String str, Class<?> targetType) {
        if (str == null || str.isEmpty()) {
            return null;
        }

        if (targetType == String.class) {
            return str;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(str);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(str);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(str);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(str);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(str);
        } else if (targetType == Date.class) {
            try {
                return dateFormat.parse(str);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        return str;
    }

    private String mapFieldNameToRedis(String javaFieldName) {
        // handle special mappings
        if ("authorName".equals(javaFieldName)) {
            return "Author Name";
        }
        // handle camel case to title case mapping for date
        if ("date".equalsIgnoreCase(javaFieldName)) {
            return "Date";
        }
        if ("description".equalsIgnoreCase(javaFieldName)) {
            return "Description";
        }
        // add other mappings as needed
        return javaFieldName;
    }

    private String mapRedisToFieldName(String redisFieldName) {
        if ("Author Name".equals(redisFieldName)) {
            return "authorName";
        }
        if ("Date".equals(redisFieldName)) {
            return "date";
        }
        return redisFieldName;
    }

    public java.util.Set<String> listKeys(String pattern) {
        if (jedis == null) {
            return new java.util.HashSet<>();
        }
        return jedis.keys(pattern);
    }

    public boolean deleteKey(String key) {
        if (jedis == null || key == null) {
            return false;
        }
        return jedis.del(key) > 0;
    }

    public void close() {
        if (jedis != null) {
            jedis.close();
        }
    }
}

