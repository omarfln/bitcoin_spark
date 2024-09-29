package cs523.SparkBCHBK;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.receiver.Receiver;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class BitcoinPriceReceiver extends Receiver<Double> {
    
    public BitcoinPriceReceiver() {
        super(StorageLevel.MEMORY_AND_DISK_2());
    }

    @Override
    public void onStart() {
        new Thread(this::receive).start();
    }

    @Override
    public void onStop() {
    }

    private void receive() {
        try {
            while (!isStopped()) {
                double price = fetchBitcoinPrice();
                store(price);
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            restart("[Error]: unble to receive Bitcoin price data", e);
        }
    }

    private double fetchBitcoinPrice() {
        String apiURL = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(apiURL);
            CloseableHttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                String result = EntityUtils.toString(entity);
                System.out.println("API Response: " + result);

                JSONObject json = new JSONObject(result);
                if (json.has("bitcoin")) {
                    return json.getJSONObject("bitcoin").getDouble("usd");
                } else {
                    System.err.println("[Error]: Key 'bitcoin' not found in response");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

}
