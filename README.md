pantopod
========

Distributed anonymized web crawler, based on [this blog post](http://blog.databigbang.com/distributed-scraping-with-multiple-tor-circuits/).

The name comes from [these things](https://en.wikipedia.org/wiki/Sea_spider).

Overview
--------

The components include:

* [Tor](https://www.torproject.org/) for anonymity,
* [Apache Helix](http://helix.apache.org/) to distribute crawler workers, and
* [Apache Kafka](http://kafka.apache.org/) as a messaging bus for work items

To begin crawling a site, one creates a resource in Helix with the full authority (in the URI sense) of the web page to be crawled, e.g. `www.cs.washington.edu`.

The number of replicas of the Helix resource corresponds to how many machines run a Kafka consumer. The number of partitions can be used as a multiplying factor here as well. For example, to run six consumers in total on three machines, one would set the number of replicas to three and the number of partitions to 2.

When a partition (i.e. worker) comes online, it sends a message into Kafka to crawl the base URL. The steps involved in processing a unit of work are:

* Download the raw page data
* Store the raw page data in underlying storage mechanism (e.g. file system, HDFS, database)
* If storing for the first time, extract all the links, and emit Kafka events for these

This process continues until all the links for a page have been visited. The external domains are noted during this process, and we can later create new resources for these in order to crawl them.

QuickStart
------------

First, build the pantopod code

```
mvn install
```

As we need Kafka to manage the work queue, we'll use its [quick start](http://kafka.apache.org/documentation.html#quickstart) to set up ZooKeeper and Kafka broker instances, repeated here in part for convenience:

* [Download](https://www.apache.org/dyn/closer.cgi?path=/kafka/0.8.2.0/kafka_2.10-0.8.2.0.tgz) the Kafka code
* Start the ZooKeeper server: `bin/zookeeper-server-start.sh config/zookeeper.properties`
* Start the Kafka broker: `bin/kafka-server-start.sh config/server.properties`

At this point, there should be a ZooKeeper running on `localhost:2181` and Kafka broker on `localhost:9092`. To gain more clarity into this, one can use the excellent [ZooInspector](https://github.com/zzhang5/zooinspector) tool.

Now, we set up our Helix cluster, adding one worker node, `node0`

```
java -jar pantopod-core/target/pantopod-core-1.0-SNAPSHOT.jar admin \
  --zkSvr localhost:2181 \
  --addCluster PANTOPOD

java -jar pantopod-core/target/pantopod-core-1.0-SNAPSHOT.jar admin \
  --zkSvr localhost:2181 \
  --addNode PANTOPOD node0
```

We'll run the scraper with the following configuration, existing in `/tmp/pantopod-config.yml`

```
zkConnectString: 'localhost:2181'
helixParticipantName: node0
helixClusterName: PANTOPOD
kafkaBrokerList: 'localhost:9092'
kafkaGroupId: 'g0'

# Use file-system to store page data
outputDir: '/tmp/pantopod-data'
```

Now run the scraper worker and controller

```
java -jar pantopod-core/target/pantopod-core-1.0-SNAPSHOT.jar controller \
  --zkSvr localhost:2181 \
  --cluster PANTOPOD

java -jar pantopod-core/target/pantopod-core-1.0-SNAPSHOT.jar participant \
  server /tmp/pantopod-config.yml
```

At this point, we have a single node cluster ready to start accepting some work. To scrape a website, we simply add a Helix resource with the name of the authority (e.g. `www.cs.washington.edu`), or assign partitions of that resource to different nodes.

```
java -jar pantopod-core/target/pantopod-core-1.0-SNAPSHOT.jar admin \
  --zkSvr localhost:2181 \
  --addResource PANTOPOD courses.cs.washington.edu 4 OnlineOffline
```

If we don't want to scrape the whole website, we can provide a `chroot` and `startPage` to the root domain:

```
java -jar pantopod-core/target/pantopod-core-1.0-SNAPSHOT.jar admin \
  --zkSvr localhost:2181 \
  --setConfig RESOURCE PANTOPOD,courses.cs.washington.edu chroot=/courses/cse190m/10su,startPage=index.shtml
```

Now to start the job, rebalance the resource onto the cluster:

```
java -jar pantopod-core/target/pantopod-core-1.0-SNAPSHOT.jar admin \
  --zkSvr localhost:2181 \
  --rebalance PANTOPOD courses.cs.washington.edu 1
```

This starts 4 workers on our 1 node. If we wanted 8 workers on 2 nodes, we could change the argument to `--rebalance` from 1 to 2.

### Tor

First, make sure you have `tor` installed on your machine and on your path. On OS X, you can do

```
brew install tor
```

To tunnel traffic through a tor circuit, add the following to configuration:

```
useTor: true
```

You may also need to change the `torExecutable` config to the location of the `tor` program on your machine.

If the website still rate-limits even through a tor circuit, you can configure to change the circuit periodically (via sending SIGHUP to the tor process), for example:

```
watchdogDelayMillis: 60000 # use a new circuit every 60s
```

If you are running multiple workers on the same machine, be sure to set the `socksPort` and `controlPort` config parameters to non-conflicting values.

See `com.github.brandtg.pantopod.PantopodConfiguration` for more configuration options.

### Database

If it is more convenient to write to a database, one can remove the `outputDir` configuration and add the following (e.g. to write to a MySQL instance):

```
handlerType: database
database:
  driverClass: 'com.mysql.jdbc.Driver'
  user: 'msandbox'
  password: 'msandbox'
  url: 'jdbc:mysql://localhost:5000/pantopod'
  properties:
    charSet: UTF-8
  maxWaitForConnection: 1s
  validationQuery: "/* Health Check */ SELECT 1"
  validationQueryTimeout: 3s
  minSize: 8
  maxSize: 32
  checkConnectionWhileIdle: false
  evictionInterval: 10s
  minIdleTime: 1 minute
```

Data is stored as `LONGBLOB`, but if you're using MySQL, make sure to set `max_allowed_packet=500M` or something along those lines so the server doesn't complain about big web resources. If Postgres is being used, [TOAST](http://www.postgresql.org/docs/8.3/static/storage-toast.html) is likely a good option.

TODOs
-----

* Support automated cross-domain crawling
