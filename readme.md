# SpringBoot Distributed Lock With JDBC

## Setup for Postgresql 

```postgresql
CREATE TABLE INT_LOCK  (
    LOCK_KEY CHAR(36) NOT NULL,
    REGION VARCHAR(100) NOT NULL,
    CLIENT_ID CHAR(36),
    CREATED_DATE TIMESTAMP NOT NULL,
    constraint INT_LOCK_PK primary key (LOCK_KEY, REGION)
);
```

## Testing Jdbc Lock

```shell
curl http://localhost:8099/lock/jdbc?name=anil1
#[Distributed Lock] 16:39:46.596  INFO --- com.github.senocak.skdl.LockService      : [1]Amount is incremented for anil1
#10 seconds later...
#[Distributed Lock] 16:41:32.744  INFO --- com.github.senocak.skdl.LockController   : Waited 10sec

curl http://localhost:8099/lock/jdbc?name=anil1
#5 seconds later...
#[Distributed Lock] 16:39:47.647 ERROR --- com.github.senocak.skdl.LockController   : Lock is not available
```

## Testing Redis Lock

```shell
curl http://localhost:8099/lock/redis?name=anil1
#[Distributed Lock] 16:40:44.115  INFO --- com.github.senocak.skdl.LockService      : [2]Amount is incremented for anil1
#10 seconds later...
#[Distributed Lock] 16:41:32.744  INFO --- com.github.senocak.skdl.LockController   : Waited 10sec

curl http://localhost:8099/lock/redis?name=anil1
#[Distributed Lock] 16:40:44.859 ERROR --- com.github.senocak.skdl.LockController   : Lock is not available
```