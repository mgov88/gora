/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gora.benchmark;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import org.apache.gora.query.Query;
import org.apache.gora.query.Result;
import org.apache.gora.store.DataStore;
import org.apache.gora.store.DataStoreFactory;
import org.apache.gora.util.GoraException;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.client.Log;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;
import com.yahoo.ycsb.Status;
import com.yahoo.ycsb.StringByteIterator;
import com.yahoo.ycsb.workloads.CoreWorkload;
import org.apache.gora.benchmark.generated.User;

/**
 * The Class GoraBenchmarkClient
 *
 * @author sc306 This class extends the Yahoo! Cloud Service Benchmark benchmark
 *         {@link #com.yahoo.ycsb.DB DB} class to provide functionality for
 *         {@link #insert(String, String, HashMap) insert},
 *         {@link #read(String, String, Set, HashMap) read},
 *         {@link #scan(String, String, int, Set, Vector) scan} and
 *         {@link #update(String, String, HashMap) update} methods as per Apache
 *         Gora implementation.
 */
public class GoraBenchmarkClient extends DB {
  private static final Logger LOG = LoggerFactory.getLogger(GoraBenchmarkClient.class);
  private static final String FIELDS[] = User._ALL_FIELDS;
  private static volatile boolean executed;
  private static int totalFieldCount;
  /** This is only for set to array conversion in {@link read()} method */
  private String[] DUMMY_ARRAY = new String[0];
  private static DataStore<String, User> dataStore;
  private User user = new User();
  private Properties prop;

  public GoraBenchmarkClient() {
  }

  /**
   * * Initialisation method. This method is called once for each database
   * instance. There is one database instance for each client thread.
   *
   * @throws DBException
   *           the DB exception
   */
  public void init() throws DBException {
    try {
      synchronized (GoraBenchmarkClient.class) {
        prop = getProperties();
        totalFieldCount = Integer
            .parseInt(prop.getProperty(CoreWorkload.FIELD_COUNT_PROPERTY, CoreWorkload.FIELD_COUNT_PROPERTY_DEFAULT));
        Properties properties = DataStoreFactory.createProps();
        if (!executed) {
          executed = true;
          GoraBenchmarkUtils.generateAvroSchema(totalFieldCount);
          String dataStoreName = GoraBenchmarkUtils.getDataStore(properties);
          GoraBenchmarkUtils.generateMappingFile(dataStoreName);
          GoraBenchmarkUtils.generateDataBeans();
        }
        String keyClass = prop.getProperty("key.class", "java.lang.String");
        String persistentClass = prop.getProperty("persistent.class", "org.apache.gora.benchmark.generated.User");
        dataStore = DataStoreFactory.getDataStore(keyClass, persistentClass, properties, new Configuration());
      }
    } catch (GoraException e) {
      LOG.info("There is a problem in initialising the DataStore \n {}", e.getMessage(), e);
    }
  }

  /**
   * Cleanup any state for this DB.
   * 
   * It is very important to close the datastore properly, otherwise some data
   * loss might occur.
   *
   * @throws DBException
   *           the DB exception
   */
  public void cleanup() throws DBException {
    if (dataStore != null)
      dataStore.close();
  }

  /**
   * Delete a record from the database.
   *
   * @param table
   *          The name of the table to delete the data from
   * @param key
   *          The key of the record to delete.
   * @return Status of the operation failed or success.
   */
  @Override
  public Status delete(String table, String key) {
    try {
      dataStore.delete(key);
    } catch (Exception e) {
      LOG.info("There is a problem deleting record\n {}", e.getMessage(), e);
      return Status.ERROR;
    }
    return Status.OK;
  }

  /**
   * Insert a record in the selected Gora database. Any field/value pairs in the
   * specified values HashMap will be written into the record with the specified
   * record key.
   *
   * @param table
   *          The name of the table"field"+i
   * @param key
   *          The record key of the record to insert.
   * @param values
   *          A HashMap of field/value pairs to insert in the record. Each
   *          HashMap will have a
   * @return The result of the operation.
   */
  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
    try {
      user.setUserId(key);
      for (int fieldCount = 0; fieldCount < totalFieldCount; fieldCount++) {
        String field = FIELDS[fieldCount + 1];
        int fieldIndex = fieldCount + 1;
        String fieldValue = values.get(field).toString();
        user.put(fieldIndex, fieldValue);
        user.setDirty(fieldIndex);
      }
      dataStore.put(key, user);
    } catch (Exception e) {
      LOG.info("There is a problem inserting data \n {}", e.getMessage(), e);
      return Status.ERROR;
    }
    return Status.OK;
  }

  /**
   * Read a record from the database. Each field/value pair from the result will
   * be stored in a HashMap.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to read.
   * @param fields
   *          The list of fields to read, or null for all of them
   * @param result
   *          A HashMap oftestInsert field/value pairs for the result
   * @return The result of the operation.
   */
  @Override
  public Status read(String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
    try {
      // Check for null is necessary.
      User user = (fields == null || fields.size() == 0) ? dataStore.get(key)
          : dataStore.get(key, fields.toArray(DUMMY_ARRAY));
      for (int fieldCount = 0; fieldCount < totalFieldCount; fieldCount++) {
        String field = FIELDS[fieldCount + 1];
        int fieldIndex = fieldCount + 1;
        String value = user.get(fieldIndex).toString();
        result.put(field, new StringByteIterator(value));
      }
    } catch (Exception e) {
      LOG.info("There is a problem in reading data from the table \n {}", e.getMessage(), e);
      return Status.ERROR;
    }
    return Status.OK;
  }

  /**
   * Perform a range scan for a set of records in the database. Each field/value
   * pair from the result will be stored in a HashMap.
   *
   * @param table
   *          The name of the table
   * @param startkey
   *          The record key of the first record to read.
   * @param recordCount
   *          The number of records to read
   * @param fields
   *          The list of fields to read, or null for all of them
   * @param result
   *          A Vector of HashMaps, where each HashMap is a set field/value
   *          pairs for one record
   * @return The result of the operation.
   */
  @Override
  public Status scan(String table, String startKey, int recordCount, Set<String> fields,
      Vector<HashMap<String, ByteIterator>> result) {
    try {
      Query<String, User> goraQuery = dataStore.newQuery();
      goraQuery.setStartKey(startKey);
      goraQuery.setLimit(recordCount);
      Result<String, User> resultSet = goraQuery.execute();
      while (resultSet.next()) {
        HashMap<String, ByteIterator> resultsMap = new HashMap<>();
        for (int fieldCount = 0; fieldCount < totalFieldCount; fieldCount++) {
          String field = FIELDS[fieldCount + 1];
          int fieldIndex = fieldCount + 1;
          String value = resultSet.get().get(fieldIndex).toString();
          resultsMap.put(field, new StringByteIterator(value));
        }
        result.add(resultsMap);
      }
    } catch (Exception e) {
      LOG.info("There is a problem in scanning data from the table \n {}", e.getMessage(), e);
      return Status.ERROR;
    }
    return Status.OK;
  }

  /**
   * Update a record in the database. Any field/value pairs in the specified
   * values HashMap will be written into the record with the specified record
   * key, overwriting any existing values with the same field name.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to write.
   * @param values
   *          A HashMap of field/value pairs to update in the record
   * @return The result of the operation.
   */
  @Override
  public Status update(String table, String key, Map<String, ByteIterator> values) {
    try {
      // We first get the database object to update
      User user = dataStore.get(key);
      List<String> allFields = Arrays.asList(FIELDS);
      for (String field : values.keySet()) {
        if (GoraBenchmarkUtils.isFieldUpdatable(field, values)) {
          // Get the index of the field from the global fields array and call
          // the right method
          int indexOfFieldInArray = allFields.indexOf(field);
          user.put(indexOfFieldInArray, values.get(field).toString());
          user.setDirty(indexOfFieldInArray);
        }
      }
      dataStore.put(user.getUserId().toString(), user);
    } catch (Exception e) {
      LOG.info("There is a problem updating the records \n {}", e.getMessage(), e);
      return Status.ERROR;
    }
    return Status.OK;
  }

  /**
   * Gets the data store.
   *
   * @return the data store
   */
  public DataStore<String, User> getDataStore() {
    return this.dataStore;
  }
}
