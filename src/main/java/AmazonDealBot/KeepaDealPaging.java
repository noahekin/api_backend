package DealRequest;

import MiniTools.AmazonAffiliateLinkGenerator;
import MiniTools.GoogleSheetsHelper;
import com.keepa.api.backend.KeepaAPI;
import com.keepa.api.backend.structs.Deal;
import com.keepa.api.backend.structs.DealRequest;
import com.keepa.api.backend.structs.Request;
import com.keepa.api.backend.structs.Response;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

//ALTE KLASSE VON NOAH, EXEMPLARISCH, paar methoden muss ich noch überarbeiten deswegen errors
public class KeepaDealPaging {
    private static final String API_KEY = "bcstgs6iqspkks3ad770qdk8sil8uh46u29um77r17d6gpuuqd08r94i2n9mt047";

    private static final int MAX_API_CALLS = 2;
    private static final int DEALS_PER_REQUEST = 150;

    private static final Map<Integer, Integer> MARKETPLACE_REQUESTS = new HashMap<>();
    private static final Map<Integer, long[]> CATEGORY_FILTERS = new HashMap<>();
    private static final List<String> ALLOWED_BRANDS = Arrays.asList("Samsung", "Lenovo", "Jabra", "Bose", "Sony", "Apple", "Dell", "HP");

    private static final Map<String, Map<Integer, Deal>> marketDeals = new HashMap<>();
    private static final Set<String> sheetsWithHeader = new HashSet<>();

    static {
        // **Verteilung der API-Requests**
        MARKETPLACE_REQUESTS.put(3, 24); // Deutschland (40%)
        MARKETPLACE_REQUESTS.put(4, 15); // Frankreich (25%)
        MARKETPLACE_REQUESTS.put(8, 15); // Italien (25%)
        MARKETPLACE_REQUESTS.put(9, 6);  // Spanien (10%)

        // **Kategorien: Elektronik & Foto, Computer & Zubehör**
        CATEGORY_FILTERS.put(3, new long[]{562066, 340831031});
        CATEGORY_FILTERS.put(4, new long[]{13921051, 340831031});
        CATEGORY_FILTERS.put(8, new long[]{412607031, 340831031});
        CATEGORY_FILTERS.put(9, new long[]{667049031, 340831031});
    }

    public static void main(String[] args) {
        KeepaAPI api = new KeepaAPI(API_KEY);

        for (int domainId : MARKETPLACE_REQUESTS.keySet()) {
            processMarketplace(api, domainId, MARKETPLACE_REQUESTS.get(domainId));
        }

        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        compareMarketplaces();
    }

    private static void processMarketplace(KeepaAPI api, int domainId, int maxCalls) {
        System.out.println("🔍 Starte Deals-Suche für Marktplatz: " + getMarketName(domainId));

        String sheetName = getMarketName(domainId);
        if (!sheetsWithHeader.contains(sheetName)) {
            writeSheetHeader(sheetName);
            sheetsWithHeader.add(sheetName);
        }

        for (int currentPage = 0; currentPage < maxCalls; currentPage++) {
            final int page = currentPage;

            // Anfrage für Amazon Preis (1)
            Request requestAmazon = createDealRequest(domainId, page, 1);
            // Anfrage für FBA Preis (10)
            Request requestFBA = createDealRequest(domainId, page, 10);

            api.sendRequest(requestAmazon)
                    .done(resultAmazon -> {
                        api.sendRequest(requestFBA)
                                .done(resultFBA -> processDeals(resultAmazon, resultFBA, domainId, sheetName))
                                .fail(failure -> System.out.println("❌ Fehler bei FBA-Preis auf Seite " + page + ": " + failure));
                    })
                    .fail(failure -> System.out.println("❌ Fehler bei Amazon-Preis auf Seite " + page + ": " + failure));

            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static Request createDealRequest(int domainId, int page, int priceType) {
        DealRequest dealRequest = new DealRequest();
        dealRequest.page = page;
        dealRequest.domainId = domainId;
        dealRequest.priceTypes = new int[]{priceType};
        dealRequest.salesRankRange = new int[]{1, 200000};
        dealRequest.currentRange = new int[]{5000, 200000};
        dealRequest.minRating = 30;
        dealRequest.includeCategories = CATEGORY_FILTERS.get(domainId);
        dealRequest.sortType = 4;
        dealRequest.isRangeEnabled = true;
        dealRequest.isFilterEnabled = true;

        return Request.getDealsRequest(dealRequest);
    }

    private static void processDeals(Response resultAmazon, Response resultFBA, int domainId, String sheetName) {
        if (resultAmazon.deals.dr == null || resultAmazon.deals.dr.length == 0 ||
                resultFBA.deals.dr == null || resultFBA.deals.dr.length == 0) {
            System.out.println("✅ Keine weiteren Deals in " + sheetName);
            return;
        }

        List<List<Object>> sheetData = new ArrayList<>();
        Map<String, Deal> amazonDeals = new HashMap<>();
        Map<String, Deal> fbaDeals = new HashMap<>();

        for (Deal deal : resultAmazon.deals.dr) {
            amazonDeals.put(deal.asin, deal);
        }

        for (Deal dealFBA : resultFBA.deals.dr) {
            String asin = dealFBA.asin;
            Deal dealAmazon = amazonDeals.get(asin);

            if (dealAmazon == null) continue;

            int amazonPrice = getValidPrice(dealAmazon);
            int fbaPrice = getValidPrice(dealFBA);
            int lowestPrice = Math.min(amazonPrice, fbaPrice);

            if (lowestPrice > 0) {
                storeDeal(dealAmazon, domainId);
                if (isDealGood(dealAmazon)) {
                    sheetData.add(extractDealData(dealAmazon));
                }
            }
        }

        if (!sheetData.isEmpty()) {
            try {
                GoogleSheetsHelper.writeDealsToSheet(sheetName, sheetData);
            } catch (IOException | GeneralSecurityException e) {
                e.printStackTrace();
            }
        }
    }

    private static void storeDeal(Deal deal, int domainId) {
        marketDeals.putIfAbsent(deal.asin, new HashMap<>());
        marketDeals.get(deal.asin).put(domainId, deal);
    }

    private static void compareMarketplaces() {
        for (String asin : marketDeals.keySet()) {
            Map<Integer, Deal> dealsByMarket = marketDeals.get(asin);
            if (dealsByMarket.size() > 1) {
                System.out.println("🔍 Vergleich für ASIN: " + asin);
                for (int marketId : dealsByMarket.keySet()) {
                    Deal deal = dealsByMarket.get(marketId);
                    System.out.println("   - " + getMarketName(marketId) + ": " + formatPrice(getValidPrice(deal)));
                }
            }
        }
    }

    private static int getValidPrice(Deal deal) {
        if (deal.current == null) return -1;
        int price = deal.current[1];
        if (deal.current.length > 10 && deal.current[10] > 0) {
            price = Math.min(price, deal.current[10]);
        }
        return (price > 0) ? price : -1;
    }

    private static boolean isDealGood(Deal deal) {
        int lowestPrice = getValidPrice(deal);
        int avg90Price = deal.avg[3][1];
        double discount = 100.0 * (1 - ((double) lowestPrice / avg90Price));
        return discount >= 15.0;
    }

    private static String formatPrice(int price) {
        return (price != -1) ? (price / 100.0 + "€") : "Nicht verfügbar";
    }

    private static String getMarketName(int domainId) {
        if (domainId == 3) return "Deutschland";
        if (domainId == 4) return "Frankreich";
        if (domainId == 8) return "Italien";
        if (domainId == 9) return "Spanien";
        return "Unbekannt";
    }
    private static List<Object> extractDealData(Deal deal) {
        String asin = deal.asin;
        String title = deal.title;
        int lowestPrice = getValidPrice(deal);
        int avg1 = deal.avg[0][1];
        int avg7 = deal.avg[1][1];
        int avg30 = deal.avg[2][1];
        int avg90 = deal.avg[3][1];
        double discount = 100.0 * (1 - ((double) lowestPrice / avg90));
        String affiliateLink = AmazonAffiliateLinkGenerator.generateAffiliateLink(asin);

        return Arrays.asList(
            asin,
            title,
            String.format("%.2f%%", discount),
            formatPrice(lowestPrice),
            formatPrice(avg1),
            formatPrice(avg7),
            formatPrice(avg30),
            formatPrice(avg90),
            affiliateLink
        );
    }

    private static void writeSheetHeader(String sheetName) {
        List<List<Object>> header = Collections.singletonList(
            Arrays.asList("ASIN", "Titel", "Rabatt", "Aktueller Preis", "1-Tages AVG", "7-Tages AVG", "30-Tages AVG", "90-Tages AVG", "Affiliate Link")
        );
        try {
            GoogleSheetsHelper.writeDealsToSheet(sheetName, header);
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

}
