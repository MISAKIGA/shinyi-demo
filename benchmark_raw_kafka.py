#!/usr/bin/env python3
"""
Raw Kafka Producer/Consumer Benchmark
Tests 100K messages using kafka-python library
"""

import time
import os
import sys
from kafka import KafkaProducer, KafkaConsumer
from kafka.admin import KafkaAdminClient, NewTopic

BOOTSTRAP_SERVER = "localhost:9092"
TOPIC = "benchmark-raw-test"
MESSAGE_COUNT = 100000
MESSAGE_SIZE = 1024

def generate_message(size):
    """Generate random message payload"""
    return os.urandom(size).hex()[:size]

def create_topic():
    """Create topic if not exists"""
    try:
        admin = KafkaAdminClient(bootstrap_servers=BOOTSTRAP_SERVER)
        topic = NewTopic(name=TOPIC, num_partitions=1, replication_factor=1)
        admin.create_topics([topic])
        admin.close()
        print(f"[INFO] Created topic: {TOPIC}")
    except Exception as e:
        if "TopicExistsException" in str(e) or "already exists" in str(e):
            print(f"[INFO] Topic {TOPIC} already exists")
        else:
            print(f"[WARN] Could not create topic: {e}")
        try:
            admin.close()
        except:
            pass

def delete_topic():
    """Delete topic"""
    try:
        admin = KafkaAdminClient(bootstrap_servers=BOOTSTRAP_SERVER)
        admin.delete_topics([TOPIC])
        admin.close()
        print(f"[INFO] Deleted topic: {TOPIC}")
    except Exception as e:
        print(f"[WARN] Could not delete topic: {e}")

def run_producer_benchmark():
    """Run raw Kafka producer benchmark"""
    print("\n" + "="*50)
    print("RAW KAFKA PRODUCER BENCHMARK")
    print("="*50 + "\n")

    create_topic()

    print(f"[INFO] Starting producer benchmark: {MESSAGE_COUNT} messages, {MESSAGE_SIZE} bytes each")

    # Producer with optimized settings
    producer = KafkaProducer(
        bootstrap_servers=BOOTSTRAP_SERVER,
        acks=1,
        batch_size=65536,
        linger_ms=10,
        compression_type='gzip',
        buffer_memory=67108864,
        retries=3,
    )

    message = generate_message(MESSAGE_SIZE).encode('utf-8')

    start_time = time.time()

    for i in range(MESSAGE_COUNT):
        producer.send(TOPIC, value=message, key=f"key-{i}".encode('utf-8'))
        if (i + 1) % 10000 == 0:
            producer.flush()
            print(f"[PROGRESS] Produced {i + 1} messages...")

    producer.flush()
    producer.close()

    end_time = time.time()
    duration = end_time - start_time
    throughput = MESSAGE_COUNT / duration
    bandwidth = (MESSAGE_COUNT * MESSAGE_SIZE) / duration / 1024 / 1024

    print(f"\n[SUCCESS] Producer Results:")
    print(f"  Messages: {MESSAGE_COUNT}")
    print(f"  Duration: {duration:.2f}s")
    print(f"  Throughput: {throughput:.0f} msg/s")
    print(f"  Bandwidth: {bandwidth:.2f} MB/s")

    return {
        'duration': duration,
        'throughput': throughput,
        'bandwidth': bandwidth
    }

def run_consumer_benchmark():
    """Run raw Kafka consumer benchmark"""
    print("\n" + "="*50)
    print("RAW KAFKA CONSUMER BENCHMARK")
    print("="*50 + "\n")

    print(f"[INFO] Starting consumer benchmark: {MESSAGE_COUNT} messages")

    # Consumer with optimized settings
    consumer = KafkaConsumer(
        TOPIC,
        bootstrap_servers=BOOTSTRAP_SERVER,
        auto_offset_reset='earliest',
        enable_auto_commit=True,
        max_poll_records=10000,
        fetch_max_wait_ms=1000,
        fetch_min_bytes=1024,
        max_partition_fetch_bytes=10485760,
        consumer_timeout_ms=120000,
    )

    start_time = time.time()
    consumed = 0

    for message in consumer:
        consumed += 1
        if consumed % 10000 == 0:
            print(f"[PROGRESS] Consumed {consumed} messages...")
        if consumed >= MESSAGE_COUNT:
            break

    consumer.close()

    end_time = time.time()
    duration = end_time - start_time
    throughput = consumed / duration
    bandwidth = (consumed * MESSAGE_SIZE) / duration / 1024 / 1024

    print(f"\n[SUCCESS] Consumer Results:")
    print(f"  Messages: {consumed}")
    print(f"  Duration: {duration:.2f}s")
    print(f"  Throughput: {throughput:.0f} msg/s")
    print(f"  Bandwidth: {bandwidth:.2f} MB/s")

    return {
        'duration': duration,
        'throughput': throughput,
        'bandwidth': bandwidth
    }

def main():
    print("="*50)
    print("RAW KAFKA BENCHMARK (100K Messages)")
    print("="*50)

    producer_results = run_producer_benchmark()
    consumer_results = run_consumer_benchmark()

    print("\n" + "="*50)
    print("BENCHMARK COMPLETE")
    print("="*50)

    print("\n--- SUMMARY ---")
    print(f"RAW Producer: {producer_results['throughput']:.0f} msg/s, {producer_results['bandwidth']:.2f} MB/s, {producer_results['duration']:.2f}s")
    print(f"RAW Consumer: {consumer_results['throughput']:.0f} msg/s, {consumer_results['bandwidth']:.2f} MB/s, {consumer_results['duration']:.2f}s")

    # Store for later comparison
    with open('/tmp/raw_benchmark_results.txt', 'w') as f:
        f.write(f"RAW_PRODUCER_DURATION={producer_results['duration']}\n")
        f.write(f"RAW_PRODUCER_THROUGHPUT={producer_results['throughput']}\n")
        f.write(f"RAW_PRODUCER_BANDWIDTH={producer_results['bandwidth']}\n")
        f.write(f"RAW_CONSUMER_DURATION={consumer_results['duration']}\n")
        f.write(f"RAW_CONSUMER_THROUGHPUT={consumer_results['throughput']}\n")
        f.write(f"RAW_CONSUMER_BANDWIDTH={consumer_results['bandwidth']}\n")

    delete_topic()

if __name__ == "__main__":
    main()
