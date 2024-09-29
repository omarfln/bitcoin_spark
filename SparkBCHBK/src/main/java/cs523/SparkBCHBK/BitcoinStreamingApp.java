package cs523.SparkBCHBK;

import org.apache.spark.SparkConf;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;

import java.util.Iterator;
import java.util.Properties;
import java.util.Collections;

public class BitcoinStreamingApp {

    public static void main(String[] args) throws InterruptedException {
    	//System.out.println("Classpath: " + System.getProperty("java.class.path"));
        SparkConf conf = new SparkConf().setAppName("BitcoinPriceTracker").setMaster("local[2]");
        JavaStreamingContext jssc = new JavaStreamingContext(conf, new Duration(10000));

        JavaDStream<Double> bitcoinPrices = jssc.receiverStream(new BitcoinPriceReceiver());

        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", "localhost:9092");
        producerProps.put("key.serializer", StringSerializer.class.getName());
        producerProps.put("value.serializer", StringSerializer.class.getName());
        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);
        

        bitcoinPrices.foreachRDD(rdd -> {
            rdd.foreachPartition(partitionOfRecords -> {
                Properties kafkaProducerProps = new Properties();
                kafkaProducerProps.put("bootstrap.servers", "localhost:9092");
                kafkaProducerProps.put("key.serializer", StringSerializer.class.getName());
                kafkaProducerProps.put("value.serializer", StringSerializer.class.getName());

                KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(kafkaProducerProps);
                try {
                    partitionOfRecords.forEachRemaining(price -> {

                        try {
                            ProducerRecord<String, String> record = new ProducerRecord<>("bitcoin-price-topic", Double.toString(price));
                            kafkaProducer.send(record, (metadata, exception) -> {
                                if (exception != null) {
                                    System.err.println("[Error]: unable to send message to Kafka, reason: " + exception.getMessage());
                                } else {
                                    System.out.println("[Info]: Successfully sent Bitcoin Price to Kafka: " + price);
                                }
                            });
                            Thread.sleep(10000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        Configuration hbaseConfig = HBaseConfiguration.create();
                        try (Connection connection = ConnectionFactory.createConnection(hbaseConfig)) {
                            Table table = connection.getTable(TableName.valueOf("bitcoin_prices"));

                            long timestamp = System.currentTimeMillis();
                            Put put = new Put(Bytes.toBytes(timestamp));
                            put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("price"), Bytes.toBytes(Double.toString(price)));
                            table.put(put);

                            table.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                    kafkaProducer.flush();

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    kafkaProducer.close();
                }
            });
        });


        jssc.start();

        new Thread(() -> consumeKafka()).start();

        jssc.awaitTermination();
    }

    private static void consumeKafka() {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "bitcoin-consumer-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("bitcoin-price-topic"));



        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(1000);
            
            for (ConsumerRecord<String, String> record : records) {
                System.out.println("[Info]: Sucessfully consumed the Bitcoin Price from Kafka: " + record.value());
            }
        }
    }
}
