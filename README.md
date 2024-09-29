# bitcoin_spark

This program is meant to stream data about the bitcoin prices (live data) using an API, then data is persisted in HBase and pushed to a Kafka server that has a console consumer to show the bitcoin price periodically.

Following are the main components of the project:


BitcoinPriceReceiver

A class fetches real-time Bitcoin prices from the CoinGecko API using an HTTP client and stores this data in a Spark streaming context.

BitcoinStreamingApp 

The main application that manages the streaming process. It uses a Spark Streaming context to process Bitcoin prices every 10 seconds, and stores the data in a Kafka topic ("bitcoin-price-topic") and an HBase table ("bitcoin_prices").

Kafka producer

sends the price data to the topic, while a separate thread consumes and prints it. Each price is also stored in HBase with a timestamp, under the column family "cf" and the qualifier "price".
