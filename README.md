# Prom-client-IBMi
Prometheus Client for IBM i

# Installation and Startup (IBM i)

1. Download the latest `prom-client-ibmi.jar` file from
[the releases page](https://github.com/ThePrez/Prom-client-IBMi/releases).
Place this file in IFS somewhere. 
1. From a command line, `cd` to the directory where you placed
`prom-client-ibmi.jar` and run:
```bash
java -jar prom-client-ibmi.jar
```
Create a default configuration file by responding `y` to the
following prompt:
```bash
Configuration file config.json not found. Would you like to initialize one with defaults? [y] 
```
You Should see a series of messages about collectors being registered. If you see the
following message, the client is running successfully:
```
==============================================================
Successfully started Prometheus client on port 8910
==============================================================
```

If you would like to run the program in the background so that you can exit
your shell and keep the Prometheus client running, you can use the `nohup` utility:
```bash
nohup java -jar prom-client-ibmi.jar > prom-client.log 2>&1
```


# Running on a different port

The Prometheus client port can be customized in several ways. The port
is determined by the following, in order of precedence:
- The `PORT` environment variable
- The `promclient.port` Java system property
- The `port` value of the JSON configuration file
- The default value of 8910

# Prometheus Configuration

To configure Prometheus, just add a target to the `scrape_configs` as done
in the following sample configuration:

```yaml
scrape_configs:
  - job_name: 'prometheusibmi'
    static_configs:
    - targets: ['1.2.3.4:8910']
```

# JSON Configuration

See [config.json](./config.json) for an example JSON file, which contains the
following:
```json
{
  "port": 8910,
  "queries": [
    {
      "name": "System Statistics",
      "interval": 60,
      "sql": "SELECT * FROM TABLE(QSYS2.SYSTEM_STATUS(RESET_STATISTICS=>'YES',DETAILED_INFO=>'ALL')) X"
    },
    {
      "name": "number of remote connections",
      "interval": 60,
      "sql": "select COUNT(REMOTE_ADDRESS) as REMOTE_CONNECTIONS from qsys2.netstat_info where TCP_STATE = 'ESTABLISHED' AND REMOTE_ADDRESS != '::1' AND REMOTE_ADDRESS != '127.0.0.1'"
    }
  ],
  "username": "myusername",
  "password": "mypassword",
  "hostname": "mysystem"
}
```

Notes about the JSON configuration file:
- The program will create the default version for you if you don't create one yourself
- The location of the configuration file can be customized by the `promclient.config` Java system property
- The default configuration gathers metrics with just two queries. You can customize your metrics collection with any SQL query you'd like to monitor with prometheus.
- For each query, the `interval` value represents the interval between data collection attempts
for that query, in seconds.

**IMPORTANT NOTES ABOUT COLLECTED METRICS**
- Only numeric values will be collected
- The values will be reported to Prometheus in the format
```
hostname__column
```
(where `hostname` is the IBM i self-resolved hostname and `column` is the SQL column)
- You can tailor the metric name in prometheus by changing the column name via the SQL query (using the SELECT `AS XXXX` syntax)

# Managing with Service Commander

(documentation forthcoming)


# Installation and Startup (off IBM i)

(documentation forthcoming)

# Metrics gathered with default config

- TOTAL_JOBS_IN_SYSTEM
- MAXIMUM_JOBS_IN_SYSTEM
- ACTIVE_JOBS_IN_SYSTEM
- INTERACTIVE_JOBS_IN_SYSTEM
- ELAPSED_TIME
- ELAPSED_CPU_USED
- ELAPSED_CPU_SHARED
- ELAPSED_CPU_UNCAPPED_CAPACITY
- CONFIGURED_CPUS
- CURRENT_CPU_CAPACITY
- AVERAGE_CPU_RATE
- AVERAGE_CPU_UTILIZATION
- MINIMUM_CPU_UTILIZATION
- MAXIMUM_CPU_UTILIZATION
- SQL_CPU_UTILIZATION
- MAIN_STORAGE_SIZE
- SYSTEM_ASP_STORAGE
- TOTAL_AUXILIARY_STORAGE
- SYSTEM_ASP_USED
- CURRENT_TEMPORARY_STORAGE
- MAXIMUM_TEMPORARY_STORAGE_USED
- PERMANENT_ADDRESS_RATE
- TEMPORARY_ADDRESS_RATE
- TEMPORARY_256MB_SEGMENTS
- TEMPORARY_4GB_SEGMENTS
- PERMANENT_256MB_SEGMENTS
- PERMANENT_4GB_SEGMENTS
- TEMPORARY_JOB_STRUCTURES_AVAILABLE
- PERMANENT_JOB_STRUCTURES_AVAILABLE
- TOTAL_JOB_TABLE_ENTRIES
- AVAILABLE_JOB_TABLE_ENTRIES
- IN_USE_JOB_TABLE_ENTRIES
- ACTIVE_JOB_TABLE_ENTRIES
- JOBQ_JOB_TABLE_ENTRIES
- OUTQ_JOB_TABLE_ENTRIES
- JOBLOG_PENDING_JOB_TABLE_ENTRIES
- PARTITION_ID
- NUMBER_OF_PARTITIONS
- ACTIVE_THREADS_IN_SYSTEM
- PARTITION_GROUP_ID
- SHARED_PROCESSOR_POOL_ID
- DEFINED_MEMORY
- MINIMUM_MEMORY
- MAXIMUM_MEMORY
- MEMORY_INCREMENT
- PHYSICAL_PROCESSORS
- PHYSICAL_PROCESSORS_SHARED_POOL
- MAXIMUM_PHYSICAL_PROCESSORS
- DEFINED_VIRTUAL_PROCESSORS
- VIRTUAL_PROCESSORS
- MINIMUM_VIRTUAL_PROCESSORS
- MAXIMUM_VIRTUAL_PROCESSORS
- DEFINED_PROCESSING_CAPACITY
- PROCESSING_CAPACITY
- UNALLOCATED_PROCESSING_CAPACITY
- MINIMUM_REQUIRED_PROCESSING_CAPACITY
- MAXIMUM_LICENSED_PROCESSING_CAPACITY
- MINIMUM_PROCESSING_CAPACITY
- MAXIMUM_PROCESSING_CAPACITY
- PROCESSING_CAPACITY_INCREMENT
- DEFINED_INTERACTIVE_CAPACITY
- INTERACTIVE_CAPACITY
- INTERACTIVE_THRESHOLD
- UNALLOCATED_INTERACTIVE_CAPACITY
- MINIMUM_INTERACTIVE_CAPACITY
- MAXIMUM_INTERACTIVE_CAPACITY
- DEFINED_VARIABLE_CAPACITY_WEIGHT
- VARIABLE_CAPACITY_WEIGHT
- UNALLOCATED_VARIABLE_CAPACITY_WEIGHT
- THREADS_PER_PROCESSOR
- DISPATCH_LATENCY
- DISPATCH_WHEEL_ROTATION_TIME
- TOTAL_CPU_TIME
- INTERACTIVE_CPU_TIME
- INTERACTIVE_CPU_TIME_ABOVE_THRESHOLD
- UNUSED_CPU_TIME_SHARED_POOL
- JOURNAL_RECOVERY_COUNT
- JOURNAL_CACHE_WAIT_TIME
- REMOTE_CONNECTIONS
