# Table of Contents

- [Background](#background)
- [#310 (Broken Batch logic with Oracle)](#310-broken-batch-logic-with-oracle)
- [#309 (Broken type handling logic with Oracle)](#309-broken-type-handling-logic-with-oracle)
- [Running the project:](#running-the-project)
  - [Prerequisites](#prerequisites)
  - [Oracle Database](#oracle-database)

# Background

This project is used to demonstrate the following bugs:
- [Vert.x - #310 - Broken Batch logic with Oracle](https://github.com/vert-x3/vertx-jdbc-client/issues/310)
- [Vert.x - #309 - Broken type handling logic with Oracle](https://github.com/vert-x3/vertx-jdbc-client/issues/309)

---

# #310 (Broken Batch logic with Oracle)

It seems that the client assumes all vendors support [DLM returning clause](https://docs.oracle.com/database/121/LNPLS/returninginto_clause.htm#LNPLS01354) on batching. However, this is [not the cause with Oracle](https://stackoverflow.com/a/50130653).

After trying to find a simple way to disable the DLM returning with no success, we settled with a transacted insert loop. You can see a similar issue for another framework here: [Micronaut #991 - Oracle - Batch w/ DML Returning](https://github.com/micronaut-projects/micronaut-data/issues/911)

You can see the code that is used to demonstrate the issue here:
- [io.github.u.ways.bugs.BatchHandlingScenariosTest](https://github.com/u-ways/broken-4.3.5-vertx-jdbc-client-oracle/blob/main/src/test/kotlin/io/github/u/ways/bugs/BatchHandlingScenariosTest.kt)

---

# #309 (Broken type handling logic with Oracle)

It seems that 4.3.5 has introduced broken logic when it comes to certain type handling (e.g. UUID, TIMESTAMP) with Oracle 
Database with the JDBC client.

You can see the code that is used to demonstrate the issue here:
- [io.github.u.ways.DataTypesHandlingScenariosTest](https://github.com/u-ways/broken-4.3.5-vertx-jdbc-client-oracle/blob/main/src/test/kotlin/io/github/u/ways/bugs/DataTypesHandlingScenariosTest.kt)

## Analysis for UUIDs

As other users has reported in [vertx-jdbc-client/issues/295](https://github.com/vert-x3/vertx-jdbc-client/issues/295), 
this is caused by the `JDBCPreparedQuery.fillStatement` method that has been changed to use the
`preparedStatement.getParameterMetaData()` to work out parameter types:

![screenshot - fillStatment](./doc/screenshot%20-%20fill%20statment.png)

The metadata is used to obtain the `JDBCPropertyAccessor.jdbcType` and for a String parameter with Oracle, the jdbcType 
is equated to `JdbcType.OTHER`:

![screenshot - column descriptor](./doc/screenshot%20-%20column%20descriptor.png)

When the encode method is called on the vertx `JDBCEncoderImpl` class, there is special check for whether Strings that
match a UUID format should be treated as UUIDs:

![screenshot - encode data](./doc/screenshot%20-%20encode%20data.png)

Part of the check involves calling the `JDBCTypeWrapper.isAbleASUUID()` method. As the JDBCType is OTHER, this method 
call returns true and causes a UUID object to be instantiated from the string and used as a parameter in the prepared 
statement. This UUID instance then causes the exception when it is set as an object on the underlying prepared statement.

**Potential Fix**: Remove `JdbcType.OTHER` check as a valid option in the `JDBCTypeWrapper.isAbleASUUID()` method.

---

# Running the project:

The project contains testing code that will demonstrate the issue. Those tests can be found here: 
- [io.github.u.ways.DataTypesHandlingScenariosTest](https://github.com/u-ways/broken-4.3.5-vertx-jdbc-client-oracle/blob/main/src/test/kotlin/io/github/u/ways/DataTypesHandlingScenariosTest.kt)

Tests that fail with 4.3.5, will have the comment: `// FIXME: FAILS WITH 4.3.5`

## Prerequisites
- Java 17
- Docker

## Oracle Database

We provide a docker-compose file that will start an Oracle Database 19c instance. The database will be available on port
`1521` with the username/password of `system/Oracl3!!`. The database will be created with the `ORCLCDB1` SID.

However, it's important to note that you will need to accept the Oracle Database license agreement before you can pull
the image. To do this:

1. Go to https://container-registry.oracle.com
2. Login with your Oracle SSO credentials.
3. Go to database enterprise edition details.
4. Accept the terms and conditions for the image.
5. Run `docker login container-registry.oracle.com`
6. Use your Oracle SSO credentials.

Then start the database service by running:

```shell
docker-compose compose -f ./docker-compose.yml up -d
```

___
