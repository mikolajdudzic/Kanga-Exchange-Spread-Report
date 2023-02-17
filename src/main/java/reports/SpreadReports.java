package reports;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.*;
import java.util.Map;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class SpreadReports {
    public static void main(String[] args) {

        while (true) {

            String orderBookURL = "https://public.kanga.exchange/api/market/orderbook/%s";
            String marketURL = "https://public.kanga.exchange/api/market/pairs";
            String reportFile = "report_spread_%s.txt";
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy_MM_dd'T'HHmmss'Z'");
            Date date = new Date();

            JSONArray markets = new JSONArray();

            try {
                URL url = new URL(marketURL);
                Scanner scanner = new Scanner(url.openStream());
                markets = new JSONArray(scanner.useDelimiter("\\A").next());
            } catch (IOException error) {
                System.out.println("Error loading markets: " + error.getMessage());
                System.exit(1);
            }

            // Mapowanie nazwy rynku z wartością spreadu
            Map<String, Double> spreads = new HashMap<String, Double>();
            for (int i = 0; i < markets.length(); i++) {

                // Zaciąganie kolejnych nazw rynków w pętli
                String market = markets.getJSONObject(i).getString("ticker_id");

                try {
                    URL url = new URL(String.format(orderBookURL, market));
                    Scanner scanner = new Scanner(url.openStream());

                    // Tworzenie obiektu orderbook ("timestamp", "bids", "asks", "ticker_id")
                    JSONObject orderBook = new JSONObject(scanner.useDelimiter("\\A").next());
                    scanner.close();

                    if (orderBook.has("bids") && orderBook.has("asks")) {
                        JSONArray bids = orderBook.getJSONArray("bids");
                        JSONArray asks = orderBook.getJSONArray("asks");

                        double bestBid = bids.getJSONArray(0).getDouble(0);
                        double bestAsk = asks.getJSONArray(0).getDouble(0);

                        // Obliczanie spreadu
                        double spread = (bestAsk - bestBid) / (0.5 * (bestAsk + bestBid)) * 100;
                        spreads.put(market, spread);
                    } else {
                        spreads.put(market, Double.NaN);
                    }

                } catch (IOException error) {
                    System.out.println("Error calculating spread for " + market + ": " + error.getMessage());
                }
            }

            // Konwertowanie HashMapy "spreads" do listy i sortowanie jej alfabetycznie
            List<Map.Entry<String, Double>> sortedSpreads = spreads.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toList());

            // Tworzenie i zapisywanie pliku z zestawieniem speadów
            try {
                String fileName = String.format(reportFile, dateFormatter.format(date));
                FileWriter fileWriter = new FileWriter(fileName);
                PrintWriter printWriter = new PrintWriter(fileWriter);
                printWriter.println("Spread <= 2%");
                printWriter.println("Nazwa rynku\tSpread[%]");

                for (Map.Entry<String, Double> entry : sortedSpreads) {
                    double spread = entry.getValue();
                    if (!Double.isNaN(spread) && spread <= 2) {
                        printWriter.printf("%s\t%.2f%%%n", entry.getKey(), entry.getValue());
                    }
                }

                printWriter.println();
                printWriter.println("Spread > 2%");
                printWriter.println("Nazwa rynku\tSpread[%]");

                for (Map.Entry<String, Double> entry : sortedSpreads) {
                    double spread = entry.getValue();
                    if (!Double.isNaN(spread) && spread > 2) {
                        printWriter.printf("%s\t%.2f%%%n", entry.getKey(), entry.getValue());
                    }
                }

                printWriter.println();
                printWriter.println("Rynki bez płynności");
                printWriter.println("Nazwa rynku\tSpread[%]");
                for (Map.Entry<String, Double> entry : sortedSpreads) {
                    double spread = entry.getValue();
                    if (Double.isNaN(spread)) {
                        System.out.println(entry.getKey());
                        printWriter.printf("%s\t%s", entry.getKey(), "-");
                    }
                }
                printWriter.close();
            } catch (IOException error) {
                System.out.println("Error writing a report in seperate file: " + error.getMessage());
            }

            // Program bedzie działał w nieskończoność do momentu zabicia wątku
            // Raport będzie generowany co 60 sekund.
            try {
                Thread.sleep(60000);
            } catch (InterruptedException error) {
                System.out.println(error.getMessage());
            }
        }
    }
}
