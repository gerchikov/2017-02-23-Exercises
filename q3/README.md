#Question Three

##Problem Statement

> Write a one-page design doc describing a service that keeps track of Foursquare users as they check in at different venues. In particular, this service will get informed of each check-in in real time (a user/venue pair) and must be able to answer the following queries in real time:
>
> 1. Where is user A right now?
> 2. What users are at venue X right now?
>
> The following business rules apply:
> 
> 1. A user can only be at one venue at a time. If user A checks in at venue X and then at venue Y, they are no longer at venue X.
> 2. A check-in only “lasts” for at most 3 hours. If user A checks in at venue X and then does nothing for 3 hours, they are no longer at venue X.

##Proposed Solution 

Implement service as a two-tier application consisting of the **Service** (combined web and application) tier and the **Persistence** tier.

###Assumptions and simplifications

1. Only real-time requests need to be supported. No historical data or queries! (Although both can be added with relatively straight-forward modifications.)
2. Authentication & Authorization are outside the scope of this document, although can be trivially added, especially if directly supported by the framework.
3. Check-in info only -- no additional information is stored or provided about users or venues.
4. Initial sizing for 1 billion users (~100 GB data size), 100 thousand check-ins per second (one per user every three hours), 100 thousand queries per second, of each kind.
5. Eventual consistency may or may not be tolerated. _Certainly a discussion point!_ Some ideas below.

###Service Tier

#####Service Tier Option 1 - Traditional

Stateless & Idempotent. Implement e.g. in Java using [Spring Framework](https://projects.spring.io/spring-framework/), perhaps with Spring Boot for rapid prototyping.

Implement the following (REST, e.g.) endpoints:

 1. `POST /service/api/checkin` JSON (alternative: XML) body `{user: UUID, venue: UUID}` returning `200` HTTP code and `{user: UUID, venue: UUID, timestamp: TMS}` on success or appropriate HTTP error code otherwise. Note that no check-in time is specified in the request -- check-ins are real-time only.
 2. `GET /service/api/user/<UUID>/venue` returning `200` and body `{user: UUID, venue: UUID, timestamp: TMS}` if a venue can be determined, `404` (or `410`) if it can't, or appropriate HTTP error code otherwise.
 3. `GET /service/api/venue/<UUID>/users` returning `200` and body `{venue: UUID, users: [{user: UUID, timestamp: TMS}, ...]}` if a venue can be determined, `404` (or `410`) if it can't, or appropriate HTTP error code otherwise.

Service tier can be replicated (for high availability/fault tolerance) or horizontally scaled (for performance) by adding or removing arbitrary number of nodes, potentially dynamically in response to workload. A (distributed) load balancer is then needed in front of the service. No single point of failure but client may need to retry if a node fails while serving requests.

#####Service Tier Option 2 - Managed

[Amazon API Gateway](https://aws.amazon.com/api-gateway/) + [AWS Lambda](https://aws.amazon.com/lambda/) + Persistence (either option below).  
~Infinite scalability and availability plus management, analytics and monitoring -- all for one low, low price...

###Persistence Tier

#####Persistence Tier Option 1 - Relational ([MySQL](http://mysql.com) or [Amazon RDS](https://aws.amazon.com/rds/)<sup id="a1">[[1]](#f1)</sup>)

```$SQL
CREATE TABLE check_ins (
  user_id BINARY(16) NOT NULL PRIMARY KEY,  -- may want to prefix with autoincrement for access locality
  venue_id BINARY(16) NOT NULL,
  checkin_time TIMESTAMP NOT NULL,
  INDEX (venue_id)  -- and, optionally, checkin_time as well, to serve query (3) entirely from index
);
```

The following SQL statements support the above API endpoints (1-3), respectively:
  
```$SQL
INSERT INTO check_ins (user_id, venue_id, checkin_time) VALUES (:u, :v, :now)
    ON DUPLICATE KEY UPDATE venue = :v, checkin_time = :now;
```

```$SQL
SELECT * FROM check_ins
 WHERE user_id = :u
   AND checkin_time >= TIMESTAMPADD(HOUR, -3 , NOW());
```

```$SQL
SELECT * FROM check_ins
 WHERE venue_id = :v
   AND checkin_time >= TIMESTAMPADD(HOUR, -3 , NOW());
```

######Discussion points:

* **Time/space tradeoffs** — the secondary index on `venue_id` may be viewed as pre-calculating or caching data to save time later when query (3) is executed. Said index effectively is a precomputed and automatically maintained list of venues with a list, for every venue, of all the users who checked in there -- so that all that query (3) has to do is filter users by their check-in time to exclude the expired check-ins.
* **Storage** — the volume of the data is well within what a single-node relational DBMS can manage, but the query throughput is not. Therefore... 
* **Scalability / Availability** — A reasonable server with SSD storage and a well-tuned DB can support on the order of 10,000 simple queries per second. We need at least 30x that. Sharding and/or replication are the solutions.  
**Sharding** is complicated by the fact that the two queries that we need to support are "orthogonal" in the sense of dimensions along which a dataset could be partitioned. Solve this by keeping _two_ full copies of the dataset, each sharded along a different dimension. Specifically, deploy 200 identically structured databases on 200 nodes that we will call U00, U01, ..., U99; V00, V01, ..., V99. Apply each incoming INSERT to _two_ nodes, one in the U group and one in the V group, determined by hashing `user_id` and `venue_id`, respectively. (Make the two inserts atomic with two-phase commit or a similar distributed transaction coordinator if strong consistency is required! Otherwise, a reliable but asynchronous mechanism like a message queue can be used.) Send type (2) queries to the node in the U group determined by hashing `user_id`. Send type (3) queries to the node in the V group determined by hashing `venue_id`. (Handle request routing in the service tier, which is itself scalable as described above.) The two groups do not have to be the same size. If one type of queries ((2) or (3) above) dominates the workload, then the corresponding group can be larger, e.g. 32 U groups and 64 V groups, etc. Measure and monitor query performance and adjust the number of nodes in each group accordingly (requires repartitioning, which may be slightly tricky (but doable) if the system must remain available).  
Additional benefit of this approach is the built-in redundancy: we can recover from a failure of any number of nodes in one group by rebuilding failed nodes with the data from the other group -- albeit slowly, as it will require querying all 100 nodes in the surviving group. Failure of a single node would make queries for the corresponding user or venue temporarily unavailable until the recovery from the surviving group is complete. Inserts could continue (into one or both groups, as soon as the replacement is deployed but possibly before it's fully rebuilt) provided that they don't interfere with recovery.  
**Replication** can be employed if still higher availability/fault tolerance is desired. Namely, replicate each of the shards in both groups three ways with Galera Cluster multi-master synchronous replication, MySQL native group replication or master-slave replication -- each offering different trade-offs between read and write performance, consistency model, system complexity, recovery time and load balancing possibilities. With any kind of replication in place, most multi-node failures won't affect availability or even performance, and the recovery can be transparent and fully automatic. If only one type of queries ((2) or (3)) requires higher availability, then only the corresponding group may require replication.

#####Persistence Tier Option 2 - NoSQL ([Amazon DynamoDB](https://aws.amazon.com/dynamodb/))

Table: {user_id, venue_id, checkin_time}; partition key: user_id; range key: checkin_time.  
Global Secondary Index: partition key: venue_id; range key: checkin_time.

API endpoints are supported by (1) "Upsert" semantics (similar to the INSERT above); (2) table get; and (3) GSI get, respectively.

######Discussion points:

* **Time/space tradeoffs** — global secondary index on `venue_id` is a precomputed cache of venues and their users, similar to the relational index above. Note that secondary indices in DynamoDB are eventually-consistent -- no strong consistency can be achieved with this approach.
* **Storage and Scalability** are practically unlimited. ~Any desired throughput can be dialed in by provisioning sufficient amount of Read and Write Capacity Units (basically IOPS -- input-output operations per second). Plus management, analytics and monitoring -- all for one low, low price...
* **Availability** is in AWS hands and governed by SLA. There have been serious and far-reaching [outages](https://aws.amazon.com/message/5467D2/)!
* **Cost** is certainly a factor, but may be offset by reduced development and operating efforts. DynamoDB hides a lot of complexity of the RDBMS-based solution above! TCO analysis is not trivial.

---

<b id="f1">[1]</b> Amazon RDS does not support multi-master synchronous replication. [↩](#a1)