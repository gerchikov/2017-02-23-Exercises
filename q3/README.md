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

Implement service as a two-tier application consisting of a combined **Service** (web + app) tier and a **Persistence** tier.

###Assumptions and simplifications

1. Only real-time requests need to be supported. No historical data or queries.
2. Authentication & Authorization are outside of the scope of this document, although can be trivially added if supported by the framework.
3. Check-in info only: no additional information if stored or provided about users and venues.
4. Initial sizing for 1 billion users (~100 GB data size), 100 thousand check-ins/sec (one per user per three hours), 100 thousand queries/sec.
5. Eventual consistency can be tolerated -- e.g. a query soon after update may not reflect it. _Certainly a discussion point!_

###Service Tier

#####Service Tier Option 1 - Traditional

Stateless & Idempotent. Implement e.g. in Java using [Spring Framework](https://projects.spring.io/spring-framework/), perhaps with Spring Boot for rapid prototyping.

Implement the following (REST, e.g.) endpoints:

 1. `POST /service/api/checkin` JSON (alternative: XML) body `{user: UUID, venue: UUID}` returning `200` HTTP code and `{user: UUID, venue: UUID, timestamp: TMS}` on success or appropriate HTTP error code otherwise. Note that no check-in time is specified in the request -- check-ins are real-time only.
 2. `GET /service/api/user/<UUID>/venue` returning `200` and body `{user: UUID, venue: UUID, timestamp: TMS}` if a venue can be determined, `404` (or `410`) if it can't, or appropriate HTTP error code otherwise.
 3. `GET /service/api/venue/<UUID>/users` returning `200` and body `{venue: UUID, users: [{user: UUID, timestamp: TMS}, ...]}` if a venue can be determined, `404` (or `410`) if it can't, or appropriate HTTP error code otherwise.

Service tier can be replicated (for high availability/fault tolerance) or scaled up or down horizontally (for performance) by adding or removing arbitrary number of nodes, potentially dynamically in response to workload. A (distributed) load balancer is then needed in front of the service. No single point of failure but client may need to retry if a node fails while serving requests.

#####Service Tier Option 2 - Managed

[Amazon API Gateway](https://aws.amazon.com/api-gateway/), [AWS Lambda](https://aws.amazon.com/lambda/), Persistence (either option). Get ~infinite scalability and availability plus management, analytics and monitoring, for the price.

###Persistence Tier

#####Persistence Tier Option 1 - Relational (MySQL)

```$SQL
CREATE TABLE check_ins (
  user_id BINARY(16) NOT NULL PRIMARY KEY,  -- may want to prefix with autoincrement for access locality
  venue_id BINARY(16) NOT NULL,
  checkin_time TIMESTAMP NOT NULL,
  INDEX (venue_id)  -- and, optionally, checkin_time as well, to serve query (3) entirely from index
);
```

The following SQL supports the above REST endpoints, respectively:
  
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

Discussion points:

* Time/space tradeoffs — the secondary index on venue_id may be viewed as pre-calculating or caching data to save time later when query (3) is executed
* Storage — capacity, sharding ...
* Scalability / Availability — If you are going to have multiple servers in your service, describe how you will distribute work between them. What would happen if you suddenly had 100x more requests? What would occur if one or more servers failed (in whatever way they could fail)?
...Chance for strong consistency!

#####Persistence Tier Option 2 - NoSQL ([Amazon DynamoDB](https://aws.amazon.com/dynamodb/))

Table: {user_id, venue_id, checkin_time}; partition key: user_id; range key: checkin_time.  
Global Secondary Index: partition key: venue_id; range key: checkin_time.

Endpoints are supported by (1) "Upsert" semantics (similar to the above); (2) table get; and (3) SGI get, respectively.
...Discuss!
